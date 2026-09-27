package com.ctos.client.network

import io.ktor.http.encodeURLParameter
import kotlinx.serialization.builtins.serializer

/**
 * The upgrade name this app asks the API for; see rawprotocol.go on the server. Only [CtosApi]'s
 * own uploads use it (the website's browser uploads have no way to speak it, so they never ask).
 */
internal const val uploadProtocol = "ctos-upload/1"

/** One raw-protocol upload's answer: the status (as the API always uses: 200/201/409/422/413/500,
 * or a plain HTTP status if the connection was refused before it could even be upgraded) and its
 * JSON body ("" for none). */
internal data class RawTransferAnswer(val status: Int, val json: String)

/**
 * Sends one raw-protocol upload: the request line and headers (still ordinary HTTP), then exactly
 * [contentLength] bytes read from [source], over a connection to [host]:[port] ([secure] for
 * TLS). Returns what came back, or throws on any connection problem (the caller turns that into
 * an [ApiException]). Implemented per platform because it needs a raw socket, which Ktor's own
 * HTTP client has no way to hand over once a request starts (see rawprotocol.go for why this
 * needs one at all).
 */
internal expect suspend fun sendRawUpload(
    secure: Boolean,
    host: String,
    port: Int,
    requestLine: String,
    headers: List<Pair<String, String>>,
    contentLength: Long,
    source: FileSource,
): RawTransferAnswer

/** Builds the request line and headers for a raw-protocol upload of [path] in [slug]. */
internal fun rawUploadRequestLine(pathPrefix: String, slug: String, path: String, baseVersion: Long): String =
    "PUT ${pathPrefix.trimEnd('/')}/departments/$slug/files?path=${path.encodeURLParameter()}&base_version=$baseVersion HTTP/1.1"

internal fun rawUploadHeaders(host: String, token: String?, size: Long, sha256: String, modifiedAt: Long): List<Pair<String, String>> = buildList {
    add("Host" to host)
    token?.takeIf { it.isNotEmpty() }?.let { add("Authorization" to "Bearer $it") }
    add("Upgrade" to uploadProtocol)
    add("Connection" to "Upgrade")
    add("Content-Length" to size.toString())
    add("X-Content-SHA256" to sha256)
    add("X-Modified-At" to modifiedAt.toString())
}

/** Turns a [RawTransferAnswer] into the same [WriteOutcome] shape the plain HTTP upload uses. */
internal fun RawTransferAnswer.toWriteOutcome(): Result<WriteOutcome> {
    val parsedBody = json.takeIf { it.isNotEmpty() }
    return when {
        status == 409 -> {
            val current = parsedBody?.let { runCatching { ctosJson.decodeFromString(ConflictAnswer.serializer(), it) }.getOrNull()?.current }
            Result.success(WriteOutcome.Conflict(current))
        }
        status in 200..299 -> {
            val node = parsedBody?.let { runCatching { ctosJson.decodeFromString(ApiResponse.serializer(RemoteNode.serializer()), it) }.getOrNull()?.data }
            Result.success(WriteOutcome.Done(node))
        }
        else -> {
            val message = parsedBody?.let { runCatching { ctosJson.decodeFromString(ApiResponse.serializer(Unit.serializer()), it) }.getOrNull() }
                ?.let { it.errors?.values?.firstOrNull()?.firstOrNull() ?: it.message }
            Result.failure(ApiException(status, message ?: "The server answered $status."))
        }
    }
}
