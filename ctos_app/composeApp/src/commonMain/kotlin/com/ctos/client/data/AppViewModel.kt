package com.ctos.client.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ctos.client.ToastManager
import com.ctos.client.db.Session
import com.ctos.client.deviceName
import com.ctos.client.network.CtosApi
import com.ctos.client.network.Department
import com.ctos.client.network.DepartmentDetails
import com.ctos.client.network.OrganizationSummaryDto
import com.ctos.client.network.WsEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where the user is: a screen, and for the detail screens which organization or department. */
data class Destination(
    val screen: Screen,
    val organizationSlug: String? = null,
    val departmentSlug: String? = null,
)

sealed interface DepartmentsState {
    data object Loading : DepartmentsState

    data class Loaded(val departments: List<Department>, val refreshing: Boolean = false) : DepartmentsState

    data class Failed(val message: String) : DepartmentsState
}

/** The members, access code and invitations of the open department (what its page on the website shows next to the files). */
sealed interface DetailsState {
    data object Loading : DetailsState

    data class Loaded(val details: DepartmentDetails) : DetailsState

    data class Failed(val message: String) : DetailsState
}

/** A form that sends something to the server: idle, waiting for the answer, or refused with a message. */
sealed interface FormState {
    data object Idle : FormState

    data object Working : FormState

    data class Failed(val message: String) : FormState
}

class AppViewModel : ViewModel() {
    private val _destination = MutableStateFlow(Destination(Screen.Loading))
    val destination: StateFlow<Destination> = _destination.asStateFlow()

    private val _canGoBack = MutableStateFlow(false)
    val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()

    private val history = ArrayDeque<Destination>()

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    private val _departmentsState = MutableStateFlow<DepartmentsState>(DepartmentsState.Loading)
    val departmentsState: StateFlow<DepartmentsState> = _departmentsState.asStateFlow()

    private val _loginState = MutableStateFlow<FormState>(FormState.Idle)
    val loginState: StateFlow<FormState> = _loginState.asStateFlow()

    private val _registerState = MutableStateFlow<FormState>(FormState.Idle)
    val registerState: StateFlow<FormState> = _registerState.asStateFlow()

    /** The address a code was just emailed to, while confirming it after registering. */
    private val _pendingVerificationEmail = MutableStateFlow("")
    val pendingVerificationEmail: StateFlow<String> = _pendingVerificationEmail.asStateFlow()

    private val _verifyEmailState = MutableStateFlow<FormState>(FormState.Idle)
    val verifyEmailState: StateFlow<FormState> = _verifyEmailState.asStateFlow()

    private val _forgotPasswordState = MutableStateFlow<FormState>(FormState.Idle)
    val forgotPasswordState: StateFlow<FormState> = _forgotPasswordState.asStateFlow()

    /** Null while asking for an email address; the address a reset code was just sent to once it has been. */
    private val _forgotPasswordEmailSent = MutableStateFlow<String?>(null)
    val forgotPasswordEmailSent: StateFlow<String?> = _forgotPasswordEmailSent.asStateFlow()

    private val _joinDialogOpen = MutableStateFlow(false)
    val joinDialogOpen: StateFlow<Boolean> = _joinDialogOpen.asStateFlow()

    private val _joinState = MutableStateFlow<FormState>(FormState.Idle)
    val joinState: StateFlow<FormState> = _joinState.asStateFlow()

    // ── organizations, including ones with no departments yet ───────────────

    private val _allOrganizations = MutableStateFlow<List<OrganizationSummaryDto>>(emptyList())

    private val _createOrgDialogOpen = MutableStateFlow(false)
    val createOrgDialogOpen: StateFlow<Boolean> = _createOrgDialogOpen.asStateFlow()

    private val _createOrgState = MutableStateFlow<FormState>(FormState.Idle)
    val createOrgState: StateFlow<FormState> = _createOrgState.asStateFlow()

    // ── sharing the open department ──────────────────────────────────────────

