package com.ctos.client

import com.ctos.client.data.SyncEngine
import com.ctos.client.data.SyncReport
import com.ctos.client.data.localFiles
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The sync engine against a stand-in for the API (see FakeServer), with real folders on disk. */
class SyncEngineTest {
    /** One computer: a folder, and the memory of the last sync. */
    private inner class Device(val name: String) {
        val folder: File = TestEnvironment.newFolder(name)
        private val store = MemorySyncStore()
        private var clock = 1_000_000L

        fun engine(server: FakeServer) = SyncEngine(
            api = server.api(),
            departmentSlug = "engineering",
            files = localFiles(folder.path),
            store = store,
            deviceLabel = name,
            now = { 42L },
            today = { "2026-09-27" },
        )

        fun sync(server: FakeServer, full: Boolean = false): SyncReport = runBlocking { engine(server).sync(full) }

        /** Keeps syncing until a run has nothing left to do, like the app does. */
        fun settle(server: FakeServer): SyncReport {
            var report = sync(server)
            var runs = 1
            while (report.again && runs < 4) {
                report = sync(server)
                runs++
            }
            return report
        }

        fun write(path: String, content: String) {
            val file = File(folder, path)
            file.parentFile.mkdirs()
            file.writeText(content)
            clock += 10_000 // a real editor would give it a later time; the tests must not depend on the speed of the disk
            file.setLastModified(clock)
        }

        fun read(path: String): String? = File(folder, path).takeIf { it.isFile }?.readText()

        fun files(): Map<String, String> = folder.walkTopDown().filter { it.isFile && !it.name.startsWith(".") }
            .associate { it.relativeTo(folder).invariantSeparatorsPath to it.readText() }
    }

    private fun assertQuiet(report: SyncReport) {
        assertNull(report.error)
        assertEquals(emptyList(), report.problems)
        assertFalse(report.changedSomething, "expected nothing to happen, but $report")
    }

    @Test
    fun aNewFileGoesToTheServerAndThenToTheOtherDevice() {
        val server = FakeServer()
        val alice = Device("alice")
        val bob = Device("bob")
        alice.write("notes.txt", "hello")

        val up = alice.settle(server)
        val down = bob.settle(server)

        assertEquals(1, up.uploaded)
        assertEquals(mapOf("notes.txt" to "hello"), server.files())
        assertEquals(1, down.downloaded)
        assertEquals("hello", bob.read("notes.txt"))
    }

    @Test
    fun aRunWithNothingToDoDoesNothing() {
        val server = FakeServer()
        val alice = Device("alice")
        alice.write("a.txt", "one")
        alice.write("docs/b.txt", "two")
        alice.settle(server)

        assertQuiet(alice.sync(server))
        assertQuiet(alice.sync(server, full = true))
        assertEquals(2, server.files().size)
    }

    @Test
    fun theDownloadedFileKeepsItsModificationTimeAndIsNotUploadedAgain() {
        val server = FakeServer()
        server.put("report.txt", "from the server", modifiedAt = 1_700_000_000_000)
        val bob = Device("bob")

        bob.settle(server)

        assertEquals(1_700_000_000_000, File(bob.folder, "report.txt").lastModified())
        assertQuiet(bob.sync(server))
        assertEquals(1L, server.version("report.txt"))
    }

    @Test
    fun anEditTravelsInBothDirections() {
        val server = FakeServer()
        val alice = Device("alice")
        val bob = Device("bob")
        alice.write("plan.md", "v1")
        alice.settle(server)
        bob.settle(server)

        bob.write("plan.md", "v2 by bob")
        assertEquals(1, bob.settle(server).uploaded)
        assertEquals(1, alice.settle(server).downloaded)
        assertEquals("v2 by bob", alice.read("plan.md"))

        alice.write("plan.md", "v3 by alice")
        alice.settle(server)
        bob.settle(server)

        assertEquals("v3 by alice", bob.read("plan.md"))
        assertEquals(3L, server.version("plan.md"))
        assertQuiet(alice.sync(server))
        assertQuiet(bob.sync(server))
    }

    @Test
    fun aDeletionTravelsToTheServerAndToTheOtherDevice() {
        val server = FakeServer()
        val alice = Device("alice")
        val bob = Device("bob")
        alice.write("old.txt", "x")
        alice.write("keep.txt", "y")
        alice.settle(server)
        bob.settle(server)

        File(alice.folder, "old.txt").delete()
        val up = alice.settle(server)
        val down = bob.settle(server)

        assertEquals(1, up.removedOnServer)
        assertEquals(setOf("keep.txt"), server.files().keys)
        assertEquals(1, down.removedHere)
        assertEquals(mapOf("keep.txt" to "y"), bob.files())
    }

