package com.ctos.client.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

// The shapes the CTOS API (ctos_api, written in Go) sends and receives. A success is
// {"data": ...}; an error is {"message": "...", "errors": {"field": ["..."]}}.

@Serializable
data class ApiResponse<T>(
    val data: T? = null,
    val message: String? = null,
    val errors: Map<String, List<String>>? = null,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
    /** What the token is called in the user's list of devices ("Alice's laptop"). */
    @SerialName("device_name") val deviceName: String,
)

@Serializable
data class ApiUser(
    val id: Long,
    val name: String,
    val email: String,
)

@Serializable
data class LoginData(
    val token: String,
    val user: ApiUser,
)

@Serializable
data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String,
    @SerialName("password_confirmation") val passwordConfirmation: String,
)

@Serializable
data class RegisterData(val email: String)

@Serializable
data class VerifyEmailRequest(
    val email: String,
    val code: String,
    @SerialName("device_name") val deviceName: String,
)

@Serializable
data class ResendCodeRequest(val email: String)

@Serializable
data class ForgotPasswordRequest(val email: String)

@Serializable
data class ResetPasswordRequest(
    val email: String,
    val code: String,
    val password: String,
    @SerialName("password_confirmation") val passwordConfirmation: String,
)

@Serializable
data class JoinRequest(
    /** The department ID shown on the website; unique across every organization. */
    val department: String,
    val code: String,
)

/** An organization as the dashboard shows it: it may not have any departments yet. */
@Serializable
data class OrganizationSummaryDto(
    val id: Long,
    val name: String,
    val slug: String,
    @SerialName("department_count") val departmentCount: Int = 0,
    @SerialName("is_owner") val isOwner: Boolean = false,
)

@Serializable
data class CreateOrganizationRequest(val name: String)

@Serializable
data class ShareRequest(val email: String)

/** The "data" of a successful share answer: whether an existing account was attached straight
 * away (true) or an email invitation was sent instead (false). */
@Serializable
data class ShareResultData(val added: Boolean)

@Serializable
data class AccessCodeData(@SerialName("access_code") val accessCode: String)

/** What came of sharing a department: [added] as above, and [message] the server's own words for
 * what happened ("X now has access..." or "We emailed X..."), ready to show as-is. */
data class ShareOutcome(val added: Boolean, val message: String)

@Serializable
data class OrganizationRef(
    val id: Long,
    val name: String,
    val slug: String,
)

/** A department the signed-in user belongs to. [role] is "admin", "lead" or "member". */
@Serializable
data class Department(
    val id: Long,
    val name: String,
    val slug: String,
    val organization: OrganizationRef,
    val role: String,
)

/** One of the people in a department. [email] is only sent to people who manage the department. */
@Serializable
data class DepartmentMember(
    val id: Long,
    val name: String,
    val role: String,
    val email: String? = null,
) {
    val departmentRole: DepartmentRole get() = DepartmentRole.fromApi(role)
}

/** Someone who was sent a share link but has not joined yet. */
@Serializable
data class DepartmentInvitation(
    val id: Long,
    val email: String,
)

/**
 * A department with what its page on the website shows: the members and, for people who manage
 * the department, its access code and the invitations that are still open.
 */
@Serializable
data class DepartmentDetails(
    val id: Long,
    val name: String,
    val slug: String,
    val organization: OrganizationRef,
    val role: String,
    @SerialName("member_count") val memberCount: Int = 0,
    val members: List<DepartmentMember> = emptyList(),
    @SerialName("can_manage") val canManage: Boolean = false,
    @SerialName("access_code") val accessCode: String? = null,
    val invitations: List<DepartmentInvitation> = emptyList(),
)

/**
 * A file or folder of a department, as the server knows it. [path] is relative to the department.
 * [version] goes up by one with every change; a deleted node stays as a tombstone so devices that
 * were offline learn about it.
 */
@Serializable
data class RemoteNode(
    val id: Long,
    val path: String,
    val type: String,
    val size: Long = 0,
    val checksum: String? = null,
    val version: Long,
    /** Milliseconds since 1970. */
    @SerialName("modified_at") val modifiedAt: Long? = null,
    val deleted: Boolean = false,
) {
    val isFolder: Boolean get() = type == "folder"
}

