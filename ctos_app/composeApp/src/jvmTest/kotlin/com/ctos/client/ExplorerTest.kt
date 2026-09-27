package com.ctos.client

import com.ctos.client.data.ExplorerRow
import com.ctos.client.data.LocalFile
import com.ctos.client.data.LocalFolderState
import com.ctos.client.data.breadcrumbs
import com.ctos.client.data.conflictCopyPath
import com.ctos.client.data.explorerRows
import com.ctos.client.data.isSyncablePath
import com.ctos.client.data.parentPath
import com.ctos.client.data.uniqueFolderName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExplorerTest {
    private val folder = LocalFolderState(
        path = "/x",
        files = listOf(
            LocalFile("zebra.txt", 10, 100),
            LocalFile("Apple.txt", 20, 200),
            LocalFile("reports/2026/q3.xlsx", 30, 300),
            LocalFile("reports/2026/q4.xlsx", 40, 900),
            LocalFile("reports/summary.md", 50, 400),
            LocalFile("Photos/a.jpg", 60, 500),
        ),
        directories = listOf("Empty", "Photos", "reports", "reports/2026", "reports/drafts"),
    )

    @Test
    fun theTopFolderListsFoldersFirstThenFilesEachSortedIgnoringCase() {
        val rows = explorerRows(folder, "")

        assertEquals(listOf("Empty", "Photos", "reports", "Apple.txt", "zebra.txt"), rows.map { it.name })
        assertEquals(listOf(true, true, true, false, false), rows.map { it.isFolder })
    }

    @Test
    fun aFolderShowsTheLatestTimeOfWhatIsInsideAndAnEmptyOneShowsNone() {
        val rows = explorerRows(folder, "").associateBy { it.name }

        assertEquals(900L, rows.getValue("reports").modifiedAt, "the newest file below it")
        assertEquals(500L, rows.getValue("Photos").modifiedAt)
        assertNull(rows.getValue("Empty").modifiedAt)
    }

    @Test
    fun aSubFolderListsOnlyWhatIsDirectlyInsideIt() {
        val rows = explorerRows(folder, "reports")

        assertEquals(listOf("2026", "drafts", "summary.md"), rows.map { it.name })
        assertEquals(listOf("reports/2026", "reports/drafts", "reports/summary.md"), rows.map { it.path })
        assertEquals(50L, rows.last().size)
    }

    @Test
    fun aFolderThatOnlyExistsThroughItsFilesStillAppears() {
        val onlyFiles = LocalFolderState("/x", files = listOf(LocalFile("a/b/c.txt", 1, 1)))

        assertEquals(listOf("a"), explorerRows(onlyFiles, "").map { it.name })
        assertEquals(listOf("b"), explorerRows(onlyFiles, "a").map { it.name })
        assertEquals(listOf("c.txt"), explorerRows(onlyFiles, "a/b").map { it.name })
    }

    @Test
    fun aFolderNameThatIsAPrefixOfAnotherIsNotMixedUp() {
        val similar = LocalFolderState("/x", files = listOf(LocalFile("docs/a.txt", 1, 1), LocalFile("docs-old/b.txt", 1, 1)), directories = listOf("docs", "docs-old"))

        assertEquals(listOf("a.txt"), explorerRows(similar, "docs").map { it.name })
    }

    @Test
    fun filesThatHaveNotBeenUploadedAreMarked() {
        val rows = explorerRows(folder, "", pending = setOf("Apple.txt", "reports/summary.md"))

        assertTrue(rows.single { it.name == "Apple.txt" }.waitingToUpload)
        assertFalse(rows.single { it.name == "zebra.txt" }.waitingToUpload)
        assertTrue(explorerRows(folder, "reports", pending = setOf("reports/summary.md")).single { it.name == "summary.md" }.waitingToUpload)
    }

    @Test
    fun anEmptyFolderHasNoRows() {
        assertEquals(emptyList<ExplorerRow>(), explorerRows(LocalFolderState("/x"), ""))
        assertEquals(emptyList<ExplorerRow>(), explorerRows(folder, "Empty"))
    }

    @Test
    fun breadcrumbsAndParents() {
        assertEquals(emptyList(), breadcrumbs(""))
        assertEquals(listOf("reports" to "reports", "2026" to "reports/2026"), breadcrumbs("reports/2026"))
        assertEquals("reports", parentPath("reports/2026"))
        assertEquals("", parentPath("reports"))
        assertEquals("", parentPath(""))
    }

    @Test
    fun aNewFolderNeverTakesTheNameOfOneThatIsThere() {
        assertEquals("Photos", uniqueFolderName("Photos") { false })
        assertEquals("Photos (2)", uniqueFolderName("Photos") { it == "Photos" })
        assertEquals("Photos (3)", uniqueFolderName("Photos") { it == "Photos" || it == "Photos (2)" })
    }

    @Test
    fun conflictCopiesKeepTheFolderAndTheExtensionAndNeverOverwriteAnEarlierCopy() {
        assertEquals("a/b/report (conflict copy alice-laptop 2026-09-27).docx", conflictCopyPath("a/b/report.docx", "alice-laptop", "2026-09-27") { false })
        assertEquals("Makefile (conflict copy x 2026-09-27)", conflictCopyPath("Makefile", "x", "2026-09-27") { false })
        assertEquals("archive.tar (conflict copy x 2026-09-27).gz", conflictCopyPath("archive.tar.gz", "x", "2026-09-27") { false })
        assertEquals(".env (conflict copy x 2026-09-27)", conflictCopyPath(".env", "x", "2026-09-27") { false })

        val taken = setOf("r (conflict copy x d).txt", "r (conflict copy x d 2).txt")
        assertEquals("r (conflict copy x d 3).txt", conflictCopyPath("r.txt", "x", "d") { it in taken })
        assertEquals("r (conflict copy Alices laptop (Linux) d).txt", conflictCopyPath("r.txt", "Alice's laptop (Linux)/", "d") { false }, "characters that are not safe in a name are dropped")
    }

    @Test
    fun onlyPathsTheServerAcceptsAreSynced() {
        for (good in listOf("a.txt", "dir/sub/file.tar.gz", "with space/ünï.txt", ".hidden-but-legal-name", "a".repeat(255))) assertTrue(isSyncablePath(good), good)
        for (bad in listOf("", "/abs", "trailing/", "a//b", "./a", "a/./b", "../a", "a/../b", "back\\slash", "ctrl\u0001char", "del\u007fchar", "a".repeat(256), "a/".repeat(600))) assertFalse(isSyncablePath(bad), bad)
    }
}