    @Test
    fun aFileChangedOnBothSidesKeepsBothVersions() {
        val server = FakeServer()
        val alice = Device("alice")
        val bob = Device("bob")
        alice.write("budget.txt", "start")
        alice.settle(server)
        bob.settle(server)

        // both edit before either syncs
        alice.write("budget.txt", "alice's numbers")
        bob.write("budget.txt", "bob's numbers")
        alice.settle(server)
        val report = bob.settle(server)

        assertEquals(1, report.conflicts)
        assertEquals("alice's numbers", bob.read("budget.txt"), "the server's version takes the file's place")
        val copy = bob.files().keys.single { it != "budget.txt" }
        assertEquals("budget (conflict copy bob 2026-09-27).txt", copy)
        assertEquals("bob's numbers", bob.read(copy))

        // and the copy reaches everyone, so nothing is lost anywhere
        alice.settle(server)
        assertEquals("bob's numbers", alice.read(copy))
        assertEquals(setOf("budget.txt", copy), server.files().keys)
        assertQuiet(alice.sync(server))
        assertQuiet(bob.sync(server))
    }

    @Test
    fun anUploadBasedOnAnOldVersionIsRefusedAndBecomesAConflictCopy() {
        val server = FakeServer()
        val bob = Device("bob")
        server.put("doc.txt", "v1")
        bob.settle(server)

        // between bob's sync and his upload, someone else changes the file on the server
        bob.write("doc.txt", "bob's edit")
        server.put("doc.txt", "someone else's edit")
        val report = bob.settle(server)

        assertEquals(1, report.conflicts)
        assertEquals("someone else's edit", bob.read("doc.txt"))
        assertEquals("bob's edit", server.files().entries.single { it.key != "doc.txt" }.value)
        assertEquals("someone else's edit", server.files()["doc.txt"])
    }

    @Test
    fun twoPeopleCreatingTheSameFileBothKeepTheirs() {
        val server = FakeServer()
        val alice = Device("alice")
        val bob = Device("bob")
        alice.write("new.txt", "alice")
        bob.write("new.txt", "bob")

        alice.settle(server)
        val report = bob.settle(server)

        assertEquals(1, report.conflicts)
        assertEquals("alice", bob.read("new.txt"))
        assertEquals(setOf("alice", "bob"), server.files().values.toSet())
    }

    @Test
    fun theSameFileOnBothSidesIsNotAConflict() {
        val server = FakeServer()
        server.put("same.txt", "identical")
        val bob = Device("bob")
        bob.write("same.txt", "identical")

        val report = bob.settle(server)

        assertEquals(0, report.conflicts)
        assertEquals(0, report.downloaded)
        assertEquals(setOf("same.txt"), bob.files().keys)
        assertQuiet(bob.sync(server))
    }

    @Test
    fun aFileDeletedOnTheServerButEditedHereIsKeptAndUploadedAgain() {
        val server = FakeServer()
        val alice = Device("alice")
        val bob = Device("bob")
        alice.write("draft.txt", "v1")
        alice.settle(server)
        bob.settle(server)

        server.remove("draft.txt")
        bob.write("draft.txt", "bob kept working")
        bob.settle(server)

        assertEquals("bob kept working", bob.read("draft.txt"))
        assertEquals(mapOf("draft.txt" to "bob kept working"), server.files())
    }

    @Test
    fun aFileEditedOnTheServerButDeletedHereComesBack() {
        val server = FakeServer()
        val alice = Device("alice")
        val bob = Device("bob")
        alice.write("a.txt", "v1")
        alice.settle(server)
        bob.settle(server)

        File(bob.folder, "a.txt").delete()
        server.put("a.txt", "v2 from someone else")
        bob.settle(server)

        assertEquals("v2 from someone else", bob.read("a.txt"))
        assertEquals(setOf("a.txt"), server.files().keys)
    }

    @Test
    fun aFileThatWasTouchedButNotChangedIsNotUploaded() {
        val server = FakeServer()
        val alice = Device("alice")
        alice.write("a.txt", "same")
        alice.settle(server)

        File(alice.folder, "a.txt").setLastModified(1_234_567)
        val report = alice.sync(server)

        assertEquals(0, report.uploaded)
        assertEquals(1L, server.version("a.txt"))
        assertQuiet(alice.sync(server))
    }

