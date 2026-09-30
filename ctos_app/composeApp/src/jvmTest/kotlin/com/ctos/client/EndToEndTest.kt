package com.ctos.client

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.ctos.client.data.AppRepository
import com.ctos.client.data.AppViewModel
import com.ctos.client.data.ChangeKind
import com.ctos.client.data.DepartmentsState
import com.ctos.client.data.Screen
import com.ctos.client.screens.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.jetbrains.skia.EncodedImageFormat
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Walks through the whole app the way a person would, with the real [App], the real view model
 * and the real API (ctos_api on http://localhost:9090, which is what a default build talks to),
 * and saves a screenshot at each step in composeApp/build/screenshots/e2e-*.png.
 * Skipped unless CTOS_E2E_EMAIL is set:
 *
 *   CTOS_E2E_EMAIL, CTOS_E2E_PASSWORD   a verified user
 *   CTOS_E2E_DEPARTMENT, CTOS_E2E_CODE  a department that user has not joined yet, and its access code
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalCoroutinesApi::class)
class EndToEndTest {
    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        AppRepository.clearSession()
    }

    @Test
    fun logsInBrowsesJoinsWatchesAFolderAndLogsOut() {
        val email = System.getenv("CTOS_E2E_EMAIL")
        assumeTrue("CTOS_E2E_EMAIL is not set", email != null)
        val password = System.getenv("CTOS_E2E_PASSWORD")
        val department = System.getenv("CTOS_E2E_DEPARTMENT")
        val code = System.getenv("CTOS_E2E_CODE")

        TestEnvironment.ensure()
        Dispatchers.setMain(UnconfinedTestDispatcher())
        AppRepository.clearSession()

        val viewModel = AppViewModel()
        val scene = ImageComposeScene(1200, 820, Density(1f)) { App(viewModel) }
        var clock = 0L

        fun shot(name: String) {
            repeat(6) { scene.render(++clock * 50_000_000L) }
            val bytes = scene.render(++clock * 50_000_000L).encodeToData(EncodedImageFormat.PNG)!!.bytes
            File("build/screenshots/e2e-$name.png").apply { parentFile.mkdirs() }.writeBytes(bytes)
        }

        try {
            // 1. no session yet: the login screen
            waitUntil(description = "the first screen") { viewModel.destination.value.screen == Screen.Login }
            shot("1-login")

            // 2. a wrong password is refused with the server's own words
            viewModel.login(email, "definitely-wrong")
            waitUntil(description = "the refusal") { viewModel.loginState.value.toString().contains("Invalid credentials") }
            shot("2-login-refused")

            // 3. the right password: the dashboard, with the organizations behind the user's departments
            viewModel.login(email, password)
            waitUntil(description = "the dashboard with data") {
                viewModel.destination.value.screen == Screen.Dashboard && viewModel.departmentsState.value is DepartmentsState.Loaded
            }
            assertNotNull(AppRepository.getCurrentSession(), "the token is kept, so the app opens signed in next time")
            assertTrue(viewModel.departments.none { it.slug == department }, "not joined yet")
            shot("3-dashboard")

            // 4. an organization, then its departments
            val organization = viewModel.departments.first().organization
            viewModel.openOrganization(organization.slug)
            shot("4-organization")

            // 5. the search popup, across organizations, departments, files and folders at once
            viewModel.openSearchDialog()
            viewModel.setGlobalSearchQuery("des")
            shot("5-search")
            viewModel.closeSearchDialog()

            // 6. joining a department: a wrong code first, then the right one
            viewModel.openJoinDialog()
            viewModel.join("WRONG-CODE")
            waitUntil(description = "the join refusal") { viewModel.joinState.value.toString().contains("incorrect") }
            viewModel.join(code)
            waitUntil(description = "the department to open") {
                viewModel.destination.value.screen == Screen.DepartmentDetail && viewModel.destination.value.departmentSlug == department
            }
            assertTrue(viewModel.departments.any { it.slug == department })
            assertEquals("member", viewModel.departmentBySlug(department)?.role)
            shot("6-joined-department")

            // 7. the department's folder is watched: a file put in it shows up by itself
            waitUntil(description = "the folder to be watched") { viewModel.localFolder.value?.watching == true }
            val folder = File(assertNotNull(viewModel.localFolder.value).path)
            Thread.sleep(400)
            File(folder, "Budget 2026.xlsx").writeText("numbers ".repeat(500))
            File(folder, "notes").mkdirs()
            Thread.sleep(300)
            File(folder, "notes/meeting.md").writeText("# Meeting")
            waitUntil(description = "both files to be listed") { viewModel.localFolder.value?.files?.size == 2 }
            assertTrue(viewModel.localFolder.value!!.recentChanges.any { it.path == "Budget 2026.xlsx" && it.kind == ChangeKind.Created })
            shot("7-department-with-files")

            // 8. settings, then logging out ends the session
            viewModel.goTo(Screen.Settings)
            shot("8-settings")
            viewModel.logout()
            waitUntil(description = "the logout") { viewModel.session.value == null }
            assertNull(AppRepository.getCurrentSession())
            assertEquals(Screen.Login, viewModel.destination.value.screen)
            shot("9-logged-out")
        } finally {
            scene.close()
        }
    }
}
