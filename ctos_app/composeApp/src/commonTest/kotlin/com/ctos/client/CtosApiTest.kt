package com.ctos.client

import com.ctos.client.network.ApiException
import com.ctos.client.network.CtosApi
import com.ctos.client.network.JoinRequest
import com.ctos.client.network.LoginRequest
import com.ctos.client.network.configureCtosClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CtosApiTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private class Recorder {
        val requests = mutableListOf<HttpRequestData>()
        var sessionExpired = 0
    }

    private fun api(
        recorder: Recorder = Recorder(),
        token: String? = "12|secret",
        baseUrl: String = "http://api.test/api/v1",
        handler: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): CtosApi {
        val engine = MockEngine { request ->
            recorder.requests += request
            handler(request)
        }
        return CtosApi(
            client = HttpClient(engine) { configureCtosClient() },
            baseUrl = baseUrl,
            token = { token },
            onSessionExpired = { recorder.sessionExpired++ },
        )
    }

    private fun MockRequestHandleScope.json(status: HttpStatusCode, body: String) = respond(body, status, jsonHeaders)

    private val departmentsJson = """
        {"data":[
          {"id":4,"name":"Design Systems","slug":"design-systems","organization":{"id":3,"name":"Globex","slug":"globex"},"role":"lead"},
          {"id":1,"name":"Engineering","slug":"engineering","organization":{"id":1,"name":"Acme","slug":"acme"},"role":"admin"}
        ]}
    """.trimIndent()

    // ── login ──────────────────────────────────────────────────────────────

    @Test
    fun loginSendsTheDeviceNameAndReturnsTheToken() = runTest {
        val recorder = Recorder()
        val api = api(recorder, token = null) {
            json(HttpStatusCode.OK, """{"data":{"token":"7|abc","user":{"id":1,"name":"Alice","email":"alice@example.com"}}}""")
        }

        val data = api.login(LoginRequest("alice@example.com", "secret", "Alice laptop")).getOrThrow()

        assertEquals("7|abc", data.token)
        assertEquals("Alice", data.user.name)
        val request = recorder.requests.single()
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("http://api.test/api/v1/auth/login", request.url.toString())
        val body = (request.body as TextContent).text
        assertTrue(body.contains("\"device_name\":\"Alice laptop\""), body)
        assertNull(request.headers[HttpHeaders.Authorization], "the login must not carry a token")
    }

    @Test
    fun aWrongPasswordShowsTheServersMessageAndDoesNotSignAnyoneOut() = runTest {
        val recorder = Recorder()
        val api = api(recorder) { json(HttpStatusCode.Unauthorized, """{"message":"Invalid credentials."}""") }

        val error = api.login(LoginRequest("a@b.co", "wrong", "d")).exceptionOrNull()

        assertIs<ApiException>(error)
        assertEquals("Invalid credentials.", error.message)
        assertEquals(401, error.status)
        assertEquals(0, recorder.sessionExpired)
    }

    @Test
    fun anUnverifiedEmailAndTooManyTriesShowTheirOwnMessages() = runTest {
        val unverified = api { json(HttpStatusCode.Forbidden, """{"message":"Your email address is not verified. Open the link we emailed you, then log in again."}""") }
        assertTrue(unverified.login(LoginRequest("a@b.co", "x", "d")).exceptionOrNull()!!.message!!.contains("not verified"))

        val locked = api { json(HttpStatusCode.TooManyRequests, """{"message":"Too many wrong passwords. Try again in a few minutes."}""") }
        assertEquals(429, (locked.login(LoginRequest("a@b.co", "x", "d")).exceptionOrNull() as ApiException).status)
    }

    @Test
    fun aFieldErrorIsShownInsteadOfTheGeneralMessage() = runTest {
        val api = api {
            json(HttpStatusCode.UnprocessableEntity, """{"message":"The email field must be a valid email address.","errors":{"email":["The email field must be a valid email address."]}}""")
        }

        assertEquals(
            "The email field must be a valid email address.",
            api.login(LoginRequest("nope", "x", "d")).exceptionOrNull()!!.message,
        )
    }

    // ── departments ────────────────────────────────────────────────────────

    @Test
    fun departmentsAreFetchedWithTheBearerToken() = runTest {
        val recorder = Recorder()
        val api = api(recorder) { json(HttpStatusCode.OK, departmentsJson) }

        val departments = api.getDepartments().getOrThrow()

        assertEquals(listOf("Design Systems", "Engineering"), departments.map { it.name })
        assertEquals("globex", departments[0].organization.slug)
        assertEquals("lead", departments[0].role)
        val request = recorder.requests.single()
        assertEquals("Bearer 12|secret", request.headers[HttpHeaders.Authorization])
        assertEquals("http://api.test/api/v1/departments", request.url.toString())
    }

    @Test
    fun aRefusedTokenSignsTheUserOutOnce() = runTest {
        val recorder = Recorder()
        val api = api(recorder) { json(HttpStatusCode.Unauthorized, """{"message":"Unauthenticated."}""") }

        val error = api.getDepartments().exceptionOrNull()

        assertEquals("Unauthenticated.", error?.message)
        assertEquals(1, recorder.sessionExpired)
    }

    @Test
    fun joiningSendsTheCode() = runTest {
        val recorder = Recorder()
        val api = api(recorder) {
            json(HttpStatusCode.Created, """{"data":{"id":4,"name":"Design Systems","slug":"design-systems","organization":{"id":3,"name":"Globex","slug":"globex"},"role":"member"}}""")
        }

        val department = api.joinDepartment(JoinRequest("ABCDE23456")).getOrThrow()

        assertEquals("Design Systems", department.name)
        val request = recorder.requests.single()
        assertEquals("http://api.test/api/v1/departments/join", request.url.toString())
        val body = (request.body as TextContent).text
        assertTrue(body.contains("\"code\":\"ABCDE23456\""), body)
    }

    @Test
    fun aWrongAccessCodeShowsItsMessage() = runTest {
        val message = "That access code is incorrect."
        val api = api { json(HttpStatusCode.UnprocessableEntity, """{"errors":{"code":["$message"]},"message":"$message"}""") }

        assertEquals(message, api.joinDepartment(JoinRequest("x")).exceptionOrNull()!!.message)
    }

    @Test
    fun logoutPostsWithTheTokenAndNeedsNoBody() = runTest {
        val recorder = Recorder()
        val api = api(recorder) { json(HttpStatusCode.OK, """{"message":"Logged out."}""") }

        assertTrue(api.logout().isSuccess)

        val request = recorder.requests.single()
        assertEquals("http://api.test/api/v1/auth/logout", request.url.toString())
        assertEquals("Bearer 12|secret", request.headers[HttpHeaders.Authorization])
    }

    // ── things going wrong ─────────────────────────────────────────────────

    @Test
    fun noConnectionGivesAReadableMessage() = runTest {
        val engine = MockEngine { throw RuntimeException("connection refused") }
        val api = CtosApi(HttpClient(engine) { configureCtosClient() }, "http://api.test/api/v1", { null })

        val error = api.getDepartments().exceptionOrNull()

        assertIs<ApiException>(error)
        assertNull(error.status)
        assertTrue(error.message!!.contains("Could not reach the server"))
    }

    @Test
    fun anAnswerThatCannotBeReadGivesAReadableMessage() = runTest {
        val api = api { json(HttpStatusCode.OK, "<html>not json</html>") }

        val error = api.getDepartments().exceptionOrNull()

        assertTrue(error!!.message!!.contains("could not read"), error.message)
    }

    @Test
    fun anErrorWithoutAJsonBodyStillSaysWhatHappened() = runTest {
        val api = api { respond("404 page not found", HttpStatusCode.NotFound) }

        val error = api.getDepartments().exceptionOrNull() as ApiException

        assertEquals(404, error.status)
        assertTrue(error.message!!.contains("404"), error.message)
    }

    @Test
    fun theWebSocketAddressFollowsTheApiAddress() {
        assertEquals("ws://localhost:9090/api/v1/ws", api(baseUrl = "http://localhost:9090/api/v1") { json(HttpStatusCode.OK, "{}") }.webSocketUrl)
        assertEquals("wss://api.example.com/api/v1/ws", api(baseUrl = "https://api.example.com/api/v1") { json(HttpStatusCode.OK, "{}") }.webSocketUrl)
        assertFalse(api { json(HttpStatusCode.OK, "{}") }.webSocketUrl.startsWith("http"))
    }
}
