package com.ctos.client

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.ctos.client.data.ChangeKind
import com.ctos.client.data.DepartmentSyncState
import com.ctos.client.data.DepartmentsState
import com.ctos.client.data.DetailsState
import com.ctos.client.data.FileSearchResult
import com.ctos.client.data.GlobalSearchResults
import com.ctos.client.data.FormState
import com.ctos.client.data.LocalFile
import com.ctos.client.data.LocalFolderState
import com.ctos.client.data.PickedFile
import com.ctos.client.data.RecentChange
import com.ctos.client.data.Screen
import com.ctos.client.data.SecureDropKeyState
import com.ctos.client.data.SyncStatus
import com.ctos.client.data.groupByOrganization
import com.ctos.client.network.Department
import com.ctos.client.network.DepartmentDetails
import com.ctos.client.network.DepartmentInvitation
import com.ctos.client.network.DepartmentMember
import com.ctos.client.network.OrganizationRef
import com.ctos.client.network.SecureDropLists
import com.ctos.client.network.SecureDropRecipientOption
import com.ctos.client.network.SecureDropSummary
import com.ctos.client.screens.DashboardContent
import com.ctos.client.screens.DepartmentAccessCodeContent
import com.ctos.client.screens.DepartmentContent
import com.ctos.client.screens.DepartmentInvitationsContent
import com.ctos.client.screens.DepartmentLeaveContent
import com.ctos.client.screens.DepartmentMembersContent
import com.ctos.client.screens.DepartmentsContent
import com.ctos.client.screens.ForgotPasswordContent
import com.ctos.client.screens.JoinDepartmentForm
import com.ctos.client.screens.GenerateAccessCodeCard
import com.ctos.client.screens.OpenSecureDropForm
import com.ctos.client.screens.RegisterContent
import com.ctos.client.screens.SearchDialogContent
import com.ctos.client.screens.LoadingScreen
import com.ctos.client.screens.LoginContent
import com.ctos.client.screens.OrganizationContent
import com.ctos.client.screens.SecureDropContent
import com.ctos.client.screens.SendSecureDropByEmailContent
import com.ctos.client.screens.SendSecureDropContent
import com.ctos.client.screens.SettingsContent
import com.ctos.client.screens.VerifyEmailContent
import com.ctos.client.screens.layouts.AppShell
import com.ctos.client.screens.widgets.DialogCard
import com.ctos.client.screens.widgets.ToastCard
import com.ctos.client.theme.AppTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Draws each screen off-screen (no window, no display needed) and saves a PNG in
 * composeApp/build/screenshots, so the UI can be looked at without running the app. A test
 * fails if a screen throws while it is drawn, or comes out blank.
 */
@OptIn(ExperimentalComposeUiApi::class)
class ScreenshotTest {
    private val acme = OrganizationRef(1, "Acme Corp", "acme-corp")
    private val globex = OrganizationRef(3, "Globex", "globex")
    private val departments = listOf(
        Department(1, "Engineering", "engineering", acme, "admin"),
        Department(2, "Design", "design", acme, "admin"),
        Department(3, "Finance", "finance", acme, "member"),
        Department(4, "Design Systems", "design-systems", globex, "lead"),
    )
    private val loaded = DepartmentsState.Loaded(departments)
    private val now = 1_790_000_000_000L
    private val minute = 60_000L

    private val folder = LocalFolderState(
        path = "/home/alice/CTOS/acme-corp/engineering",
        watching = true,
        files = listOf(
            LocalFile("Brand-guidelines.pdf", 8_598_323, now - 1 * minute),
            LocalFile("Q3-budget.xlsx", 1_468_006, now - 14 * minute),
            LocalFile("Roadmap 2026/plan.md", 12_400, now - 26 * 60 * minute),
            LocalFile("Roadmap 2026/notes/meeting-notes.md", 2_048, now - 3 * 24 * 60 * minute),
        ),
        recentChanges = listOf(
            RecentChange("Brand-guidelines.pdf", ChangeKind.Created, now - 1 * minute),
            RecentChange("Q3-budget.xlsx", ChangeKind.Modified, now - 14 * minute),
            RecentChange("Roadmap 2026/old-plan.docx", ChangeKind.Deleted, now - 2 * 60 * minute, whileClosed = true),
        ),
    )

