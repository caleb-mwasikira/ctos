package com.ctos.client

import com.ctos.client.data.AppRepository
import com.ctos.client.data.ChangeKind
import com.ctos.client.data.FolderChange
import com.ctos.client.data.FolderWatcher
import com.ctos.client.data.LocalFile
import com.ctos.client.data.LocalFolderState
import com.ctos.client.data.LocalFolderTracker
import com.ctos.client.data.scanFolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LocalFolderTrackerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val trackers = mutableListOf<LocalFolderTracker>()

    private class Updates {
        val states = CopyOnWriteArrayList<LocalFolderState>()
        val latest: LocalFolderState? get() = states.lastOrNull()
    }

    @BeforeTest
    fun setUp() = TestEnvironment.ensure()

    @AfterTest
    fun tearDown() {
        trackers.forEach { it.stop() }
        scope.cancel()
    }

    private fun track(
        folder: File,
        slug: String = "dept-${UUID.randomUUID()}",
        debounceMillis: Long = 100,
        rescanMillis: Long = 60_000,
        watch: (String, (FolderChange) -> Unit) -> FolderWatcher = ::watchFolderForTest,
    ): Pair<LocalFolderTracker, Updates> {
        val updates = Updates()
        val tracker = LocalFolderTracker(scope, slug, folder.path, { updates.states += it }, debounceMillis, rescanMillis, watch)
        trackers += tracker
        tracker.start()
        return tracker to updates
    }

    @Test
    fun itStartsWithWhatIsAlreadyInTheFolder() {
        val folder = TestEnvironment.newFolder().apply {
            File(this, "a.txt").writeText("one")
            File(this, "sub").mkdirs()
            File(this, "sub/b.txt").writeText("two")
        }

        val (_, updates) = track(folder)

        waitUntil(description = "the first look and the watcher") { updates.latest?.watching == true }
        assertEquals(listOf("a.txt", "sub/b.txt"), updates.latest!!.files.map { it.path })
        // The folder was never looked at before, so what is in it is not a change.
        assertTrue(updates.latest!!.recentChanges.isEmpty(), updates.latest!!.recentChanges.toString())
    }

    @Test
    fun aNewFileShowsUpWithinAMoment() {
        val folder = TestEnvironment.newFolder()
        val (_, updates) = track(folder)
        waitUntil(description = "the watcher") { updates.latest?.watching == true }
        Thread.sleep(300)

        File(folder, "fresh.txt").writeText("hello")

        waitUntil(description = "the new file") { updates.latest?.files?.any { it.path == "fresh.txt" } == true }
        val change = updates.latest!!.recentChanges.single { it.path == "fresh.txt" }
        assertEquals(ChangeKind.Created, change.kind)
        assertFalse(change.whileClosed)
    }

    @Test
    fun aChangedAndADeletedFileAreShownToo() {
        val folder = TestEnvironment.newFolder()
        val edited = File(folder, "edit.txt").apply { writeText("v1") }
        val doomed = File(folder, "doomed.txt").apply { writeText("x") }
        val (_, updates) = track(folder)
        waitUntil(description = "the watcher") { updates.latest?.watching == true }
        Thread.sleep(300)

        edited.writeText("version two, longer")
        doomed.delete()

        waitUntil(description = "both changes") {
            updates.latest?.recentChanges?.let { changes ->
                changes.any { it.path == "edit.txt" && it.kind == ChangeKind.Modified } &&
                    changes.any { it.path == "doomed.txt" && it.kind == ChangeKind.Deleted }
            } == true
        }
        assertEquals(listOf("edit.txt"), updates.latest!!.files.map { it.path })
    }

    @Test
    fun aBurstOfNotificationsIsOneLook() {
        val folder = TestEnvironment.newFolder()
        var notify: (FolderChange) -> Unit = {}
        val fake = object : FolderWatcher {
            override fun close() {}
        }
        val (_, updates) = track(folder, debounceMillis = 300, watch = { _, onChange -> notify = onChange; fake })
        waitUntil(description = "the first look and the watcher") { updates.latest?.watching == true }
        val before = updates.states.size

        repeat(30) { notify(FolderChange("file-$it.txt", ChangeKind.Modified)) }
        Thread.sleep(1_200)

        assertEquals(before + 1, updates.states.size, "thirty notifications must lead to one look")
    }

    @Test
    fun itFindsWhatChangedWhileTheAppWasClosed() {
        val folder = TestEnvironment.newFolder().apply {
            File(this, "edited.txt").writeText("now much longer than before")
            File(this, "brand-new.txt").writeText("hi")
            File(this, "same.txt").writeText("same")
        }
        val slug = "dept-${UUID.randomUUID()}"
        val same = scanFolder(folder.path).single { it.path == "same.txt" }
        // What the folder held when the app last looked at it.
        AppRepository.saveFiles(slug, listOf(same, LocalFile("edited.txt", 3, 1), LocalFile("deleted.txt", 9, 1)))

        val (_, updates) = track(folder, slug)

        waitUntil(description = "the first look") { updates.latest != null }
        val changes = updates.latest!!.recentChanges.associate { it.path to it }
        assertEquals(setOf("brand-new.txt", "edited.txt", "deleted.txt"), changes.keys)
        assertEquals(ChangeKind.Created, changes.getValue("brand-new.txt").kind)
        assertEquals(ChangeKind.Modified, changes.getValue("edited.txt").kind)
        assertEquals(ChangeKind.Deleted, changes.getValue("deleted.txt").kind)
        assertTrue(changes.values.all { it.whileClosed })
    }

    @Test
    fun aLookIsRememberedSoTheNextStartHasNothingToReport() {
        val folder = TestEnvironment.newFolder().apply { File(this, "a.txt").writeText("one") }
        val slug = "dept-${UUID.randomUUID()}"

        val (first, firstUpdates) = track(folder, slug)
        waitUntil(description = "the first look") { firstUpdates.latest != null }
        first.stop()

        val (_, secondUpdates) = track(folder, slug)
        waitUntil(description = "the second look") { secondUpdates.latest != null }

        assertEquals(listOf("a.txt"), secondUpdates.latest!!.files.map { it.path })
        assertTrue(secondUpdates.latest!!.recentChanges.isEmpty(), secondUpdates.latest!!.recentChanges.toString())
    }

    @Test
    fun temporaryFilesAreLeftOut() {
        val folder = TestEnvironment.newFolder().apply {
            File(this, "real.txt").writeText("x")
            File(this, ".hidden").writeText("x")
            File(this, "draft.tmp").writeText("x")
        }

        val (_, updates) = track(folder)

        waitUntil(description = "the first look") { updates.latest != null }
        assertEquals(listOf("real.txt"), updates.latest!!.files.map { it.path })
    }

    @Test
    fun itFallsBackToLookingNowAndThenWhenTheWatcherCannotStart() {
        val folder = TestEnvironment.newFolder()
        val (_, updates) = track(folder, rescanMillis = 300, watch = { _, _ -> error("too many open files") })

        waitUntil(description = "the failure to be reported") { updates.latest?.error != null }
        assertFalse(updates.latest!!.watching)
        assertNotNull(updates.latest!!.error)

        File(folder, "found-anyway.txt").writeText("x")

        waitUntil(description = "the periodic look to find the file") { updates.latest?.files?.any { it.path == "found-anyway.txt" } == true }
    }

    @Test
    fun stoppingClosesTheWatcher() {
        val folder = TestEnvironment.newFolder()
        var closed = false
        val (tracker, updates) = track(folder, watch = { _, _ -> object : FolderWatcher { override fun close() { closed = true } } })
        waitUntil(description = "the watcher") { updates.latest?.watching == true }

        tracker.stop()

        assertTrue(closed)
    }
}

private fun watchFolderForTest(root: String, onChange: (FolderChange) -> Unit): FolderWatcher = com.ctos.client.data.watchFolder(root, onChange)