    private val _shareDialogOpen = MutableStateFlow(false)
    val shareDialogOpen: StateFlow<Boolean> = _shareDialogOpen.asStateFlow()

    private val _shareState = MutableStateFlow<FormState>(FormState.Idle)
    val shareState: StateFlow<FormState> = _shareState.asStateFlow()

    private val _generateCodeDialogOpen = MutableStateFlow(false)
    val generateCodeDialogOpen: StateFlow<Boolean> = _generateCodeDialogOpen.asStateFlow()

    private val _generateCodeState = MutableStateFlow<FormState>(FormState.Idle)
    val generateCodeState: StateFlow<FormState> = _generateCodeState.asStateFlow()

    /** The code the last "generate new code" made, so the dialog can show it once, to copy. */
    private val _generatedCode = MutableStateFlow<String?>(null)
    val generatedCode: StateFlow<String?> = _generatedCode.asStateFlow()

    /** Keeps every department's folder on this device in step with the server. */
    private val syncManager = SyncManager(viewModelScope, api = { CtosApi.default }, store = AppRepository.syncStore, deviceLabel = deviceName())

    /** How the sync of the open department is going, and its folder on this device, while a department is open. */
    val departmentSync: StateFlow<DepartmentSyncState?> = combine(_destination, syncManager.states) { destination, states ->
        if (destination.screen in departmentScopedScreens) states[destination.departmentSlug] else null
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The local folder of the department that is open, while one is. */
    val localFolder: StateFlow<LocalFolderState?> = combine(departmentSync, _destination) { sync, destination ->
        sync?.folder ?: if (destination.screen in departmentScopedScreens) departmentBySlug(destination.departmentSlug)?.let { LocalFolderState(departmentFolder(it)) } else null
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _details = MutableStateFlow<DetailsState>(DetailsState.Loading)
    val details: StateFlow<DetailsState> = _details.asStateFlow()

    /** The folder the file explorer of the open department is showing ("" is the top). */
    private val _explorerPath = MutableStateFlow("")
    val explorerPath: StateFlow<String> = _explorerPath.asStateFlow()

    private val _addingFolder = MutableStateFlow(false)
    val addingFolder: StateFlow<Boolean> = _addingFolder.asStateFlow()

    private val _leaveState = MutableStateFlow<FormState>(FormState.Idle)
    val leaveState: StateFlow<FormState> = _leaveState.asStateFlow()

    private var openedDepartment: String? = null

    init {
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { AppRepository.getCurrentSession() }
            _session.value = saved
            show(Destination(if (saved != null) Screen.Dashboard else Screen.Login))
            if (saved != null) loadDepartments()
        }
    }

    val departments: List<Department> get() = (departmentsState.value as? DepartmentsState.Loaded)?.departments.orEmpty()

    fun departmentBySlug(slug: String?): Department? = departments.firstOrNull { it.slug == slug }

    /** Every organization the user can see, including ones with no departments yet. */
    val organizations: StateFlow<List<OrganizationSummary>> = combine(_departmentsState, _allOrganizations) { _, all ->
        mergeEmptyOrganizations(groupByOrganization(departments), all)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun organizationBySlug(slug: String?): OrganizationSummary? = organizations.value.firstOrNull { it.organization.slug == slug }

    // ── navigation ─────────────────────────────────────────────────────────

    /** Opens a top-level screen (one in the navigation), forgetting where the user came from. */
    fun goTo(screen: Screen) {
        history.clear()
        show(Destination(screen))
    }

    fun openOrganization(organizationSlug: String) = push(Destination(Screen.OrganizationDetail, organizationSlug))

    fun openDepartment(department: Department) =
        push(Destination(Screen.DepartmentDetail, department.organization.slug, department.slug))

    /** The open department's own sections, each its own page, like on the website. */
    fun openDepartmentMembers() = pushDepartmentScreen(Screen.DepartmentMembers)

    fun openDepartmentAccessCode() = pushDepartmentScreen(Screen.DepartmentAccessCode)

    fun openDepartmentInvitations() = pushDepartmentScreen(Screen.DepartmentInvitations)

    fun openDepartmentLeave() = pushDepartmentScreen(Screen.DepartmentLeave)

    private fun pushDepartmentScreen(screen: Screen) {
        val current = _destination.value
        push(Destination(screen, current.organizationSlug, current.departmentSlug))
    }

    /** Goes back one step. Returns false when there is nowhere to go back to. */
    fun back(): Boolean {
        // Inside a department's folders, Back goes up a folder before it leaves the department.
        if (_destination.value.screen == Screen.DepartmentDetail && _explorerPath.value.isNotEmpty()) {
            _explorerPath.value = parentPath(_explorerPath.value)
            return true
        }
        val previous = history.removeLastOrNull() ?: return false
        show(previous)
        return true
    }

    private fun push(next: Destination) {
        history.addLast(_destination.value)
        show(next)
    }

    private fun show(next: Destination) {
        _destination.value = next
        _canGoBack.value = history.isNotEmpty()
        followOpenDepartment(next)
    }

    // ── departments ────────────────────────────────────────────────────────

    /** Fetches the user's departments. [silent] refreshes what is on screen without a spinner. */
    fun loadDepartments(silent: Boolean = false) {
        viewModelScope.launch {
            val current = _departmentsState.value
            if (current is DepartmentsState.Loaded) {
                if (!silent) _departmentsState.value = current.copy(refreshing = true)
            } else {
                _departmentsState.value = DepartmentsState.Loading
            }

            AppRepository.fetchOrganizations().onSuccess { _allOrganizations.value = it }

            AppRepository.fetchDepartments()
                .onSuccess {
                    _departmentsState.value = DepartmentsState.Loaded(it)
                    if (_session.value != null) syncManager.setDepartments(it)
                }
                .onFailure { error ->
                    val message = error.message ?: "The departments could not be loaded."
                    if (current is DepartmentsState.Loaded) {
                        // Keep showing what we have; a refresh that fails is not worth an empty screen.
                        _departmentsState.value = current.copy(refreshing = false)
                        if (!silent) ToastManager.error(message)
                    } else {
                        _departmentsState.value = DepartmentsState.Failed(message)
                    }
                }
        }
    }

    // ── the global search popup ──────────────────────────────────────────────

    private val _searchDialogOpen = MutableStateFlow(false)
    val searchDialogOpen: StateFlow<Boolean> = _searchDialogOpen.asStateFlow()

    private val _globalSearchQuery = MutableStateFlow("")
    val globalSearchQuery: StateFlow<String> = _globalSearchQuery.asStateFlow()

    /** Organizations, departments, and every synced department's files and folders that match. */
    val globalSearchResults: StateFlow<GlobalSearchResults> = combine(_globalSearchQuery, organizations, _departmentsState, syncManager.states) { query, orgs, departmentsState, syncStates ->
        searchEverything(query, orgs, (departmentsState as? DepartmentsState.Loaded)?.departments.orEmpty(), syncStates)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, GlobalSearchResults())

    /** The search icon: opens the popup that searches everything at once. */
    fun openSearchDialog() {
        _globalSearchQuery.value = ""
        _searchDialogOpen.value = true
    }

    fun closeSearchDialog() {
        _searchDialogOpen.value = false
    }

    fun setGlobalSearchQuery(query: String) {
        _globalSearchQuery.value = query
    }

    /** A result in the search popup was picked: go to where it lives, and close the popup. */
    fun openSearchResult(result: SearchResult) {
        closeSearchDialog()
        when (result) {
            is SearchResult.OrganizationHit -> openOrganization(result.organization.organization.slug)
            is SearchResult.DepartmentHit -> openDepartment(result.department)
            is SearchResult.FileHit -> {
                val department = departmentBySlug(result.file.departmentSlug) ?: return
                openDepartment(department)
                openExplorerFolder(result.file.folderToOpen)
            }
        }
    }

    // ── signing in and out ─────────────────────────────────────────────────

    fun login(email: String, password: String) {
        if (_loginState.value is FormState.Working) return
        if (email.isBlank() || password.isEmpty()) {
            _loginState.value = FormState.Failed("Enter your email address and your password.")
            return
        }

        _loginState.value = FormState.Working
        viewModelScope.launch {
            AppRepository.login(email, password)
                .onSuccess {
                    _session.value = withContext(Dispatchers.IO) { AppRepository.getCurrentSession() }
                    _loginState.value = FormState.Idle
                    goTo(Screen.Dashboard)
                    loadDepartments()
                }
                .onFailure { _loginState.value = FormState.Failed(it.message ?: "Could not log in.") }
        }
    }

    fun clearLoginError() {
        if (_loginState.value is FormState.Failed) _loginState.value = FormState.Idle
    }

    fun logout() {
        viewModelScope.launch {
            AppRepository.logout()
            forgetEverything()
        }
    }

    /** The server no longer accepts our token (revoked, expired): back to the login screen. */
    fun sessionExpired() {
        if (_session.value == null) return
        AppRepository.clearSession()
        forgetEverything()
        ToastManager.error("You were signed out. Please log in again.")
    }

    // ── creating an account ───────────────────────────────────────────────

    fun openRegister() {
        _registerState.value = FormState.Idle
        push(Destination(Screen.Register))
    }

    fun clearRegisterError() {
        if (_registerState.value is FormState.Failed) _registerState.value = FormState.Idle
    }

    fun register(name: String, email: String, password: String, confirmation: String) {
        if (_registerState.value is FormState.Working) return
        if (name.isBlank() || email.isBlank() || password.isEmpty()) {
            _registerState.value = FormState.Failed("Fill in your name, email address and a password.")
            return
        }
        if (password != confirmation) {
            _registerState.value = FormState.Failed("The passwords do not match.")
            return
        }

        _registerState.value = FormState.Working
        viewModelScope.launch {
            AppRepository.register(name, email, password, confirmation)
                .onSuccess {
                    _registerState.value = FormState.Idle
                    _pendingVerificationEmail.value = it.email
                    _verifyEmailState.value = FormState.Idle
                    push(Destination(Screen.VerifyEmail))
                }
                .onFailure { _registerState.value = FormState.Failed(it.message ?: "Could not create the account.") }
        }
    }

    fun clearVerifyEmailError() {
        if (_verifyEmailState.value is FormState.Failed) _verifyEmailState.value = FormState.Idle
    }

    /** Confirms the address with the code that was emailed, and finishes signing in. */
    fun verifyEmail(code: String) {
        if (_verifyEmailState.value is FormState.Working) return
        if (code.isBlank()) {
            _verifyEmailState.value = FormState.Failed("Enter the code we emailed you.")
            return
        }

        _verifyEmailState.value = FormState.Working
        viewModelScope.launch {
            AppRepository.verifyEmail(_pendingVerificationEmail.value, code)
                .onSuccess {
                    _session.value = withContext(Dispatchers.IO) { AppRepository.getCurrentSession() }
                    _verifyEmailState.value = FormState.Idle
                    ToastManager.success("Your account is ready.")
                    goTo(Screen.Dashboard)
                    loadDepartments()
                }
                .onFailure { _verifyEmailState.value = FormState.Failed(it.message ?: "That code is invalid or has expired.") }
        }
    }

    fun resendVerificationCode() {
        viewModelScope.launch {
            AppRepository.resendVerificationCode(_pendingVerificationEmail.value)
                .onSuccess { ToastManager.success("We sent a new code.") }
                .onFailure { ToastManager.error(it.message ?: "Could not send a new code.") }
        }
    }

    // ── forgotten password ────────────────────────────────────────────────

    fun openForgotPassword() {
        _forgotPasswordState.value = FormState.Idle
        _forgotPasswordEmailSent.value = null
        push(Destination(Screen.ForgotPassword))
    }

    fun clearForgotPasswordError() {
        if (_forgotPasswordState.value is FormState.Failed) _forgotPasswordState.value = FormState.Idle
    }

    fun requestPasswordResetCode(email: String) {
        if (_forgotPasswordState.value is FormState.Working) return
        if (email.isBlank()) {
            _forgotPasswordState.value = FormState.Failed("Enter your email address.")
            return
        }

        _forgotPasswordState.value = FormState.Working
        viewModelScope.launch {
            AppRepository.forgotPassword(email)
                .onSuccess {
                    _forgotPasswordState.value = FormState.Idle
                    _forgotPasswordEmailSent.value = email.trim().lowercase()
                }
                .onFailure { _forgotPasswordState.value = FormState.Failed(it.message ?: "Could not send the code.") }
        }
    }

    /** Sets the new password with the code that was emailed, then sends the app back to log in with it. */
    fun resetPassword(code: String, password: String, confirmation: String) {
        val email = _forgotPasswordEmailSent.value ?: return
        if (_forgotPasswordState.value is FormState.Working) return
        if (code.isBlank()) {
            _forgotPasswordState.value = FormState.Failed("Enter the code we emailed you.")
            return
        }
        if (password != confirmation) {
            _forgotPasswordState.value = FormState.Failed("The passwords do not match.")
            return
        }

        _forgotPasswordState.value = FormState.Working
        viewModelScope.launch {
            AppRepository.resetPassword(email, code, password, confirmation)
                .onSuccess {
                    _forgotPasswordState.value = FormState.Idle
                    _forgotPasswordEmailSent.value = null
                    ToastManager.success("Your password has been reset. Log in with it now.")
                    goTo(Screen.Login)
                }
                .onFailure { _forgotPasswordState.value = FormState.Failed(it.message ?: "That code is invalid or has expired.") }
        }
    }

    private fun forgetEverything() {
        syncManager.stopAll()
        _session.value = null
        _departmentsState.value = DepartmentsState.Loading
        _allOrganizations.value = emptyList()
        _searchDialogOpen.value = false
        _globalSearchQuery.value = ""
        _joinState.value = FormState.Idle
        _joinDialogOpen.value = false
        goTo(Screen.Login)
    }

    // ── joining a department ───────────────────────────────────────────────

    fun openJoinDialog() {
        _joinState.value = FormState.Idle
        _joinDialogOpen.value = true
    }

    fun closeJoinDialog() {
        _joinDialogOpen.value = false
        _joinState.value = FormState.Idle
    }

    fun clearJoinError() {
        if (_joinState.value is FormState.Failed) _joinState.value = FormState.Idle
    }

    fun join(departmentId: String, code: String) {
        if (_joinState.value is FormState.Working) return
        if (departmentId.isBlank() || code.isBlank()) {
            _joinState.value = FormState.Failed("Enter the department ID and its access code.")
            return
        }

        _joinState.value = FormState.Working
        viewModelScope.launch {
            AppRepository.joinDepartment(departmentId, code)
                .onSuccess { department ->
                    val loaded = _departmentsState.value as? DepartmentsState.Loaded
                    _departmentsState.value = DepartmentsState.Loaded(
                        (loaded?.departments.orEmpty().filter { it.id != department.id }) + department,
                    )
                    closeJoinDialog()
                    ToastManager.success("You joined ${department.name}.")
                    openDepartment(department)
                    loadDepartments(silent = true)
                }
                .onFailure { _joinState.value = FormState.Failed(it.message ?: "Could not join the department.") }
        }
    }

    // ── creating an organization ─────────────────────────────────────────────

    fun openCreateOrgDialog() {
        _createOrgState.value = FormState.Idle
        _createOrgDialogOpen.value = true
    }

    fun closeCreateOrgDialog() {
        _createOrgDialogOpen.value = false
        _createOrgState.value = FormState.Idle
    }

    fun clearCreateOrgError() {
        if (_createOrgState.value is FormState.Failed) _createOrgState.value = FormState.Idle
    }

    fun createOrganization(name: String) {
        if (_createOrgState.value is FormState.Working) return
        if (name.isBlank()) {
            _createOrgState.value = FormState.Failed("Enter a name for the organization.")
            return
        }

        _createOrgState.value = FormState.Working
        viewModelScope.launch {
            AppRepository.createOrganization(name)
                .onSuccess { organization ->
                    _allOrganizations.value = _allOrganizations.value.filter { it.id != organization.id } + organization
                    closeCreateOrgDialog()
                    ToastManager.success("${organization.name} was created.")
                    openOrganization(organization.slug)
                }
                .onFailure { _createOrgState.value = FormState.Failed(it.message ?: "Could not create the organization.") }
        }
    }

    // ── the server's nudges ────────────────────────────────────────────────

    fun onServerEvent(event: WsEvent) {
        if (event.type != "department.changed") return
        loadDepartments(silent = true)
        event.departmentId?.let { id ->
            syncManager.nudge(id)
            if (departments.firstOrNull { it.id == id }?.slug == openedDepartment) loadDetails(openedDepartment!!, silent = true)
        }
    }

    /** The WebSocket was (re)connected: anything could have changed while it was not. */
    fun onRealtimeConnected() {
        loadDepartments(silent = true)
        syncManager.syncAll()
    }

    // ── the open department ────────────────────────────────────────────────

    private fun followOpenDepartment(destination: Destination) {
        val slug = if (destination.screen in departmentScopedScreens) destination.departmentSlug else null
        if (slug == openedDepartment) return

        openedDepartment = slug
        _explorerPath.value = ""
        _details.value = DetailsState.Loading
        _leaveState.value = FormState.Idle
        _shareDialogOpen.value = false
        _shareState.value = FormState.Idle
        _generateCodeDialogOpen.value = false
        _generateCodeState.value = FormState.Idle
        _generatedCode.value = null
        if (slug != null) loadDetails(slug)
    }

    private fun loadDetails(slug: String, silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _details.value = DetailsState.Loading
            AppRepository.fetchDepartmentDetails(slug)
                .onSuccess { if (openedDepartment == slug) _details.value = DetailsState.Loaded(it) }
                .onFailure { error ->
                    if (openedDepartment != slug) return@onFailure
                    // A refresh that fails keeps what is on screen.
                    if (!silent || _details.value !is DetailsState.Loaded) {
                        _details.value = DetailsState.Failed(error.message ?: "The department could not be loaded.")
                    }
                }
        }
    }

    fun reloadDetails() {
        openedDepartment?.let { loadDetails(it) }
    }

    // ── sharing the open department ──────────────────────────────────────────

    fun openShareDialog() {
        _shareState.value = FormState.Idle
        _shareDialogOpen.value = true
    }

    fun closeShareDialog() {
        _shareDialogOpen.value = false
        _shareState.value = FormState.Idle
    }

    fun clearShareError() {
        if (_shareState.value is FormState.Failed) _shareState.value = FormState.Idle
    }

    fun shareDepartment(email: String) {
        val slug = openedDepartment ?: return
        if (_shareState.value is FormState.Working) return
        if (email.isBlank()) {
            _shareState.value = FormState.Failed("Enter an email address.")
            return
        }

        _shareState.value = FormState.Working
        viewModelScope.launch {
            AppRepository.shareDepartment(slug, email)
                .onSuccess { outcome ->
                    closeShareDialog()
                    ToastManager.success(outcome.message)
                    reloadDetails()
                }
                .onFailure { _shareState.value = FormState.Failed(it.message ?: "Could not share the department.") }
        }
    }

    /** Withdraws a pending invitation of the open department. */
    fun withdrawInvitation(invitationId: Long) {
        val slug = openedDepartment ?: return
        viewModelScope.launch {
            AppRepository.withdrawInvitation(slug, invitationId)
                .onSuccess {
                    ToastManager.success("The invitation was withdrawn.")
                    reloadDetails()
                }
                .onFailure { ToastManager.error(it.message ?: "Could not withdraw the invitation.") }
        }
    }

    // ── generating a new access code ─────────────────────────────────────────

    fun openGenerateCodeDialog() {
        _generateCodeState.value = FormState.Idle
        _generatedCode.value = null
        _generateCodeDialogOpen.value = true
    }

    fun closeGenerateCodeDialog() {
        _generateCodeDialogOpen.value = false
        _generateCodeState.value = FormState.Idle
        _generatedCode.value = null
    }

    fun regenerateAccessCode() {
        val slug = openedDepartment ?: return
        if (_generateCodeState.value is FormState.Working) return

        _generateCodeState.value = FormState.Working
        viewModelScope.launch {
            AppRepository.regenerateAccessCode(slug)
                .onSuccess { code ->
                    _generateCodeState.value = FormState.Idle
                    _generatedCode.value = code
                    reloadDetails()
                }
                .onFailure { _generateCodeState.value = FormState.Failed(it.message ?: "Could not generate a new code.") }
        }
    }

    /** Shows the contents of a folder of the open department in the file explorer. */
    fun openExplorerFolder(path: String) {
        _explorerPath.value = path
    }

    /** Sends and fetches changes now, instead of waiting for the next check. */
    fun syncNow() {
        openedDepartment?.let { syncManager.syncNow(it) }
    }

    /**
     * Adds a folder the user picked to the open department: it is copied into the folder being looked at
     * (never over something already there), and the sync then uploads it, so everyone in the department gets it.
     */
    fun addFolder(picked: PickedFolder) {
        val slug = openedDepartment ?: return
        val department = departmentBySlug(slug) ?: return
        if (_addingFolder.value) return

        val inside = _explorerPath.value
        _addingFolder.value = true
        viewModelScope.launch {
            try {
                val root = departmentFolder(department)
                val (name, copied) = withContext(Dispatchers.IO) {
                    val files = localFiles(root)
                    val taken = { candidate: String ->
                        val path = if (inside.isEmpty()) candidate else "$inside/$candidate"
                        files.stat(path) != null || files.directoryExists(path)
                    }
                    val name = uniqueFolderName(picked.name, taken)
                    val destination = "$root/${if (inside.isEmpty()) name else "$inside/$name"}"
                    name to picked.copyInto(destination)
                }
                syncManager.syncNow(slug)
                ToastManager.success(
                    if (copied == 0) "\"$name\" was added, but it has no files in it."
                    else "Added \"$name\" with $copied ${if (copied == 1) "file" else "files"}. Uploading it now.",
                )
            } catch (e: Exception) {
                ToastManager.error("The folder could not be added: ${e.message ?: "unknown error"}")
            } finally {
                _addingFolder.value = false
            }
        }
    }

    // ── leaving a department ───────────────────────────────────────────────

    fun leaveDepartment() {
        val slug = openedDepartment ?: return
        if (_leaveState.value is FormState.Working) return
        val department = departmentBySlug(slug)

        _leaveState.value = FormState.Working
        viewModelScope.launch {
            AppRepository.leaveDepartment(slug)
                .onSuccess {
                    syncManager.stop(slug)
                    _leaveState.value = FormState.Idle
                    val loaded = _departmentsState.value as? DepartmentsState.Loaded
                    if (loaded != null) _departmentsState.value = loaded.copy(departments = loaded.departments.filter { it.slug != slug })
                    ToastManager.success("You left ${department?.name ?: "the department"}. Its files stay in the folder on this device.")
                    goTo(Screen.Dashboard)
                    loadDepartments(silent = true)
                }
                .onFailure { _leaveState.value = FormState.Failed(it.message ?: "Could not leave the department.") }
        }
    }

    override fun onCleared() {
        syncManager.stopAll()
    }
}
