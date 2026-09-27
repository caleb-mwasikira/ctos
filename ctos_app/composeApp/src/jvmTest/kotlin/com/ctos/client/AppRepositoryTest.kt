package com.ctos.client

import com.ctos.client.data.AppRepository
import com.ctos.client.data.LocalFile
import com.ctos.client.data.createDatabase
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AppRepositoryTest {
    @BeforeTest
    fun setUp() {
        TestEnvironment.ensure()
        AppRepository.clearSession()
    }

    @AfterTest
    fun tearDown() = AppRepository.clearSession()

    @Test
    fun thereIsNoSessionUntilSomeoneSignsIn() {
        assertNull(AppRepository.getCurrentSession())
    }

    @Test
    fun aSavedSessionIsFoundAgainAndCanBeCleared() {
        // (Signing in needs the server; the session is written the way a successful login writes it.)
        createDatabase().appDatabaseQueries.saveSession("7|abc", 1, "Alice Wanjiku", "alice@example.com")

        val session = assertNotNull(AppRepository.getCurrentSession())
        assertEquals("7|abc", session.token)
        assertEquals("Alice Wanjiku", session.user_name)
        assertEquals("alice@example.com", session.user_email)

        AppRepository.clearSession()
        assertNull(AppRepository.getCurrentSession())
    }

    @Test
    fun onlyOneSessionIsKept() {
        val queries = createDatabase().appDatabaseQueries
        queries.saveSession("1|first", 1, "First", "first@example.com")
        queries.saveSession("2|second", 2, "Second", "second@example.com")

        assertEquals("2|second", AppRepository.getCurrentSession()?.token)
    }

    @Test
    fun theFilesOfADepartmentAreSavedAndReplaced() {
        val slug = "dept-${UUID.randomUUID()}"
        AppRepository.saveFiles(slug, listOf(LocalFile("a.txt", 1, 10), LocalFile("b/c.txt", 2, 20)))
        assertEquals(listOf("a.txt", "b/c.txt"), AppRepository.savedFiles(slug).map { it.path }.sorted())

        AppRepository.saveFiles(slug, listOf(LocalFile("only.txt", 5, 50)))

        assertEquals(listOf(LocalFile("only.txt", 5, 50)), AppRepository.savedFiles(slug))
    }

    @Test
    fun eachDepartmentKeepsItsOwnFiles() {
        val one = "dept-${UUID.randomUUID()}"
        val two = "dept-${UUID.randomUUID()}"
        AppRepository.saveFiles(one, listOf(LocalFile("same-name.txt", 1, 1)))
        AppRepository.saveFiles(two, listOf(LocalFile("same-name.txt", 2, 2)))

        assertEquals(1L, AppRepository.savedFiles(one).single().size)
        assertEquals(2L, AppRepository.savedFiles(two).single().size)
    }
}
