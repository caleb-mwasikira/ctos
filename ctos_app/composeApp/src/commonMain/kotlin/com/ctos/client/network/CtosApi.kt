package com.ctos.client.network

import com.ctos.client.BuildKonfig
import com.ctos.client.data.AppRepository
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.isSecure
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** The content of a file, handed over piece by piece so a big file is never held in memory. */
fun interface FileSource {
    suspend fun read(onChunk: suspend (bytes: ByteArray, count: Int) -> Unit)
}

/**
 * What came of a change to a file: it was made ([Done], with the file as it now is when the
 * server sends it back), or the file had moved on since the version the change was based on
 * ([Conflict], with the file as it is now, or null when it no longer exists).
 */
sealed interface WriteOutcome {
    data class Done(val node: RemoteNode?) : WriteOutcome

    data class Conflict(val current: RemoteNode?) : WriteOutcome
}

/** Carries what went wrong in a download's sink out of the request. */
private class RejectedContent(cause: Exception) : Exception(cause)

/** An answer the API gave that was not a success. [status] is null when there was no answer at all. */
class ApiException(val status: Int?, message: String) : Exception(message)

val ctosJson = Json {
    ignoreUnknownKeys = true
}

/** The plug-ins every client of the API needs; the app and its tests both use it. */
fun HttpClientConfig<*>.configureCtosClient() {
    install(ContentNegotiation) {
        json(ctosJson)
    }
    install(WebSockets)
}

/**
 * The client of the CTOS API. Every request but the login carries the Bearer token of the
 * signed-in user, read fresh from [token] each time.
 */
