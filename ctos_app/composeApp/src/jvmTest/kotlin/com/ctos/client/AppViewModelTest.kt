package com.ctos.client

import com.ctos.client.data.AppRepository
import com.ctos.client.data.AppViewModel
import com.ctos.client.data.FormState
import com.ctos.client.data.Screen
import com.ctos.client.data.createDatabase
import com.ctos.client.network.Department
import com.ctos.client.network.OrganizationRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    @BeforeTest
    fun setUp() {
        TestEnvironment.ensure()
        Dispatchers.setMain(UnconfinedTestDispatcher())
        AppRepository.clearSession()
        while (ToastManager.toasts.tryReceive().isSuccess) Unit
    }

    @AfterTest
    fun tearDown() {
        AppRepository.clearSession()
        Dispatchers.resetMain()
    }

    private fun signedIn() {
        createDatabase().appDatabaseQueries.saveSession("7|abc", 1, "Alice Wanjiku", "alice@example.com")
    }

    private fun started(): AppViewModel = AppViewModel().also { viewModel ->
        runBlocking { withTimeout(5_000) { viewModel.destination.first { it.screen != Screen.Loading } } }
    }

    private val engineering = Department(1, "Engineering", "engineering", OrganizationRef(1, "Acme", "acme"), "admin")

    @Test
    fun withoutASessionTheAppOpensOnTheLoginScreen() {
        assertEquals(Screen.Login, started().destination.value.screen)
    }

    @Test
    fun withASavedSessionTheAppOpensOnTheDashboard() {
        signedIn()

        val viewModel = started()

        assertEquals(Screen.Dashboard, viewModel.destination.value.screen)
        assertEquals("Alice Wanjiku", viewModel.session.value?.user_name)
    }

    @Test
    fun openingOrganizationsAndDepartmentsCanBeGoneBackFromStepByStep() {
        signedIn()
        val viewModel = started()
        assertFalse(viewModel.canGoBack.value)

        viewModel.openOrganization("acme")
        assertEquals(Screen.OrganizationDetail, viewModel.destination.value.screen)
        assertEquals("acme", viewModel.destination.value.organizationSlug)
        assertTrue(viewModel.canGoBack.value)

        viewModel.openDepartment(engineering)
        assertEquals(Screen.DepartmentDetail, viewModel.destination.value.screen)
        assertEquals("engineering", viewModel.destination.value.departmentSlug)

        assertTrue(viewModel.back())
        assertEquals(Screen.OrganizationDetail, viewModel.destination.value.screen)
        assertTrue(viewModel.back())
        assertEquals(Screen.Dashboard, viewModel.destination.value.screen)
        assertFalse(viewModel.back(), "there is nowhere further back")
        assertFalse(viewModel.canGoBack.value)
    }

    @Test
    fun goingToATopLevelScreenForgetsTheWayBack() {
        signedIn()
        val viewModel = started()
        viewModel.openOrganization("acme")

        viewModel.goTo(Screen.Settings)

        assertFalse(viewModel.canGoBack.value)
        assertFalse(viewModel.back())
    }

    @Test
    fun theSearchIconOpensTheSearchPopupWithAClearQuery() {
        signedIn()
        val viewModel = started()
        viewModel.setGlobalSearchQuery("leftover")

        viewModel.openSearchDialog()

        assertTrue(viewModel.searchDialogOpen.value)
        assertEquals("", viewModel.globalSearchQuery.value)
    }

    @Test
    fun theSearchPopupTextIsKeptUntilClosed() {
        signedIn()
        val viewModel = started()
        viewModel.openSearchDialog()

        viewModel.setGlobalSearchQuery("design")

        assertEquals("design", viewModel.globalSearchQuery.value)
        viewModel.closeSearchDialog()
        assertFalse(viewModel.searchDialogOpen.value)
    }

    @Test
    fun loggingInNeedsAnEmailAndAPassword() {
        val viewModel = started()

        viewModel.login("", "secret")
        assertIs<FormState.Failed>(viewModel.loginState.value)

        viewModel.login("alice@example.com", "")
        assertIs<FormState.Failed>(viewModel.loginState.value)

        viewModel.clearLoginError()
        assertEquals(FormState.Idle, viewModel.loginState.value)
    }

    @Test
    fun joiningNeedsADepartmentIdAndACode() {
        signedIn()
        val viewModel = started()
        viewModel.openJoinDialog()
        assertTrue(viewModel.joinDialogOpen.value)

        viewModel.join("engineering", " ")
        assertIs<FormState.Failed>(viewModel.joinState.value)

        viewModel.closeJoinDialog()
        assertFalse(viewModel.joinDialogOpen.value)
        assertEquals(FormState.Idle, viewModel.joinState.value)
    }

    @Test
    fun whenTheServerRefusesTheTokenTheUserIsSentToTheLoginScreen() {
        signedIn()
        val viewModel = started()
        viewModel.openOrganization("acme")

        viewModel.sessionExpired()

        assertEquals(Screen.Login, viewModel.destination.value.screen)
        assertNull(viewModel.session.value)
        assertNull(AppRepository.getCurrentSession())
        assertEquals("You were signed out. Please log in again.", ToastManager.toasts.tryReceive().getOrNull()?.message)
    }

    @Test
    fun aRefusedTokenWhileSignedOutDoesNothing() {
        val viewModel = started()

        viewModel.sessionExpired()

        assertTrue(ToastManager.toasts.tryReceive().isFailure, "there was nobody to sign out")
    }

    @Test
    fun loggingOutEndsTheSessionEvenWhenTheServerCannotBeReached() {
        signedIn()
        val viewModel = started()

        viewModel.logout()
        runBlocking { withTimeout(10_000) { viewModel.session.first { it == null } } }

        assertEquals(Screen.Login, viewModel.destination.value.screen)
        assertNull(AppRepository.getCurrentSession())
    }
}
