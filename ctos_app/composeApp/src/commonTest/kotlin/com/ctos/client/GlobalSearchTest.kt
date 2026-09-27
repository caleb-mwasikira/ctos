package com.ctos.client

import com.ctos.client.data.DepartmentSyncState
import com.ctos.client.data.LocalFile
import com.ctos.client.data.LocalFolderState
import com.ctos.client.data.OrganizationSummary
import com.ctos.client.data.SearchResult
import com.ctos.client.data.searchEverything
import com.ctos.client.network.Department
import com.ctos.client.network.OrganizationRef
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlobalSearchTest {
    private val acme = OrganizationRef(1, "Acme Corp", "acme-corp")
    private val globex = OrganizationRef(2, "Globex", "globex")
    private val organizations = listOf(OrganizationSummary(acme, emptyList()), OrganizationSummary(globex, emptyList()))

    private val engineering = Department(1, "Engineering", "engineering", acme, "admin")
    private val finance = Department(2, "Finance", "finance", globex, "member")
    private val departments = listOf(engineering, finance)

    private val syncStates = mapOf(
        "engineering" to DepartmentSyncState(
            folder = LocalFolderState(
                path = "/x",
                files = listOf(LocalFile("Budget 2026.xlsx", 1, 1), LocalFile("notes/Meeting notes.md", 1, 1)),
                directories = listOf("notes", "Roadmap"),
            ),
        ),
        "finance" to DepartmentSyncState(
            folder = LocalFolderState(path = "/y", files = listOf(LocalFile("Invoice.pdf", 1, 1))),
        ),
    )

    @Test
    fun anEmptyQueryFindsNothing() {
        val results = searchEverything("", organizations, departments, syncStates)

        assertTrue(results.isEmpty)
    }

    @Test
    fun findsOrganizationsDepartmentsAndFilesByNameIgnoringCase() {
        val results = searchEverything("fin", organizations, departments, syncStates)

        assertEquals(emptyList(), results.organizations)
        assertEquals(listOf("Finance"), results.departments.map { it.name })
        assertEquals(emptyList(), results.files)
    }

    @Test
    fun findsAFileAcrossDepartmentsWithTheDepartmentItIsIn() {
        val results = searchEverything("budget", organizations, departments, syncStates)

        assertEquals(1, results.files.size)
        val hit = results.files.single()
        assertEquals("Budget 2026.xlsx", hit.name)
        assertEquals("engineering", hit.departmentSlug)
        assertEquals("Engineering", hit.departmentName)
        assertFalse(hit.isFolder)
    }

    @Test
    fun findsAFolderTooAndKnowsItIsOne() {
        val results = searchEverything("roadmap", organizations, departments, syncStates)

        val hit = results.files.single()
        assertEquals("Roadmap", hit.name)
        assertTrue(hit.isFolder)
        assertEquals("Roadmap", hit.folderToOpen)
    }

    @Test
    fun aFilesFolderToOpenIsItsParent() {
        val results = searchEverything("meeting", organizations, departments, syncStates)

        assertEquals("notes", results.files.single().folderToOpen)
    }

    @Test
    fun matchesAcrossEveryGroupAtOnce() {
        val results = searchEverything("e", organizations, departments, syncStates)

        assertTrue(results.organizations.isNotEmpty())
        assertTrue(results.departments.isNotEmpty())
        assertTrue(results.files.isNotEmpty())
        assertFalse(results.isEmpty)
    }

    @Test
    fun aDepartmentWithNoSyncedFolderJustContributesNoFiles() {
        val results = searchEverything("anything", organizations, departments, emptyMap())

        assertEquals(emptyList(), results.files)
    }

    @Test
    fun resultsAreSortedByNameAndEachGroupIsCapped() {
        val manyOrganizations = (1..40).map { OrganizationSummary(OrganizationRef(it.toLong(), "Match $it", "match-$it"), emptyList()) }

        val results = searchEverything("match", manyOrganizations, emptyList(), emptyMap(), maxPerGroup = 5)

        assertEquals(5, results.organizations.size)
        assertEquals(results.organizations.map { it.organization.name }.sorted(), results.organizations.map { it.organization.name })
    }
}
