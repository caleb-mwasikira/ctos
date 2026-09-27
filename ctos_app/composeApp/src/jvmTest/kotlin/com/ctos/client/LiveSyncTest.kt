package com.ctos.client

import com.ctos.client.data.AppRepository
import com.ctos.client.data.AppViewModel
import com.ctos.client.data.DepartmentsState
import com.ctos.client.data.DetailsState
import com.ctos.client.data.DiskFolder
import com.ctos.client.data.Screen
import com.ctos.client.data.SyncManager
import com.ctos.client.data.SyncStatus
import com.ctos.client.data.isIgnoredFile
import com.ctos.client.data.DepartmentEvents
import com.ctos.client.network.CtosApi
import com.ctos.client.network.LoginRequest
import com.ctos.client.network.configureCtosClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Two people sync one department through the real API (ctos_api) and its database, the way the apps do:
 * Alice uses the real view model (log in, open the department, "Add folder"), Bob is a second device with
 * his own folder. Skipped unless these are set (the API must be the one the app was built for; see
 * the -PapiUrl build option):
 *
 *   CTOS_SYNC_ALICE, CTOS_SYNC_BOB   email addresses of two verified users, both members of the department
 *   CTOS_SYNC_PASSWORD               their (shared) password
 *   CTOS_SYNC_DEPARTMENT             the department's ID (slug)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveSyncTest {
    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        AppRepository.clearSession()
    }

    /** Every file (path -> content) and folder below [root]. */
    private fun tree(root: File): Pair<Map<String, String>, Set<String>> {
        val files = root.walkTopDown().filter { it.isFile && !isIgnoredFile(it.name) }.associate { it.relativeTo(root).invariantSeparatorsPath to sha256(it.readBytes()) }
        val folders = root.walkTopDown().filter { it.isDirectory && it != root && !isIgnoredFile(it.name) }.map { it.relativeTo(root).invariantSeparatorsPath }.toSet()
        return files to folders
    }

    @Test
    fun aFolderAddedInTheAppReachesTheOtherUserAndChangesComeBack() {
        val aliceEmail = System.getenv("CTOS_SYNC_ALICE")
        assumeTrue("CTOS_SYNC_ALICE is not set", aliceEmail != null)
        val bobEmail = System.getenv("CTOS_SYNC_BOB")
        val password = System.getenv("CTOS_SYNC_PASSWORD")
        val slug = System.getenv("CTOS_SYNC_DEPARTMENT")

        TestEnvironment.ensure()
        Dispatchers.setMain(UnconfinedTestDispatcher())
        AppRepository.clearSession()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        try {
            // ── Alice: the real app logic ──────────────────────────────────
            val viewModel = AppViewModel()
            scope.launch {
                DepartmentEvents.run(
                    isSignedIn = { viewModel.session.value != null },
                    listen = { onOpen, onEvent -> CtosApi.default.listen(onOpen, onEvent) },
                    onConnected = viewModel::onRealtimeConnected,
                    onEvent = viewModel::onServerEvent,
                )
            }
            waitUntil(description = "the app to start") { viewModel.destination.value.screen == Screen.Login }
            viewModel.login(aliceEmail, password)
            waitUntil(description = "Alice's departments") { viewModel.departmentsState.value is DepartmentsState.Loaded }
            val department = assertNotNull(viewModel.departmentBySlug(slug))
            viewModel.openDepartment(department)
            waitUntil(description = "the department's members") { (viewModel.details.value as? DetailsState.Loaded)?.details?.members?.size == 2 }
            val details = (viewModel.details.value as DetailsState.Loaded).details
            assertTrue(details.canManage, "Alice is an admin of the department")
            assertNotNull(details.accessCode, "and sees its access code")
            val aliceFolder = File(department.let { com.ctos.client.data.departmentFolder(it) })

            // ── Bob: a second device with his own folder ───────────────────
            var bobToken: String? = null
            val bobApi = CtosApi(HttpClient(CIO) { configureCtosClient() }, com.ctos.client.BuildKonfig.APP_URL, { bobToken })
            bobToken = runBlocking { bobApi.login(LoginRequest(bobEmail, password, "Bob's laptop")).getOrThrow().token }
            val bobFolder = TestEnvironment.newFolder("bob")
            val bob = SyncManager(scope, { bobApi }, MemorySyncStore(), "bob-laptop", folderOf = { bobFolder.path })
            bob.setDepartments(runBlocking { bobApi.getDepartments().getOrThrow() })
            scope.launch { runCatching { bobApi.listen { event -> event.departmentId?.let(bob::nudge) } } }

            // ── 1. Alice adds a folder in the app ──────────────────────────
            val source = TestEnvironment.newFolder("source")
            val holiday = File(source, "Holiday").apply { mkdirs() }
            File(holiday, "Report.bin").writeBytes(ByteArray(3 * 1024 * 1024 + 17) { (it * 7 % 253).toByte() })
            File(holiday, "notes/deep").mkdirs()
            File(holiday, "notes/meeting.md").writeText("# Meeting\n- ship it")
            File(holiday, "notes/deep/a.txt").writeText("deep")
            File(holiday, "Zażółć gęślą jaźń.txt").writeText("unicode name")
            File(holiday, "empty-dir").mkdirs()
            File(holiday, ".DS_Store").writeText("junk")
            File(holiday, "~\$lock.docx").writeText("junk")
            val expected = tree(holiday)

            viewModel.addFolder(DiskFolder(holiday))

            waitUntil(30_000, "Bob to receive the folder") {
                val (files, folders) = tree(File(bobFolder, "Holiday").also { it.mkdirs() })
                files == expected.first && folders == expected.second
            }
            waitUntil(20_000, "Alice's sync to settle") {
                viewModel.departmentSync.value?.let { it.status.phase == SyncStatus.Phase.Synced && it.pending.isEmpty() } == true
            }
            assertEquals(expected, tree(File(aliceFolder, "Holiday")), "Alice still has all of it")
            assertEquals(emptyList(), viewModel.departmentSync.value!!.status.problems)

            // ── 2. Bob edits a file and adds one: Alice gets both ──────────
            File(bobFolder, "Holiday/notes/meeting.md").writeText("# Meeting\n- ship it\n- and celebrate")
            File(bobFolder, "Holiday/from-bob.txt").writeText("hello from Bob")
            waitUntil(30_000, "Alice to receive Bob's changes") {
                File(aliceFolder, "Holiday/notes/meeting.md").takeIf { it.isFile }?.readText()?.endsWith("and celebrate") == true &&
                    File(aliceFolder, "Holiday/from-bob.txt").takeIf { it.isFile }?.readText() == "hello from Bob"
            }

            // ── 3. Bob deletes a folder: it disappears for Alice ───────────
            File(bobFolder, "Holiday/notes").deleteRecursively()
            waitUntil(30_000, "Alice to lose the deleted folder") { !File(aliceFolder, "Holiday/notes").exists() }
            assertTrue(File(aliceFolder, "Holiday/Report.bin").isFile, "the rest is untouched")

            // ── 4. Alice deletes a file in her folder: Bob loses it too ────
            File(aliceFolder, "Holiday/from-bob.txt").delete()
            waitUntil(30_000, "Bob to lose the deleted file") { !File(bobFolder, "Holiday/from-bob.txt").exists() }

            // nothing is left half-done on either side
            waitUntil(20_000, "everything to settle") { tree(File(aliceFolder, "Holiday")) == tree(File(bobFolder, "Holiday")) }
            println("LIVE SYNC OK: ${tree(File(aliceFolder, "Holiday")).first.keys.sorted()}")
        } finally {
            scope.cancel()
        }
    }
}