/** A page of the change log. Ask again from [cursor] for what came after, while [more] is true and later too. */
@Serializable
data class ChangesPage(
    val nodes: List<RemoteNode>,
    val cursor: Long,
    val more: Boolean = false,
)

@Serializable
data class CreateFolderRequest(val path: String)

/** {"message": "...", "current": {...}}: the answer to a change based on a version that is no longer the latest. */
@Serializable
data class ConflictAnswer(
    val message: String? = null,
    val current: RemoteNode? = null,
)

/**
 * A nudge pushed down the /ws connection, for example
 * {"type": "department.changed", "data": {"department_id": 7, "cursor": 4812}}.
 * It never carries file data: the app reacts by asking the API what changed.
 */
@Serializable
data class WsEvent(
    val type: String,
    val data: JsonObject? = null,
) {
    val departmentId: Long? get() = data?.get("department_id")?.jsonPrimitive?.content?.toLongOrNull()
}

/** What a department's role means for the person who has it. */
enum class DepartmentRole(val label: String, val canManage: Boolean) {
    Admin("Admin", true),
    Lead("Lead", true),
    Member("Member", false);

    companion object {
        fun fromApi(value: String): DepartmentRole = entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: Member
    }
}

val Department.departmentRole: DepartmentRole get() = DepartmentRole.fromApi(role)

// ── Secure Drop (docs/secure-drop.md): end-to-end encrypted files between department members ──

@Serializable
data class EncryptionKeyChallengeRequest(@SerialName("public_key") val publicKey: String)

@Serializable
data class EncryptionKeyChallengeData(val ciphertext: String)

@Serializable
data class StoreEncryptionKeyRequest(
    val proof: String,
    @SerialName("encrypted_private_key") val encryptedPrivateKey: String,
)

@Serializable
data class EncryptionKeyStatus(
    val fingerprint: String,
    @SerialName("created_at") val createdAtMillis: Long,
    /** Needed to wrap a file's AES key for yourself when sending directly by email (docs/secure-drop.md), where there is no department member list to find it in. */
    @SerialName("public_key") val publicKey: String? = null,
)

/**
 * A department member, whether or not they have set up a Secure Drop key yet — [hasKey] false
 * means [publicKey]/[fingerprint] are null and this person cannot be picked yet, only shown.
 */
@Serializable
data class SecureDropRecipientOption(
    val id: Long,
    val name: String,
    @SerialName("public_key") val publicKey: String? = null,
    val fingerprint: String? = null,
    @SerialName("has_key") val hasKey: Boolean = publicKey != null,
)

/**
 * A drop's [departmentId]/[departmentName] are null when it was sent directly by email, not
 * scoped to any department (docs/secure-drop.md, "sending by email").
 */
@Serializable
data class SecureDropSummary(
    val id: Long,
    @SerialName("department_id") val departmentId: Long? = null,
    @SerialName("department_name") val departmentName: String? = null,
    @SerialName("owner_id") val ownerId: Long,
    @SerialName("owner_name") val ownerName: String,
    val name: String,
    val size: Long,
    val iv: String,
    @SerialName("created_at") val createdAtMillis: Long,
)

@Serializable
data class SecureDropLists(
    val sent: List<SecureDropSummary>,
    val received: List<SecureDropSummary>,
)

@Serializable
data class SecureDropDetails(
    val id: Long,
    val name: String,
    val size: Long,
    val iv: String,
    @SerialName("encrypted_key") val encryptedKey: String,
)

@Serializable
data class SecureDropRecipientUpload(
    @SerialName("user_id") val userId: Long,
    @SerialName("encrypted_key") val encryptedKey: String,
)

@Serializable
data class SecureDropIdData(val id: Long)

@Serializable
data class SecureDropLookupRequest(val email: String)

/** Whether [email] belongs to an account with a Secure Drop key — enough to send to them directly, not scoped to any department. */
@Serializable
data class SecureDropLookupResult(
    @SerialName("has_account") val hasAccount: Boolean,
    val id: Long? = null,
    val name: String? = null,
    @SerialName("public_key") val publicKey: String? = null,
    val fingerprint: String? = null,
)

/** For someone with no account yet: their email, and their copy of a file's AES key wrapped with a one-time password instead of a public key. */
@Serializable
data class SecureDropPasswordRecipientUpload(
    val email: String,
    @SerialName("encrypted_key") val encryptedKey: String,
)