    @Test
    fun foldersSyncEvenWhenEmpty() {
        val server = FakeServer()
        val alice = Device("alice")
        val bob = Device("bob")
        File(alice.folder, "Photos/2026/trip").mkdirs()
        alice.write("Photos/2026/cover.txt", "c")

        alice.settle(server)
        bob.settle(server)

        assertEquals(listOf("Photos", "Photos/2026", "Photos/2026/trip"), server.folders())
        assertTrue(File(bob.folder, "Photos/2026/trip").isDirectory, "an empty folder arrives too")
        assertEquals("c", bob.read("Photos/2026/cover.txt"))
    }

    @Test
    fun deletingAFolderRemovesItEverywhere() {
        val server = FakeServer()
        val alice = Device("alice")
        val bob = Device("bob")
        alice.write("docs/a.txt", "a")
        alice.write("docs/deep/b.txt", "b")
        alice.write("other/c.txt", "c")
        alice.settle(server)
        bob.settle(server)

        File(alice.folder, "docs").deleteRecursively()
        alice.settle(server)
        bob.settle(server)

        assertEquals(setOf("other/c.txt"), server.files().keys)
        assertEquals(listOf("other"), server.folders())
        assertFalse(File(bob.folder, "docs").exists(), "the folder and the folders inside it are gone")
        assertEquals(mapOf("other/c.txt" to "c"), bob.files())
        assertQuiet(alice.sync(server))
        assertQuiet(bob.sync(server))
    }

    @Test
    fun aFolderKeepsFilesTheServerNeverHeardOf() {
        val server = FakeServer()
        val alice = Device("alice")
        val bob = Device("bob")
        alice.write("docs/a.txt", "a")
        alice.settle(server)
        bob.settle(server)

        server.remove("docs")
        bob.write("docs/mine.txt", "added here meanwhile")
        bob.settle(server)

        assertEquals("added here meanwhile", bob.read("docs/mine.txt"))
        assertFalse(File(bob.folder, "docs/a.txt").exists())
        assertEquals(mapOf("docs/mine.txt" to "added here meanwhile"), server.files())
    }

    @Test
    fun aFileDeletedAndCreatedAgainOnTheServerIsFetchedAgain() {
        val server = FakeServer()
        val alice = Device("alice")
        val bob = Device("bob")
        alice.write("a.txt", "first life")
        alice.settle(server)
        alice.write("a.txt", "second")
        alice.settle(server)
        bob.settle(server)

        server.remove("a.txt")
        server.put("a.txt", "reborn") // starts again at version 1
        bob.settle(server)

        assertEquals("reborn", bob.read("a.txt"))
        assertEquals(1L, server.version("a.txt"))
        assertQuiet(bob.sync(server))
    }

    @Test
    fun aDamagedDownloadIsRefusedAndTheOldFileStays() {
        val server = FakeServer()
        val bob = Device("bob")
        server.put("a.txt", "v1")
        bob.settle(server)

        server.put("a.txt", "v2")
        server.corruptDownloads = true
        val report = bob.settle(server)

        assertEquals("v1", bob.read("a.txt"))
        assertEquals(1, report.problems.size)
        assertTrue(report.problems.single().startsWith("a.txt:"), report.problems.toString())
        assertFalse(File(bob.folder, ".a.txt.part").exists(), "no half-written file is left behind")

        // the next run starts again, and this time it works
        server.corruptDownloads = false
        val retry = bob.settle(server)
        assertEquals("v2", bob.read("a.txt"))
        assertEquals(emptyList(), retry.problems)
    }

    @Test
    fun withoutAConnectionNothingIsLostAndTheNextRunCatchesUp() {
        val server = FakeServer()
        val alice = Device("alice")
        alice.write("a.txt", "one")
        alice.settle(server)

        server.offline = true
        alice.write("a.txt", "two, written offline")
        alice.write("b.txt", "new, written offline")
        val offline = alice.sync(server)
        assertNotNull(offline.error)
        assertEquals("one", server.files()["a.txt"])

        server.offline = false
        val online = alice.settle(server)

        assertNull(online.error)
        assertEquals(mapOf("a.txt" to "two, written offline", "b.txt" to "new, written offline"), server.files())
    }

