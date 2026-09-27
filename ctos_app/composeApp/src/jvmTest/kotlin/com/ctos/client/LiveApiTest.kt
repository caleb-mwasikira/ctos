package com.ctos.client

import com.ctos.client.network.ApiException
import com.ctos.client.network.CtosApi
import com.ctos.client.network.JoinRequest
import com.ctos.client.network.LoginRequest
import com.ctos.client.network.configureCtosClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs the client against a real CTOS API (ctos_api) and its database. Skipped unless these
 * environment variables are set:
 *
 *   CTOS_TEST_API         http://127.0.0.1:9090/api/v1
 *   CTOS_TEST_EMAIL       a verified user
 *   CTOS_TEST_PASSWORD    that user's password
 *   CTOS_TEST_DEPARTMENT  the ID of a department the user has not joined yet
 *   CTOS_TEST_CODE        that department's access code
 */
class LiveApiTest {
    @Test
    fun signsInJoinsListensAndSignsOutAgainstTheRealApi() = runBlocking {
        val baseUrl = System.getenv("CTOS_TEST_API")
        assumeTrue("CTOS_TEST_API is not set", baseUrl != null)
        val email = System.getenv("CTOS_TEST_EMAIL")
        val password = System.getenv("CTOS_TEST_PASSWORD")
        val department = System.getenv("CTOS_TEST_DEPARTMENT")
        val code = System.getenv("CTOS_TEST_CODE")

        var token: String? = null
        var expired = 0
        val api = CtosApi(HttpClient(CIO) { configureCtosClient() }, baseUrl, { token }, { expired++ })

        // a wrong password is refused with the server's own message, and is not a "signed out"
        val refused = api.login(LoginRequest(email, "definitely-wrong", "Live test")).exceptionOrNull()
        assertEquals("Invalid credentials.", refused?.message)
        assertEquals(0, expired)

        token = api.login(LoginRequest(email, password, "Live test")).getOrThrow().token
        assertTrue(token.contains("|"), "a Sanctum token looks like 12|abc")

        val before = api.getDepartments().getOrThrow()
        assertTrue(before.none { it.slug == department })

        val wrong = api.joinDepartment(JoinRequest(department, "WRONG-CODE")).exceptionOrNull()
        assertEquals("The department ID or access code is incorrect.", wrong?.message)

        val joined = api.joinDepartment(JoinRequest(department, code)).getOrThrow()
        assertEquals(department, joined.slug)
        assertEquals("member", joined.role)
        assertTrue(api.getDepartments().getOrThrow().any { it.slug == department })

        // the WebSocket connects with the token
        val opened = CompletableDeferred<Unit>()
        val listening = launch { api.listen(onOpen = { opened.complete(Unit) }) {} }
        withTimeout(5_000) { opened.await() }
        listening.cancelAndJoin()

        // signing out deletes the token, so the next request is refused and the app is told
        assertTrue(api.logout().isSuccess)
        val afterLogout = api.getDepartments().exceptionOrNull()
        assertEquals(401, (afterLogout as ApiException).status)
        assertEquals(1, expired)
    }
}
