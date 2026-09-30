package com.ctos.client.data

import com.ctos.client.db.AppDatabaseQueries
import com.ctos.client.db.Session
import com.ctos.client.deviceName
import com.ctos.client.network.CtosApi
import com.ctos.client.network.Department
import com.ctos.client.network.DepartmentDetails
import com.ctos.client.network.EncryptionKeyStatus
import com.ctos.client.network.JoinRequest
import com.ctos.client.network.LoginData
import com.ctos.client.network.LoginRequest
import com.ctos.client.network.OrganizationSummaryDto
import com.ctos.client.network.RegisterData
import com.ctos.client.network.RegisterRequest
import com.ctos.client.network.ResetPasswordRequest
import com.ctos.client.network.SecureDropDetails
import com.ctos.client.network.SecureDropLists
import com.ctos.client.network.SecureDropLookupResult
import com.ctos.client.network.SecureDropPasswordRecipientUpload
import com.ctos.client.network.SecureDropRecipientOption
import com.ctos.client.network.SecureDropRecipientUpload
import com.ctos.client.network.VerifyEmailRequest
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Everything the screens need from outside the app: the API, and the small local database
 * (the login session, and what each department's local folder held).
 */
object AppRepository {
    private val queries: AppDatabaseQueries by lazy { createDatabase().appDatabaseQueries }

    private val api: CtosApi get() = CtosApi.default

    // ── the login session ──────────────────────────────────────────────────

    fun getCurrentSession(): Session? = queries.getSession().executeAsOneOrNull()

    fun clearSession() {
        queries.clearSession()
    }

    /** Signs in and keeps the token, so the app opens signed in next time. */
    suspend fun login(email: String, password: String): Result<LoginData> = withContext(Dispatchers.IO) {
        api.login(LoginRequest(email.trim().lowercase(), password, deviceName())).onSuccess { data ->
            queries.saveSession(data.token, data.user.id, data.user.name, data.user.email)
        }
    }

    /** Signs out on the server (which deletes this device's token) and forgets the session here. */
    suspend fun logout() = withContext(Dispatchers.IO) {
        // Even when the server cannot be reached, this device must end up signed out.
        api.logout()
        clearSession()
    }

    // ── creating an account, and getting back in when the password is forgotten ─────────────

    /** Creates the account and emails it a code to confirm the address; no session yet. */
    suspend fun register(name: String, email: String, password: String, confirmation: String): Result<RegisterData> = withContext(Dispatchers.IO) {
        api.register(RegisterRequest(name.trim(), email.trim().lowercase(), password, confirmation))
    }

    /** Confirms the address with the code that was emailed, and signs in, same as [login]. */
    suspend fun verifyEmail(email: String, code: String): Result<LoginData> = withContext(Dispatchers.IO) {
        api.verifyEmail(VerifyEmailRequest(email.trim().lowercase(), code.trim(), deviceName())).onSuccess { data ->
            queries.saveSession(data.token, data.user.id, data.user.name, data.user.email)
        }
    }

    suspend fun resendVerificationCode(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        api.resendVerificationCode(email.trim().lowercase())
    }

    /** Always succeeds, whether or not the address has an account: see the API's own doc comment. */
    suspend fun forgotPassword(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        api.forgotPassword(email.trim().lowercase())
    }

    /** Sets the new password with the code that was emailed, and signs every device out. */
    suspend fun resetPassword(email: String, code: String, password: String, confirmation: String): Result<Unit> = withContext(Dispatchers.IO) {
        api.resetPassword(ResetPasswordRequest(email.trim().lowercase(), code.trim(), password, confirmation))
    }

    // ── departments ────────────────────────────────────────────────────────

    suspend fun fetchDepartments(): Result<List<Department>> = withContext(Dispatchers.IO) { api.getDepartments() }

    suspend fun joinDepartment(code: String): Result<Department> = withContext(Dispatchers.IO) {
        api.joinDepartment(JoinRequest(code.trim()))
    }

    // ── organizations ──────────────────────────────────────────────────────

    suspend fun fetchOrganizations(): Result<List<OrganizationSummaryDto>> = withContext(Dispatchers.IO) { api.getOrganizations() }

    suspend fun createOrganization(name: String): Result<OrganizationSummaryDto> = withContext(Dispatchers.IO) { api.createOrganization(name.trim()) }

    // ── sharing a department ─────────────────────────────────────────────────

    suspend fun shareDepartment(slug: String, email: String) = withContext(Dispatchers.IO) { api.shareDepartment(slug, email.trim()) }

    suspend fun withdrawInvitation(slug: String, invitationId: Long): Result<Unit> = withContext(Dispatchers.IO) { api.withdrawInvitation(slug, invitationId) }

    suspend fun regenerateAccessCode(slug: String): Result<String> = withContext(Dispatchers.IO) { api.regenerateAccessCode(slug) }

    suspend fun fetchDepartmentDetails(slug: String): Result<DepartmentDetails> = withContext(Dispatchers.IO) { api.getDepartmentDetails(slug) }

    suspend fun leaveDepartment(slug: String): Result<Unit> = withContext(Dispatchers.IO) { api.leaveDepartment(slug) }

    // ── Secure Drop (docs/secure-drop.md): end-to-end encrypted files between department members ──

    suspend fun secureDropChallenge(publicKeyPem: String): Result<String> = withContext(Dispatchers.IO) { api.secureDropChallenge(publicKeyPem) }

    suspend fun storeEncryptionKey(proof: String, encryptedPrivateKey: String): Result<Unit> = withContext(Dispatchers.IO) { api.storeEncryptionKey(proof, encryptedPrivateKey) }

    suspend fun encryptionKeyStatus(): Result<EncryptionKeyStatus?> = withContext(Dispatchers.IO) { api.getEncryptionKeyStatus() }

    suspend fun downloadEncryptionKey(): Result<String> = withContext(Dispatchers.IO) { api.downloadEncryptionKey() }

    suspend fun deleteEncryptionKey(): Result<Unit> = withContext(Dispatchers.IO) { api.deleteEncryptionKey() }

    suspend fun secureDropRecipients(slug: String): Result<List<SecureDropRecipientOption>> = withContext(Dispatchers.IO) { api.getSecureDropRecipients(slug) }

    suspend fun secureDrops(): Result<SecureDropLists> = withContext(Dispatchers.IO) { api.getSecureDrops() }

    suspend fun sendSecureDrop(slug: String, name: String, iv: String, ciphertext: ByteArray, recipients: List<SecureDropRecipientUpload>): Result<Long> =
        withContext(Dispatchers.IO) { api.storeSecureDrop(slug, name, iv, ciphertext, recipients) }

    /** Whether an email belongs to an account with a Secure Drop key — enough to send to them directly, not scoped to any department. */
    suspend fun lookupSecureDropRecipient(email: String): Result<SecureDropLookupResult> =
        withContext(Dispatchers.IO) { api.lookupSecureDropRecipient(email) }

    /** Sends directly to whoever [lookupSecureDropRecipient] resolved — not scoped to a department. */
    suspend fun sendSecureDropDirect(
        name: String,
        iv: String,
        ciphertext: ByteArray,
        recipients: List<SecureDropRecipientUpload>,
        passwordRecipient: SecureDropPasswordRecipientUpload? = null,
        password: String? = null,
    ): Result<Long> = withContext(Dispatchers.IO) { api.sendSecureDropDirect(name, iv, ciphertext, recipients, passwordRecipient, password) }

    suspend fun secureDropDetails(id: Long): Result<SecureDropDetails> = withContext(Dispatchers.IO) { api.getSecureDrop(id) }

    suspend fun downloadSecureDrop(id: Long, sink: suspend (ByteReadChannel) -> Unit): Result<Unit> = withContext(Dispatchers.IO) { api.downloadSecureDrop(id, sink) }

    suspend fun deleteSecureDrop(id: Long): Result<Unit> = withContext(Dispatchers.IO) { api.deleteSecureDrop(id) }

    // ── what the sync knows ────────────────────────────────────────────────

    val syncStore: SyncStore by lazy { SqlSyncStore(queries) }

    // ── what the local folders held the last time they were looked at ───────

    fun savedFiles(departmentSlug: String): List<LocalFile> =
        queries.getLocalFiles(departmentSlug).executeAsList().map { LocalFile(it.path, it.size, it.modified_at) }

    fun saveFiles(departmentSlug: String, files: List<LocalFile>) {
        queries.transaction {
            queries.deleteLocalFiles(departmentSlug)
            files.forEach { queries.upsertLocalFile(departmentSlug, it.path, it.size, it.modifiedAt) }
        }
    }
}
