package com.ctos.client

import com.ctos.client.data.ChangeKind
import com.ctos.client.data.FolderChange
import com.ctos.client.data.FolderWatcher
import com.ctos.client.data.watchFolder
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** These run the real operating system watcher (inotify on Linux) on real folders. */
class FolderWatcherTest {
    private val watchers = mutableListOf<FolderWatcher>()

    private class Seen {
        val changes = CopyOnWriteArrayList<FolderChange>()

        fun has(path: String, kind: ChangeKind) = changes.any { it.path == path && it.kind == kind }
    }

    private fun watch(folder: File): Seen {
        val seen = Seen()
        watchers += watchFolder(folder.path) { seen.changes += it }
        Thread.sleep(300) // the watcher registers on its own thread
        return seen
    }

    @AfterTest
    fun stopWatching() {
        watchers.forEach { it.close() }
    }

    @Test
    fun aNewFileIsReported() {
        val folder = TestEnvironment.newFolder()
        val seen = watch(folder)

        File(folder, "report.txt").writeText("hello")

        waitUntil(description = "the new file to be reported") { seen.has("report.txt", ChangeKind.Created) }
    }

    @Test
    fun aChangedFileIsReported() {
        val folder = TestEnvironment.newFolder()
        val file = File(folder, "notes.txt").apply { writeText("one") }
        val seen = watch(folder)

        file.appendText(" two")

        waitUntil(description = "the change to be reported") { seen.has("notes.txt", ChangeKind.Modified) }
    }

    @Test
    fun aDeletedFileIsReported() {
        val folder = TestEnvironment.newFolder()
        val file = File(folder, "old.txt").apply { writeText("bye") }
        val seen = watch(folder)

        file.delete()

        waitUntil(description = "the deletion to be reported") { seen.has("old.txt", ChangeKind.Deleted) }
    }

    @Test
    fun aFileInAFolderThatAppearsLaterIsReportedToo() {
        val folder = TestEnvironment.newFolder()
        val seen = watch(folder)

        File(folder, "plans/2026").mkdirs()
        Thread.sleep(500) // time for the new folders to be picked up
        File(folder, "plans/2026/budget.xlsx").writeText("numbers")

        waitUntil(description = "the file in the new folder") { seen.has("plans/2026/budget.xlsx", ChangeKind.Created) }
    }

    @Test
    fun temporaryFilesAreNotReported() {
        val folder = TestEnvironment.newFolder()
        val seen = watch(folder)

        File(folder, ".hidden").writeText("x")
        File(folder, "save.tmp").writeText("x")
        File(folder, "~\$Report.docx").writeText("x")
        File(folder, "real.txt").writeText("x")

        waitUntil(description = "the real file to be reported") { seen.has("real.txt", ChangeKind.Created) }
        Thread.sleep(300)
        assertEquals(setOf("real.txt"), seen.changes.map { it.path }.toSet())
    }

    @Test
    fun folderNamesThemselvesAreNotReportedAsFiles() {
        val folder = TestEnvironment.newFolder()
        val seen = watch(folder)

        File(folder, "just-a-folder").mkdirs()
        File(folder, "marker.txt").writeText("x")

        waitUntil(description = "the marker file") { seen.has("marker.txt", ChangeKind.Created) }
        assertTrue(seen.changes.none { it.path == "just-a-folder" }, seen.changes.toString())
    }

    @Test
    fun nothingIsReportedAfterItIsClosed() {
        val folder = TestEnvironment.newFolder()
        val seen = Seen()
        val watcher = watchFolder(folder.path) { seen.changes += it }
        Thread.sleep(300)

        watcher.close()
        File(folder, "late.txt").writeText("too late")
        Thread.sleep(700)

        assertTrue(seen.changes.isEmpty(), seen.changes.toString())
    }
}
