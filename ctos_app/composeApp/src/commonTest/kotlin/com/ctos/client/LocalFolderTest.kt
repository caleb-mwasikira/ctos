package com.ctos.client

import com.ctos.client.data.LocalFile
import com.ctos.client.data.diffFolders
import com.ctos.client.data.isIgnoredFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalFolderTest {
    private fun file(path: String, size: Long = 10, modifiedAt: Long = 1000) = LocalFile(path, size, modifiedAt)

    @Test
    fun aFileKnowsItsNameAndItsFolder() {
        assertEquals("plan.md", file("notes/2026/plan.md").name)
        assertEquals("notes/2026", file("notes/2026/plan.md").folder)
        assertEquals("", file("plan.md").folder)
        assertEquals("plan.md", file("plan.md").name)
    }

    @Test
    fun theSameFolderHasNoDifferences() {
        val files = listOf(file("a.txt"), file("b/c.txt"))

        assertTrue(diffFolders(files, files).isEmpty)
    }

    @Test
    fun newChangedAndRemovedFilesAreFound() {
        val before = listOf(file("keep.txt"), file("edit.txt", size = 5), file("touch.txt", modifiedAt = 1), file("gone.txt"))
        val after = listOf(file("keep.txt"), file("edit.txt", size = 6), file("touch.txt", modifiedAt = 2), file("new/one.txt"))

        val diff = diffFolders(before, after)

        assertEquals(listOf("new/one.txt"), diff.added.map { it.path })
        assertEquals(listOf("edit.txt", "touch.txt"), diff.changed.map { it.path })
        assertEquals(listOf("gone.txt"), diff.removed.map { it.path })
        assertFalse(diff.isEmpty)
    }

    @Test
    fun aRenamedFileIsOneRemovedAndOneAdded() {
        val diff = diffFolders(listOf(file("old.txt")), listOf(file("new.txt")))

        assertEquals(listOf("new.txt"), diff.added.map { it.path })
        assertEquals(listOf("old.txt"), diff.removed.map { it.path })
    }

    @Test
    fun theFilesEditorsAndBrowsersLeaveBesideTheRealOneAreIgnored() {
        listOf(".DS_Store", ".hidden", "~\$Report.docx", "draft.txt~", "save.tmp", "notes.swp", "movie.part", "file.crdownload", "Thumbs.db", "desktop.ini")
            .forEach { assertTrue(isIgnoredFile(it), it) }
        listOf("Report.docx", "notes.txt", "photo.jpg", "a.tmp.txt", "README").forEach { assertFalse(isIgnoredFile(it), it) }
    }
}