class CtosApi(
    private val client: HttpClient,
    val baseUrl: String,
    private val token: () -> String?,
    private val onSessionExpired: () -> Unit = {},
) {
    /** http(s):// -> ws(s):// against the same address the API is reached at. */
    internal val webSocketUrl: String get() = baseUrl.replaceFirst("http", "ws") + "/ws"

    private fun HttpRequestBuilder.authorize() {
        token()?.takeIf { it.isNotEmpty() }?.let { header(HttpHeaders.Authorization, "Bearer $it") }
    }

    suspend fun login(request: LoginRequest): Result<LoginData> = call(LoginData.serializer(), isLogin = true) {
        client.post("$baseUrl/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun logout(): Result<Unit> = callForNothing {
        client.post("$baseUrl/auth/logout") { authorize() }
    }

    // ── registering, without a session yet, so none of these carry a token ──

    suspend fun register(request: RegisterRequest): Result<RegisterData> = call(RegisterData.serializer(), isLogin = true) {
        client.post("$baseUrl/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    /** Confirms the address the code was emailed to, and logs in with it: same answer as [login]. */
    suspend fun verifyEmail(request: VerifyEmailRequest): Result<LoginData> = call(LoginData.serializer(), isLogin = true) {
        client.post("$baseUrl/auth/verify-email") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun resendVerificationCode(email: String): Result<Unit> = callForNothing {
        client.post("$baseUrl/auth/verify-email/resend") {
            contentType(ContentType.Application.Json)
            setBody(ResendCodeRequest(email))
        }
    }

    suspend fun forgotPassword(email: String): Result<Unit> = callForNothing {
        client.post("$baseUrl/auth/forgot-password") {
            contentType(ContentType.Application.Json)
            setBody(ForgotPasswordRequest(email))
        }
    }

    suspend fun resetPassword(request: ResetPasswordRequest): Result<Unit> = callForNothing {
        client.post("$baseUrl/auth/reset-password") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun getDepartments(): Result<List<Department>> = call(ListSerializer(Department.serializer())) {
        client.get("$baseUrl/departments") { authorize() }
    }

    suspend fun joinDepartment(request: JoinRequest): Result<Department> = call(Department.serializer()) {
        client.post("$baseUrl/departments/join") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    // ── organizations ──────────────────────────────────────────────────────

    suspend fun getOrganizations(): Result<List<OrganizationSummaryDto>> = call(ListSerializer(OrganizationSummaryDto.serializer())) {
        client.get("$baseUrl/organizations") { authorize() }
    }

    suspend fun createOrganization(name: String): Result<OrganizationSummaryDto> = call(OrganizationSummaryDto.serializer()) {
        client.post("$baseUrl/organizations") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(CreateOrganizationRequest(name))
        }
    }

    // ── sharing a department ─────────────────────────────────────────────────

    /** Gives someone access to a department by email; see [ShareOutcome]. */
    suspend fun shareDepartment(slug: String, email: String): Result<ShareOutcome> = try {
        val response = client.post("$baseUrl/departments/$slug/invitations") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(ShareRequest(email))
        }
        if (response.status.isSuccess()) {
            val body = ctosJson.decodeFromString(ApiResponse.serializer(ShareResultData.serializer()), response.bodyAsText())
            Result.success(ShareOutcome(body.data?.added == true, body.message ?: "Done."))
        } else {
            Result.failure(failure(response, isLogin = false))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e.asApiException())
    }

    suspend fun withdrawInvitation(slug: String, invitationId: Long): Result<Unit> = callForNothing {
        client.delete("$baseUrl/departments/$slug/invitations/$invitationId") { authorize() }
    }

    /** Replaces the department's access code with a new random one; the old one stops working. */
    suspend fun regenerateAccessCode(slug: String): Result<String> = call(AccessCodeData.serializer()) {
        client.post("$baseUrl/departments/$slug/access-code") { authorize() }
    }.map { it.accessCode }

    /** One department with its members (and, for people who manage it, the access code and open invitations). */
    suspend fun getDepartmentDetails(slug: String): Result<DepartmentDetails> = call(DepartmentDetails.serializer()) {
        client.get("$baseUrl/departments/$slug") { authorize() }
    }

    suspend fun leaveDepartment(slug: String): Result<Unit> = callForNothing {
        client.delete("$baseUrl/departments/$slug/membership") { authorize() }
    }

    // ── files ──────────────────────────────────────────────────────────────

    /** What changed in a department after [cursor] (0 for everything). */
    suspend fun getChanges(slug: String, cursor: Long, limit: Int = 500): Result<ChangesPage> = call(ChangesPage.serializer()) {
        client.get("$baseUrl/departments/$slug/changes") {
            authorize()
            url {
                parameters.append("cursor", cursor.toString())
                parameters.append("limit", limit.toString())
            }
        }
    }

    /**
     * Downloads a file: [sink] is given the content while it arrives. It throws when the content
     * is not acceptable (for example its checksum is wrong), which fails the download.
     */
    suspend fun downloadFile(slug: String, path: String, sink: suspend (ByteReadChannel) -> Unit): Result<Unit> = try {
        client.prepareGet("$baseUrl/departments/$slug/files") {
            authorize()
            url { parameters.append("path", path) }
        }.execute { response ->
            if (!response.status.isSuccess()) throw failure(response, isLogin = false)
            try {
                sink(response.bodyAsChannel())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw RejectedContent(e)
            }
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: RejectedContent) {
        // The sink's own problem (the disk, a wrong checksum), not the server's or the network's.
        Result.failure(e.cause ?: e)
    } catch (e: Exception) {
        Result.failure(e.asApiException())
    }

    /**
     * Uploads a file. [baseVersion] is the version being replaced, or 0 for a new file; when the
     * file has changed since, nothing is written and the answer is [WriteOutcome.Conflict].
     */
    /**
     * Uploads a file over the raw-upload protocol (see rawprotocol.go on the server): the
     * connection switches out of HTTP for the transfer itself, which spares a big file the
     * framing overhead a plain request body would add. This is the app's own upload path; the
     * website's browser uploads use the plain one instead (a page has no way to speak this).
     */
    suspend fun uploadFile(
        slug: String,
        path: String,
        baseVersion: Long,
        size: Long,
        sha256: String,
        modifiedAt: Long,
        source: FileSource,
    ): Result<WriteOutcome> = try {
        val url = Url(baseUrl)
        val answer = sendRawUpload(
            secure = url.protocol.isSecure(),
            host = url.host,
            port = url.port,
            requestLine = rawUploadRequestLine(url.encodedPath, slug, path, baseVersion),
            headers = rawUploadHeaders(url.host, token(), size, sha256, modifiedAt),
            contentLength = size,
            source = source,
        )
        answer.toWriteOutcome()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(if (e is ApiException) e else e.asApiException())
    }

    /** Makes a folder (and the ones above it). */
    suspend fun createFolder(slug: String, path: String): Result<RemoteNode> = call(RemoteNode.serializer()) {
        client.post("$baseUrl/departments/$slug/folders") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(CreateFolderRequest(path))
        }
    }

    /** Deletes a file, or a folder with everything in it. [baseVersion] is what the caller last saw. */
    suspend fun deleteNode(slug: String, path: String, baseVersion: Long?): Result<WriteOutcome> = outcomeOf {
        client.delete("$baseUrl/departments/$slug/nodes") {
            authorize()
            url {
                parameters.append("path", path)
                if (baseVersion != null) parameters.append("base_version", baseVersion.toString())
            }
        }
    }

    /** A 409 is not a failure but an answer the caller must act on. */
    private suspend fun outcomeOf(request: suspend () -> HttpResponse): Result<WriteOutcome> = try {
        val response = request()
        when {
            response.status == HttpStatusCode.Conflict -> {
                val answer = try {
                    ctosJson.decodeFromString(ConflictAnswer.serializer(), response.bodyAsText())
                } catch (e: SerializationException) {
                    ConflictAnswer()
                }
                Result.success(WriteOutcome.Conflict(answer.current))
            }
            response.status.isSuccess() -> {
                val node = try {
                    ctosJson.decodeFromString(ApiResponse.serializer(RemoteNode.serializer()), response.bodyAsText()).data
                } catch (e: SerializationException) {
                    null // a delete answers with a count, not a node
                }
                Result.success(WriteOutcome.Done(node))
            }
            else -> Result.failure(failure(response, isLogin = false))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e.asApiException())
    }

    /**
     * Keeps the WebSocket open and calls [onEvent] for every message, for as long as the
     * connection lasts ([onOpen] is called once when it is made). It returns or throws when the connection ends, so the caller can
     * reconnect (see DepartmentEvents).
     */
    suspend fun listen(onOpen: () -> Unit = {}, onEvent: (WsEvent) -> Unit) {
        client.webSocket(webSocketUrl, request = { authorize() }) {
            onOpen()
            for (frame in incoming) {
                if (frame !is Frame.Text) continue
                try {
                    onEvent(ctosJson.decodeFromString(WsEvent.serializer(), frame.readText()))
                } catch (e: Exception) {
                    println("listen() could not read a message: ${e.message}")
                }
            }
        }
    }

    // ── Secure Drop (docs/secure-drop.md) ────────────────────────────────────

    /** Step one of setting up a key pair: a nonce encrypted with the device's public key. */
    suspend fun secureDropChallenge(publicKeyPem: String): Result<String> =
        call(EncryptionKeyChallengeData.serializer()) {
            client.post("$baseUrl/encryption-keys/challenge") {
                authorize()
                contentType(ContentType.Application.Json)
                setBody(EncryptionKeyChallengeRequest(publicKeyPem))
            }
        }.map { it.ciphertext }

    /** Step two: proves the challenge was decrypted, then stores the key pair. */
    suspend fun storeEncryptionKey(proof: String, encryptedPrivateKey: String): Result<Unit> = callForNothing {
        client.post("$baseUrl/encryption-keys") {
            authorize()
            contentType(ContentType.Application.Json)
            setBody(StoreEncryptionKeyRequest(proof, encryptedPrivateKey))
        }
    }

    /** null when no key is set up yet, rather than a failure. */
    suspend fun getEncryptionKeyStatus(): Result<EncryptionKeyStatus?> = try {
        val response = client.get("$baseUrl/encryption-keys") { authorize() }
        when {
            response.status == HttpStatusCode.NotFound -> Result.success(null)
            response.status.isSuccess() -> Result.success(ctosJson.decodeFromString(ApiResponse.serializer(EncryptionKeyStatus.serializer()), response.bodyAsText()).data)
            else -> Result.failure(failure(response, isLogin = false))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e.asApiException())
    }

    /** The caller's own encrypted private key PEM, to decrypt locally with the passphrase. */
    suspend fun downloadEncryptionKey(): Result<String> = try {
        val response = client.get("$baseUrl/encryption-keys/download") { authorize() }
        if (response.status.isSuccess()) Result.success(response.bodyAsText()) else Result.failure(failure(response, isLogin = false))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e.asApiException())
    }

    suspend fun deleteEncryptionKey(): Result<Unit> = callForNothing {
        client.delete("$baseUrl/encryption-keys") { authorize() }
    }

    /** Department members who have a Secure Drop key: their public key, to wrap a file's AES key for them. */
    suspend fun getSecureDropRecipients(slug: String): Result<List<SecureDropRecipientOption>> =
        call(ListSerializer(SecureDropRecipientOption.serializer())) {
            client.get("$baseUrl/departments/$slug/secure-drop-recipients") { authorize() }
        }

    suspend fun getSecureDrops(): Result<SecureDropLists> = call(SecureDropLists.serializer()) {
        client.get("$baseUrl/secure-drops") { authorize() }
    }

    /** Sends one already-encrypted file: [ciphertext] and every recipient's wrapped copy of its key. */
    suspend fun storeSecureDrop(
        slug: String,
        name: String,
        iv: String,
        ciphertext: ByteArray,
        recipients: List<SecureDropRecipientUpload>,
    ): Result<Long> = call(SecureDropIdData.serializer()) {
        client.submitFormWithBinaryData(
            url = "$baseUrl/departments/$slug/secure-drop",
            formData = formData {
                append("name", name)
                append("iv", iv)
                append("recipients", ctosJson.encodeToString(ListSerializer(SecureDropRecipientUpload.serializer()), recipients))
                append("ciphertext", ciphertext, Headers.build {
                    append(HttpHeaders.ContentType, "application/octet-stream")
                    append(HttpHeaders.ContentDisposition, "filename=\"ciphertext\"")
                })
            },
        ) { authorize() }
    }.map { it.id }

    /** What is needed to decrypt a drop: its nonce and the caller's own wrapped key. */
    suspend fun getSecureDrop(id: Long): Result<SecureDropDetails> = call(SecureDropDetails.serializer()) {
        client.get("$baseUrl/secure-drops/$id") { authorize() }
    }

    suspend fun downloadSecureDrop(id: Long, sink: suspend (ByteReadChannel) -> Unit): Result<Unit> = try {
        client.prepareGet("$baseUrl/secure-drops/$id/download") { authorize() }.execute { response ->
            if (!response.status.isSuccess()) throw failure(response, isLogin = false)
            try {
                sink(response.bodyAsChannel())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw RejectedContent(e)
            }
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: RejectedContent) {
        Result.failure(e.cause ?: e)
    } catch (e: Exception) {
        Result.failure(e.asApiException())
    }

    suspend fun deleteSecureDrop(id: Long): Result<Unit> = callForNothing {
        client.delete("$baseUrl/secure-drops/$id") { authorize() }
    }

    private suspend fun <T> call(
        serializer: KSerializer<T>,
        isLogin: Boolean = false,
        request: suspend () -> HttpResponse,
    ): Result<T> = try {
        val response = request()
        if (response.status.isSuccess()) {
            val body = ctosJson.decodeFromString(ApiResponse.serializer(serializer), response.bodyAsText())
            body.data?.let { Result.success(it) } ?: Result.failure(ApiException(response.status.value, "The server sent an empty answer."))
        } else {
            Result.failure(failure(response, isLogin))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e.asApiException())
    }

    private suspend fun callForNothing(request: suspend () -> HttpResponse): Result<Unit> = try {
        val response = request()
        if (response.status.isSuccess()) Result.success(Unit) else Result.failure(failure(response, isLogin = false))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e.asApiException())
    }

    /**
     * Turns an error answer into an exception carrying the server's own message. A 401 on
     * anything but the login means the saved token no longer works (a wrong password on the
     * login is also a 401, and must not sign anyone out).
     */
    private suspend fun failure(response: HttpResponse, isLogin: Boolean): ApiException {
        if (response.status == HttpStatusCode.Unauthorized && !isLogin) {
            onSessionExpired()
        }
        val text = response.bodyAsText()
        val message = try {
            val body = ctosJson.decodeFromString(ApiResponse.serializer(Unit.serializer()), text)
            body.errors?.values?.firstOrNull()?.firstOrNull() ?: body.message
        } catch (e: Exception) {
            null
        }
        return ApiException(response.status.value, message ?: "The server answered ${response.status}.")
    }

    private fun Exception.asApiException(): ApiException = when (this) {
        is ApiException -> this
        is SerializationException -> ApiException(null, "The server sent an answer this app could not read.")
        else -> ApiException(null, "Could not reach the server. Check your connection and try again.")
    }

    companion object {
        /** The API this build of the app was made for (see composeApp/build.gradle.kts). */
        val default: CtosApi by lazy {
            CtosApi(
                client = HttpClient { configureCtosClient() },
                baseUrl = BuildKonfig.APP_URL,
                token = { AppRepository.getCurrentSession()?.token },
                onSessionExpired = { SessionEvents.notifyExpired() },
            )
        }
    }
}