    private fun render(name: String, width: Int, height: Int, content: @Composable () -> Unit) {
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            AppTheme { content() }
        }
        try {
            // Several frames: layout, then anything that reacts to it.
            var image = scene.render(0)
            repeat(4) { frame -> image = scene.render((frame + 1) * 50_000_000L) }

            val bytes = image.encodeToData(EncodedImageFormat.PNG)!!.bytes
            File("build/screenshots/$name.png").apply { parentFile.mkdirs() }.writeBytes(bytes)

            val colors = image.toComposeImageBitmap().toPixelMap().buffer.toSet().size
            assertTrue(colors >= 5, "$name came out blank or nearly blank ($colors colors)")
        } finally {
            scene.close()
        }
    }

    @Composable
    private fun Shell(current: Screen, title: String, canGoBack: Boolean = false, content: @Composable () -> Unit) {
        AppShell(current, title, "Alice Wanjiku", "alice@example.com", canGoBack, onBack = {}, onNavigate = {}, onSearch = {}, onLogout = {}, content = content)
    }

    // ── login ──────────────────────────────────────────────────────────────

    @Test
    fun login() {
        render("login-desktop", 1200, 800) { LoginContent(FormState.Idle, { _, _ -> }, {}, {}, {}, "http://localhost:9090/api/v1") }
        render("login-error-desktop", 1200, 800) { LoginContent(FormState.Failed("Invalid credentials."), { _, _ -> }, {}, {}, {}, "http://localhost:9090/api/v1") }
        render("login-phone", 390, 800) { LoginContent(FormState.Idle, { _, _ -> }, {}, {}, {}, "http://10.0.2.2:9090/api/v1") }
        render("loading", 600, 400) { LoadingScreen() }
    }

    @Test
    fun register() {
        render("register-desktop", 480, 800) { RegisterContent(FormState.Idle, { _, _, _, _ -> }, {}, {}) }
        render("register-error", 480, 800) { RegisterContent(FormState.Failed("The email has already been taken."), { _, _, _, _ -> }, {}, {}) }
    }

    @Test
    fun verifyEmail() {
        render("verify-email", 480, 460) { VerifyEmailContent("alice@example.com", FormState.Idle, {}, {}, {}, {}) }
        render("verify-email-error", 480, 500) { VerifyEmailContent("alice@example.com", FormState.Failed("That code is invalid or has expired."), {}, {}, {}, {}) }
    }

    @Test
    fun forgotPassword() {
        render("forgot-password-request", 480, 460) { ForgotPasswordContent(FormState.Idle, null, {}, { _, _, _ -> }, {}, {}) }
        render("forgot-password-reset", 480, 700) { ForgotPasswordContent(FormState.Idle, "alice@example.com", {}, { _, _, _ -> }, {}, {}) }
    }

    // ── dashboard ──────────────────────────────────────────────────────────

    @Test
    fun dashboard() {
        val organizations = groupByOrganization(departments)
        val content = @Composable {
            DashboardContent("Alice", loaded, organizations, {}, {}, {}, {})
        }
        render("dashboard-desktop", 1200, 800) { Shell(Screen.Dashboard, "Organizations", content = content) }
        render("dashboard-phone", 390, 844) { Shell(Screen.Dashboard, "Organizations", content = content) }
        render("dashboard-empty", 1200, 700) {
            Shell(Screen.Dashboard, "Organizations") { DashboardContent("Alice", DepartmentsState.Loaded(emptyList()), emptyList(), {}, {}, {}, {}) }
        }
        render("dashboard-failed", 1200, 700) {
            Shell(Screen.Dashboard, "Organizations") {
                DashboardContent("Alice", DepartmentsState.Failed("Could not reach the server. Check your connection and try again."), emptyList(), {}, {}, {}, {})
            }
        }
    }

    @Test
    fun organization() {
        render("organization-desktop", 1200, 700) {
            Shell(Screen.OrganizationDetail, "Acme Corp", canGoBack = true) {
                OrganizationContent(groupByOrganization(departments).first(), {}, {})
            }
        }
    }

    // ── departments ────────────────────────────────────────────────────────

    @Test
    fun departments() {
        render("departments-desktop", 1200, 800) {
            Shell(Screen.Departments, "Departments") { DepartmentsContent(loaded, {}, {}) }
        }
        render("departments-empty", 1200, 700) {
            Shell(Screen.Departments, "Departments") { DepartmentsContent(DepartmentsState.Loaded(emptyList()), {}, {}) }
        }
    }

    private val members = listOf(
        DepartmentMember(1, "Alice Wanjiku", "admin", "alice@example.com"),
        DepartmentMember(2, "Brian Otieno", "lead", "brian@example.com"),
        DepartmentMember(3, "Chloe Njeri", "member", "chloe@example.com"),
        DepartmentMember(4, "David Kamau", "member", "david@example.com"),
    )
    private fun details(department: Department, manager: Boolean) = DetailsState.Loaded(
        DepartmentDetails(
            id = department.id,
            name = department.name,
            slug = department.slug,
            organization = department.organization,
            role = department.role,
            memberCount = members.size,
            members = members,
            canManage = manager,
            accessCode = if (manager) "K7M2Q9XP4T" else null,
            invitations = if (manager) listOf(DepartmentInvitation(1, "newhire@example.com"), DepartmentInvitation(2, "contractor@example.com")) else emptyList(),
        ),
    )
    private val syncedFolder = folder.copy(
        directories = listOf("Roadmap 2026", "Roadmap 2026/notes", "Empty folder"),
        files = folder.files + LocalFile("Draft.txt", 320, now - 20_000),
    )
    private val syncing = DepartmentSyncState(
        folder = syncedFolder,
        status = SyncStatus(SyncStatus.Phase.Synced, lastSyncedAt = now - 5_000),
        pending = setOf("Draft.txt"),
    )

    @Composable
    private fun Department(
        department: Department,
        details: DetailsState,
        sync: DepartmentSyncState?,
        path: String = "",
        canOpenFolder: Boolean = true,
    ) = DepartmentContent(
        department, details, sync, path, now, addingFolder = false, canOpenFolder = canOpenFolder,
        onOpenExplorerFolder = {}, onAddFolder = {}, onOpenFolder = {}, onOpenFile = {}, onSyncNow = {}, onRetryDetails = {},
        onOpenMembers = {}, onOpenAccessCode = {}, onOpenInvitations = {}, onOpenLeave = {},
    )

    @Test
    fun department() {
        render("department-desktop", 1200, 1000) {
            Shell(Screen.DepartmentDetail, "Engineering", canGoBack = true) { Department(departments[0], details(departments[0], manager = true), syncing) }
        }
        render("department-subfolder", 1200, 800) {
            Shell(Screen.DepartmentDetail, "Engineering", canGoBack = true) { Department(departments[0], details(departments[0], manager = true), syncing, path = "Roadmap 2026") }
        }
        render("department-member", 1200, 800) {
            Shell(Screen.DepartmentDetail, "Finance", canGoBack = true) { Department(departments[2], details(departments[2], manager = false), syncing) }
        }
        render("department-empty", 1200, 800) {
            val empty = DepartmentSyncState(LocalFolderState("/home/alice/CTOS/acme-corp/engineering", watching = true), SyncStatus(SyncStatus.Phase.Synced, lastSyncedAt = now))
            Shell(Screen.DepartmentDetail, "Engineering", canGoBack = true) { Department(departments[0], details(departments[0], manager = true), empty) }
        }
        render("department-loading-offline", 1200, 900) {
            val offline = syncing.copy(status = SyncStatus(SyncStatus.Phase.Offline, error = "Could not reach the server. Check your connection and try again."))
            Shell(Screen.DepartmentDetail, "Engineering", canGoBack = true) { Department(departments[0], DetailsState.Failed("Could not reach the server."), offline) }
        }
        render("department-problems", 1200, 1000) {
            val problems = syncing.copy(status = SyncStatus(SyncStatus.Phase.Synced, lastSyncedAt = now, problems = listOf("bad\\name.txt: this name cannot be synced")))
            Shell(Screen.DepartmentDetail, "Engineering", canGoBack = true) { Department(departments[0], DetailsState.Loading, problems) }
        }
        render("department-phone", 390, 1900) {
            Shell(Screen.DepartmentDetail, "Engineering", canGoBack = true) { Department(departments[0], details(departments[0], manager = true), syncing, canOpenFolder = false) }
        }
    }

    // ── the department's own pages ────────────────────────────────────────

    @Test
    fun departmentMembers() {
        render("department-members", 900, 700) {
            Shell(Screen.DepartmentMembers, "Members", canGoBack = true) {
                DepartmentMembersContent(departments[0], details(departments[0], manager = true), onRetry = {})
            }
        }
    }

    @Test
    fun departmentAccessCode() {
        render("department-access-code", 900, 700) {
            Shell(Screen.DepartmentAccessCode, "Access code", canGoBack = true) {
                DepartmentAccessCodeContent(departments[0], details(departments[0], manager = true), onCopy = {}, onGenerate = {})
            }
        }
    }

    @Test
    fun departmentInvitations() {
        render("department-invitations", 900, 700) {
            Shell(Screen.DepartmentInvitations, "Invitations", canGoBack = true) {
                DepartmentInvitationsContent(departments[0], details(departments[0], manager = true), onWithdraw = {}, onInvite = {})
            }
        }
        render("department-invitations-empty", 900, 500) {
            Shell(Screen.DepartmentInvitations, "Invitations", canGoBack = true) {
                DepartmentInvitationsContent(departments[2], details(departments[2], manager = false), onWithdraw = {}, onInvite = {})
            }
        }
    }

    @Test
    fun departmentLeave() {
        render("department-leave", 900, 500) {
            Shell(Screen.DepartmentLeave, "Leave department", canGoBack = true) {
                DepartmentLeaveContent(departments[0], FormState.Idle, onConfirm = {}, onCancel = {})
            }
        }
        render("department-leave-failed", 900, 500) {
            Shell(Screen.DepartmentLeave, "Leave department", canGoBack = true) {
                DepartmentLeaveContent(departments[0], FormState.Failed("Could not leave the department."), onConfirm = {}, onCancel = {})
            }
        }
    }

    @Test
    fun generateAccessCodeDialog() {
        render("generate-code-dialog-confirm", 520, 420) {
            GenerateAccessCodeCard(FormState.Idle, null, {}, {}, {})
        }
        render("generate-code-dialog-new-code", 520, 420) {
            GenerateAccessCodeCard(FormState.Idle, "K7M2Q9XP4T", {}, {}, {})
        }
    }

    @Test
    fun searchDialog() {
        render("search-dialog-empty", 520, 260) {
            DialogCard("Search", onDismiss = {}, icon = Icons.Default.Search) {
                SearchDialogContent("", GlobalSearchResults(), {}, {})
            }
        }
        render("search-dialog-results", 520, 620) {
            val results = GlobalSearchResults(
                organizations = listOf(groupByOrganization(departments)[0]),
                departments = departments.take(2),
                files = listOf(
                    FileSearchResult("engineering", "Engineering", "Roadmap 2026/plan.md", isFolder = false),
                    FileSearchResult("engineering", "Engineering", "Roadmap 2026", isFolder = true),
                ),
            )
            DialogCard("Search", onDismiss = {}, icon = Icons.Default.Search) {
                SearchDialogContent("eng", results, {}, {})
            }
        }
    }

    // ── settings and popups ────────────────────────────────────────────────

    @Test
    fun settings() {
        render("settings-desktop", 1200, 900) {
            Shell(Screen.Settings, "Settings") {
                SettingsContent(
                    userName = "Alice Wanjiku",
                    userEmail = "alice@example.com",
                    deviceName = "alice-laptop (Linux)",
                    serverUrl = "http://localhost:9090/api/v1",
                    syncRoot = "/home/alice/CTOS",
                    version = "1.0.0",
                    canOpenFolder = true,
                    onOpenFolder = {},
                    onChangePassword = {},
                    onLogout = {},
                )
            }
        }
    }

    @Test
    fun joinDialog() {
        render("join-dialog", 520, 470) {
            DialogCard("Join a department", onDismiss = {}, icon = Icons.Default.VpnKey, description = "Ask your team for the department ID and its access code.") {
                JoinDepartmentForm(FormState.Idle, { _, _ -> }, {})
            }
        }
        render("join-dialog-error", 520, 520) {
            DialogCard("Join a department", onDismiss = {}, icon = Icons.Default.VpnKey, description = "Ask your team for the department ID and its access code.") {
                JoinDepartmentForm(FormState.Failed("The department ID or access code is incorrect."), { _, _ -> }, {})
            }
        }
    }

    // ── Secure Drop (docs/secure-drop.md) ─────────────────────────────────

    private val secureDropRecipients = listOf(
        SecureDropRecipientOption(1, "Alice Wanjiku", "-----BEGIN PUBLIC KEY-----\nMFk...\n-----END PUBLIC KEY-----", "AB12CD34EF56AB12CD34EF56AB12CD34EF56AB12CD34EF56AB12CD34EF56"),
        SecureDropRecipientOption(2, "Brian Otieno", "-----BEGIN PUBLIC KEY-----\nMFk...\n-----END PUBLIC KEY-----", "12CD34EF56AB12CD34EF56AB12CD34EF56AB12CD34EF56AB12CD34EF56AB"),
        SecureDropRecipientOption(3, "Chloe Njeri"),
    )
    private val secureDropDrops = SecureDropLists(
        sent = listOf(SecureDropSummary(1, 1, "Engineering", 1, "Alice Wanjiku", "Q3-budget.xlsx", 1_468_006, "iv", now - 5 * minute)),
        received = listOf(SecureDropSummary(2, 1, "Engineering", 2, "Brian Otieno", "Contract-draft.pdf", 240_000, "iv", now - 2 * 60 * minute)),
    )

    @Composable
    private fun SecureDrop(
        keyState: SecureDropKeyState,
        keySetupState: FormState = FormState.Idle,
        pendingKeyPairFingerprint: String? = null,
        lists: SecureDropLists = SecureDropLists(emptyList(), emptyList()),
    ) = SecureDropContent(
        keyState, keySetupState, pendingKeyPairFingerprint, lists, departments,
        onSetUpKeyPair = { _, _ -> }, onRestartKeySetup = {}, onEncryptAndSaveKey = { _, _ -> }, onEditKeySetup = {},
        onCopyKey = {}, onRemoveKey = {}, onSend = {}, onSendByEmail = {}, onOpenDrop = {}, onDeleteDrop = {},
    )

    @Test
    fun secureDrop() {
        render("secure-drop-setup", 1200, 900) {
            Shell(Screen.SecureDrop, "Secure Drop") { SecureDrop(SecureDropKeyState.None) }
        }
        render("secure-drop-setup-pair-error", 1200, 950) {
            Shell(Screen.SecureDrop, "Secure Drop") { SecureDrop(SecureDropKeyState.None, FormState.Failed("Those do not look like a valid key pair.")) }
        }
        render("secure-drop-setup-passphrase-step", 1200, 950) {
            Shell(Screen.SecureDrop, "Secure Drop") {
                SecureDrop(SecureDropKeyState.None, pendingKeyPairFingerprint = "8a87870664097a7e1f9b2321956e59133e04bd3b2575b89aaf97c5f062f51912")
            }
        }
        render("secure-drop-setup-passphrase-error", 1200, 1000) {
            Shell(Screen.SecureDrop, "Secure Drop") {
                SecureDrop(
                    SecureDropKeyState.None,
                    FormState.Failed("The passphrases do not match."),
                    pendingKeyPairFingerprint = "8a87870664097a7e1f9b2321956e59133e04bd3b2575b89aaf97c5f062f51912",
                )
            }
        }
        render("secure-drop-ready", 1200, 1000) {
            val ready = SecureDropKeyState.Ready("SHA-256: AB12 CD34 EF56 AB12 CD34 EF56 AB12 CD34 EF56 AB12 CD34 EF56 AB12 CD34", now - 30 * 24 * 60 * minute)
            Shell(Screen.SecureDrop, "Secure Drop") { SecureDrop(ready, lists = secureDropDrops) }
        }
        render("secure-drop-empty", 1200, 700) {
            val ready = SecureDropKeyState.Ready("SHA-256: AB12 CD34 EF56 AB12 CD34 EF56 AB12 CD34 EF56 AB12 CD34 EF56 AB12 CD34", now)
            Shell(Screen.SecureDrop, "Secure Drop") { SecureDrop(ready) }
        }
    }

    private class FakePickedFile(override val name: String) : PickedFile {
        override suspend fun readBytes(): ByteArray = ByteArray(0)
    }

    @Test
    fun sendSecureDrop() {
        render("send-secure-drop-empty", 900, 700) {
            Shell(Screen.SecureDropSend, "Secure Drop", canGoBack = true) {
                SendSecureDropContent("Engineering", secureDropRecipients, myUserId = 1L, FormState.Idle, onChooseFile = {}, pickedFile = null, onSend = { _, _ -> }, onEdit = {})
            }
        }
        render("send-secure-drop-picked", 900, 700) {
            Shell(Screen.SecureDropSend, "Secure Drop", canGoBack = true) {
                SendSecureDropContent("Engineering", secureDropRecipients, myUserId = 1L, FormState.Idle, onChooseFile = {}, pickedFile = FakePickedFile("Q3-budget.xlsx"), onSend = { _, _ -> }, onEdit = {})
            }
        }
        render("send-secure-drop-no-recipients", 900, 500) {
            Shell(Screen.SecureDropSend, "Secure Drop", canGoBack = true) {
                SendSecureDropContent("Finance", emptyList(), myUserId = 1L, FormState.Idle, onChooseFile = {}, pickedFile = null, onSend = { _, _ -> }, onEdit = {})
            }
        }
    }

    @Test
    fun sendSecureDropByEmail() {
        render("send-secure-drop-by-email-empty", 900, 600) {
            Shell(Screen.SecureDropSendByEmail, "Secure Drop", canGoBack = true) {
                SendSecureDropByEmailContent(FormState.Idle, onChooseFile = {}, pickedFile = null, onSend = { _, _ -> }, onEdit = {})
            }
        }
        render("send-secure-drop-by-email-picked", 900, 600) {
            Shell(Screen.SecureDropSendByEmail, "Secure Drop", canGoBack = true) {
                SendSecureDropByEmailContent(FormState.Idle, onChooseFile = {}, pickedFile = FakePickedFile("contract.pdf"), onSend = { _, _ -> }, onEdit = {})
            }
        }
        render("send-secure-drop-by-email-error", 900, 650) {
            Shell(Screen.SecureDropSendByEmail, "Secure Drop", canGoBack = true) {
                SendSecureDropByEmailContent(FormState.Failed("Could not send this. Check the fields and try again."), onChooseFile = {}, pickedFile = FakePickedFile("contract.pdf"), onSend = { _, _ -> }, onEdit = {})
            }
        }
    }

    @Test
    fun openSecureDropDialog() {
        render("open-secure-drop-dialog", 520, 320) {
            DialogCard("Open \"${secureDropDrops.received[0].name}\"", onDismiss = {}, icon = Icons.Default.Lock) {
                OpenSecureDropForm(FormState.Idle, onConfirm = {}, onEdit = {})
            }
        }
        render("open-secure-drop-dialog-error", 520, 380) {
            DialogCard("Open \"${secureDropDrops.received[0].name}\"", onDismiss = {}, icon = Icons.Default.Lock) {
                OpenSecureDropForm(FormState.Failed("That did not decrypt this. Check your passphrase and try again."), onConfirm = {}, onEdit = {})
            }
        }
    }

    @Test
    fun toasts() {
        render("toast-success", 520, 120) { ToastCard(Toast("You joined Engineering.", ToastKind.Success)) }
        render("toast-error", 520, 120) { ToastCard(Toast("You were signed out. Please log in again.", ToastKind.Error)) }
    }
}