    @Test
    fun aFolderThatWasRemovedIsRestoredInsteadOfDeletingEverythingOnTheServer() {
        val server = FakeServer()
        val alice = Device("alice")
        alice.write("a.txt", "one")
        alice.write("docs/b.txt", "two")
        alice.settle(server)

        alice.folder.deleteRecursively() // the whole department folder vanishes (an unplugged disk, a mistake)
        alice.settle(server)

        assertEquals(setOf("a.txt", "docs/b.txt"), server.files().keys, "the server keeps everything")
        assertEquals(mapOf("a.txt" to "one", "docs/b.txt" to "two"), alice.files(), "and the folder is filled again")
    }

    @Test
    fun namesTheServerWouldRefuseAreReportedAndTheRestIsSynced() {
        val server = FakeServer()
        val alice = Device("alice")
        alice.write("fine.txt", "ok")
        alice.write("bad\\name.txt", "backslash") // a legal file name on Linux, not accepted by the server

        val report = alice.settle(server)

        assertEquals(setOf("fine.txt"), server.files().keys)
        assertEquals(1, report.problems.size)
        assertTrue(report.problems.single().contains("bad\\name.txt"))
    }

    @Test
    fun temporaryAndHiddenFilesAreNotSynced() {
        val server = FakeServer()
        val alice = Device("alice")
        alice.write("real.txt", "1")
        alice.write(".hidden", "2")
        alice.write("~\$lock.docx", "3")
        alice.write("half.part", "4")
        alice.write("editor.swp", "5")
        alice.write(".git/config", "6")

        alice.settle(server)

        assertEquals(setOf("real.txt"), server.files().keys)
    }

    @Test
    fun aBigFileIsStreamedWithoutLosingAByte() {
        val server = FakeServer()
        val alice = Device("alice")
        val bob = Device("bob")
        val big = ByteArray(6 * 1024 * 1024 + 123) { (it * 31 % 251).toByte() }
        File(alice.folder, "big.bin").writeBytes(big)

        alice.settle(server)
        bob.settle(server)

        assertContentEquals(big, File(bob.folder, "big.bin").readBytes())
    }

    @Test
    fun anEmptyFileSyncs() {
        val server = FakeServer()
        val alice = Device("alice")
        val bob = Device("bob")
        alice.write("empty.txt", "")

        alice.settle(server)
        bob.settle(server)

        assertEquals(mapOf("empty.txt" to ""), server.files())
        assertEquals("", bob.read("empty.txt"))
    }

    @Test
    fun manyChangesArriveAcrossPages() {
        val server = FakeServer(pageLimit = 7)
        repeat(30) { server.put("f/file-$it.txt", "content $it") }
        val bob = Device("bob")

        val report = bob.settle(server)

        assertEquals(30, report.downloaded)
        assertEquals(30, bob.files().size)
        assertQuiet(bob.sync(server))
        // and it carries on from where it stopped, asking for nothing old
        server.requests.clear()
        server.put("f/late.txt", "late")
        bob.sync(server)
        assertTrue(server.requests.first().startsWith("GET engineering/changes") || server.requests.first().contains("changes?cursor=3"), server.requests.toString())
        assertEquals("late", bob.read("f/late.txt"))
    }

    @Test
    fun aRunAfterARestartFindsWhatHappenedWhileTheAppWasClosed() {
        val server = FakeServer()
        val alice = Device("alice")
        val bob = Device("bob")
        alice.write("a.txt", "one")
        alice.settle(server)
        bob.settle(server)

        // "closed": bob's engine is gone, but his folder and the sync memory remain
        alice.write("a.txt", "two")
        alice.settle(server)
        server.put("new.txt", "made meanwhile")
        bob.write("mine.txt", "written while closed")
        val report = bob.settle(server)

        assertEquals("two", bob.read("a.txt"))
        assertEquals("made meanwhile", bob.read("new.txt"))
        assertEquals("written while closed", server.files()["mine.txt"])
        assertEquals(2, report.downloaded)
        assertEquals(1, report.uploaded)
    }

    @Test
    fun aFileCannotEscapeTheFolderThroughItsPath() {
        val server = FakeServer()
        val bob = Device("bob")
        // a hostile or buggy server that names a path outside the department
        server.put("../escape.txt", "gotcha")

        val report = bob.settle(server)

        assertFalse(File(bob.folder.parentFile, "escape.txt").exists())
        assertTrue(report.problems.isNotEmpty(), "the paths are refused")
        assertNull(report.error)
    }
}
