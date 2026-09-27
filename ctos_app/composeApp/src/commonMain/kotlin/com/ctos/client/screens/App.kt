package com.ctos.client.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ctos.client.BuildKonfig
import com.ctos.client.PlatformBackHandler
import com.ctos.client.ToastManager
import com.ctos.client.copyToClipboard
import com.ctos.client.data.AppViewModel
import com.ctos.client.data.DepartmentEvents
import com.ctos.client.data.Screen
import com.ctos.client.data.rememberFolderPicker
import com.ctos.client.data.syncRootDirectory
import com.ctos.client.deviceName
import com.ctos.client.network.CtosApi
import com.ctos.client.network.SessionEvents
import com.ctos.client.openFile
import com.ctos.client.openFolder
import com.ctos.client.openUrl
import com.ctos.client.platformName
import com.ctos.client.screens.layouts.AppShell
import com.ctos.client.screens.widgets.ToastHost
import com.ctos.client.theme.AppTheme
import com.ctos.client.theme.CtosColors
import kotlinx.datetime.Clock

/** The whole app: the desktop window and the Android activity both show this. */
@Composable
fun App(viewModel: AppViewModel) {
    AppTheme {
        val destination by viewModel.destination.collectAsState()
        val canGoBack by viewModel.canGoBack.collectAsState()
        val session by viewModel.session.collectAsState()
        val departmentsState by viewModel.departmentsState.collectAsState()
        val loginState by viewModel.loginState.collectAsState()
        val registerState by viewModel.registerState.collectAsState()
        val pendingVerificationEmail by viewModel.pendingVerificationEmail.collectAsState()
        val verifyEmailState by viewModel.verifyEmailState.collectAsState()
        val forgotPasswordState by viewModel.forgotPasswordState.collectAsState()
        val forgotPasswordEmailSent by viewModel.forgotPasswordEmailSent.collectAsState()
        val joinState by viewModel.joinState.collectAsState()
        val joinDialogOpen by viewModel.joinDialogOpen.collectAsState()
        val departmentSync by viewModel.departmentSync.collectAsState()
        val details by viewModel.details.collectAsState()
        val explorerPath by viewModel.explorerPath.collectAsState()
        val addingFolder by viewModel.addingFolder.collectAsState()
        val leaveState by viewModel.leaveState.collectAsState()
        val createOrgDialogOpen by viewModel.createOrgDialogOpen.collectAsState()
        val createOrgState by viewModel.createOrgState.collectAsState()
        val shareDialogOpen by viewModel.shareDialogOpen.collectAsState()
        val shareState by viewModel.shareState.collectAsState()
        val generateCodeDialogOpen by viewModel.generateCodeDialogOpen.collectAsState()
        val generateCodeState by viewModel.generateCodeState.collectAsState()
        val generatedCode by viewModel.generatedCode.collectAsState()
        val organizations by viewModel.organizations.collectAsState()
        val searchDialogOpen by viewModel.searchDialogOpen.collectAsState()
        val globalSearchQuery by viewModel.globalSearchQuery.collectAsState()
        val globalSearchResults by viewModel.globalSearchResults.collectAsState()
        val folderPicker = rememberFolderPicker(viewModel::addFolder)

        // The server refused our token: back to the login screen.
        LaunchedEffect(Unit) {
            for (expired in SessionEvents.expired) viewModel.sessionExpired()
        }

        // While signed in, keep a connection open so changes made elsewhere arrive without a refresh.
        LaunchedEffect(Unit) {
            DepartmentEvents.run(
                isSignedIn = { viewModel.session.value != null },
                listen = { onOpen, onEvent -> CtosApi.default.listen(onOpen, onEvent) },
                onConnected = viewModel::onRealtimeConnected,
                onEvent = viewModel::onServerEvent,
            )
        }

        PlatformBackHandler(enabled = canGoBack) { viewModel.back() }

        Box(modifier = Modifier.fillMaxSize().background(CtosColors.Canvas)) {
            when (destination.screen) {
                Screen.Loading -> LoadingScreen()
                Screen.Login -> LoginScreen(
                    state = loginState,
                    onLogin = viewModel::login,
                    onEdit = viewModel::clearLoginError,
                    onForgotPassword = viewModel::openForgotPassword,
                    onCreateAccount = viewModel::openRegister,
                )
                Screen.Register -> RegisterContent(
                    state = registerState,
                    onRegister = viewModel::register,
                    onEdit = viewModel::clearRegisterError,
                    onBackToLogin = { viewModel.back() },
                )
                Screen.VerifyEmail -> VerifyEmailContent(
                    email = pendingVerificationEmail,
                    state = verifyEmailState,
                    onVerify = viewModel::verifyEmail,
                    onResend = viewModel::resendVerificationCode,
                    onEdit = viewModel::clearVerifyEmailError,
                    onBack = { viewModel.back() },
                )
                Screen.ForgotPassword -> ForgotPasswordContent(
                    state = forgotPasswordState,
                    emailSent = forgotPasswordEmailSent,
                    onRequestCode = viewModel::requestPasswordResetCode,
                    onResetPassword = viewModel::resetPassword,
                    onEdit = viewModel::clearForgotPasswordError,
                    onBackToLogin = { viewModel.back() },
                )
                else -> {
                    val organization = organizations.firstOrNull { it.organization.slug == destination.organizationSlug }
                    val department = viewModel.departmentBySlug(destination.departmentSlug)
                    val user = session

                    AppShell(
                        current = destination.screen,
                        title = when (destination.screen) {
                            Screen.OrganizationDetail -> organization?.organization?.name ?: "Organization"
                            Screen.DepartmentDetail -> department?.name ?: "Department"
                            Screen.DepartmentMembers -> "Members"
                            Screen.DepartmentAccessCode -> "Access code"
                            Screen.DepartmentInvitations -> "Invitations"
                            Screen.DepartmentLeave -> "Leave department"
                            else -> destination.screen.title
                        },
                        userName = user?.user_name.orEmpty(),
                        userEmail = user?.user_email.orEmpty(),
                        canGoBack = canGoBack,
                        onBack = { viewModel.back() },
                        onNavigate = viewModel::goTo,
                        onSearch = viewModel::openSearchDialog,
                        onLogout = viewModel::logout,
                    ) {
                        when (destination.screen) {
                            Screen.Dashboard -> DashboardContent(
                                firstName = user?.user_name.orEmpty().substringBefore(' '),
                                state = departmentsState,
                                organizations = organizations,
                                onCreateOrganization = viewModel::openCreateOrgDialog,
                                onJoinDepartment = viewModel::openJoinDialog,
                                onOpenOrganization = { viewModel.openOrganization(it.organization.slug) },
                                onRetry = { viewModel.loadDepartments() },
                            )
                            Screen.OrganizationDetail -> OrganizationContent(
                                organization = organization,
                                onOpenDepartment = viewModel::openDepartment,
                                onOpenWebsite = { openUrl(websiteUrl("/organizations/${destination.organizationSlug}")) },
                            )
                            Screen.Departments -> DepartmentsContent(
                                state = departmentsState,
                                onOpenDepartment = viewModel::openDepartment,
                                onRetry = { viewModel.loadDepartments() },
                            )
                            Screen.DepartmentDetail -> DepartmentContent(
                                department = department,
                                details = details,
                                sync = departmentSync,
                                explorerPath = explorerPath,
                                nowMillis = remember(departmentSync) { Clock.System.now().toEpochMilliseconds() },
                                addingFolder = addingFolder,
                                canOpenFolder = platformName() != "android",
                                onOpenExplorerFolder = viewModel::openExplorerFolder,
                                onAddFolder = folderPicker::open,
                                onOpenFolder = { departmentSync?.folder?.let { openFolder(it.path) } },
                                onOpenFile = { path -> departmentSync?.folder?.let { if (!openFile("${it.path}/$path")) ToastManager.error("No app on this device can open that file.") } },
                                onSyncNow = viewModel::syncNow,
                                onRetryDetails = viewModel::reloadDetails,
                                onOpenMembers = viewModel::openDepartmentMembers,
                                onOpenAccessCode = viewModel::openDepartmentAccessCode,
                                onOpenInvitations = viewModel::openDepartmentInvitations,
                                onOpenLeave = viewModel::openDepartmentLeave,
                            )
                            Screen.DepartmentMembers -> DepartmentMembersContent(
                                department = department,
                                details = details,
                                onRetry = viewModel::reloadDetails,
                            )
                            Screen.DepartmentAccessCode -> DepartmentAccessCodeContent(
                                department = department,
                                details = details,
                                onCopy = { text ->
                                    copyToClipboard(text)
                                    ToastManager.success("Copied to the clipboard.")
                                },
                                onGenerate = viewModel::openGenerateCodeDialog,
                            )
                            Screen.DepartmentInvitations -> DepartmentInvitationsContent(
                                department = department,
                                details = details,
                                onWithdraw = viewModel::withdrawInvitation,
                                onInvite = viewModel::openShareDialog,
                            )
                            Screen.DepartmentLeave -> DepartmentLeaveContent(
                                department = department,
                                leaveState = leaveState,
                                onConfirm = viewModel::leaveDepartment,
                                onCancel = { viewModel.back() },
                            )
                            Screen.Settings -> SettingsContent(
                                userName = user?.user_name.orEmpty(),
                                userEmail = user?.user_email.orEmpty(),
                                deviceName = deviceName(),
                                serverUrl = BuildKonfig.APP_URL,
                                syncRoot = syncRootDirectory(),
                                version = BuildKonfig.APP_VERSION,
                                canOpenFolder = platformName() != "android",
                                onOpenFolder = { openFolder(syncRootDirectory()) },
                                onChangePassword = viewModel::openForgotPassword,
                                onLogout = viewModel::logout,
                            )
                            else -> Unit
                        }
                    }
                }
            }

            if (joinDialogOpen) {
                JoinDepartmentDialog(
                    state = joinState,
                    onSubmit = viewModel::join,
                    onEdit = viewModel::clearJoinError,
                    onDismiss = viewModel::closeJoinDialog,
                )
            }

            if (shareDialogOpen) {
                ShareDepartmentDialog(
                    departmentName = viewModel.departmentBySlug(destination.departmentSlug)?.name ?: "this department",
                    state = shareState,
                    onSubmit = viewModel::shareDepartment,
                    onEdit = viewModel::clearShareError,
                    onDismiss = viewModel::closeShareDialog,
                )
            }

            if (generateCodeDialogOpen) {
                GenerateAccessCodeDialog(
                    state = generateCodeState,
                    newCode = generatedCode,
                    onGenerate = viewModel::regenerateAccessCode,
                    onCopy = { code ->
                        copyToClipboard(code)
                        ToastManager.success("Copied to the clipboard.")
                    },
                    onDismiss = viewModel::closeGenerateCodeDialog,
                )
            }

            if (searchDialogOpen) {
                SearchDialog(
                    query = globalSearchQuery,
                    results = globalSearchResults,
                    onQueryChange = viewModel::setGlobalSearchQuery,
                    onSelect = viewModel::openSearchResult,
                    onDismiss = viewModel::closeSearchDialog,
                )
            }

            if (createOrgDialogOpen) {
                CreateOrganizationDialog(
                    state = createOrgState,
                    onSubmit = viewModel::createOrganization,
                    onEdit = viewModel::clearCreateOrgError,
                    onDismiss = viewModel::closeCreateOrgDialog,
                )
            }

            ToastHost(modifier = Modifier.align(Alignment.BottomCenter))
        }
    }
}
