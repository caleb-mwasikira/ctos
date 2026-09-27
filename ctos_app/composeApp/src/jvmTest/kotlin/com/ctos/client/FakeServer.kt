package com.ctos.client

import com.ctos.client.data.SyncNode
import com.ctos.client.data.SyncStore
import com.ctos.client.network.CtosApi
import com.ctos.client.network.configureCtosClient
import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.security.MessageDigest

/**
 * A stand-in for the department file endpoints of the Go API (ctos_api/internal/handlers/files.go),
 * with the same rules: versions, "base_version" checks answered with 409, tombstones, and a change
 * log read with a cursor. [api] gives a client that talks to it, as one department's member.
 *
 * Uploads are the one call [CtosApi] never makes through an [io.ktor.client.HttpClient] engine at
 * all (see rawprotocol.go on the server, and RawUpload.kt/.jvm.kt on the client): it opens a real
 * socket instead. So unlike every other call here, which [MockEngine] answers without any real
 * networking, uploads are served by a real (if tiny) local HTTP server — the one thing that needs
 * to actually listen on a real port for these tests to reach the real client code under test. It
 * is started once per [FakeServer] and is never explicitly stopped: the JVM running the tests
 * closes it when the process ends, same as any other test-scoped resource here.
 */
class FakeServer(private val pageLimit: Int = 500) {
    class Node(
        val id: Long,
        val path: String,
        val isFolder: Boolean,
        var content: ByteArray = ByteArray(0),
        var version: Long = 1,
        var modifiedAt: Long = 0,
        var deleted: Boolean = false,
        var changeId: Long = 0,
    ) {
        val checksum: String? get() = if (isFolder) null else sha256(content)
    }

    private val nodes = mutableListOf<Node>()
    private var lastChange = 0L
    private var lastId = 0L

    /** While true every request fails as if there were no connection. */
    var offline = false

    /** When set, downloads send this instead of the real content (a damaged transfer). */
    var corruptDownloads = false

    val requests = mutableListOf<String>()

    private fun live(path: String) = nodes.firstOrNull { it.path == path && !it.deleted }

    private fun touch(node: Node) {
        node.changeId = ++lastChange
    }

    private fun ensureFolders(path: String) {
        val parts = path.split('/').dropLast(1)
        for (i in 1..parts.size) {
            val folder = parts.take(i).joinToString("/")
            if (live(folder) == null) nodes += Node(++lastId, folder, isFolder = true).also { touch(it) }
        }
    }

    // ── for tests to look at, and to change what is on the server ──────────

    fun files(): Map<String, String> = nodes.filter { !it.deleted && !it.isFolder }.associate { it.path to it.content.decodeToString() }

    fun folders(): List<String> = nodes.filter { !it.deleted && it.isFolder }.map { it.path }.sorted()

    fun version(path: String): Long? = live(path)?.version

    /** Someone else's change, made straight on the server. */
    fun put(path: String, content: String, modifiedAt: Long = 1_000) {
        ensureFolders(path)
        val existing = live(path)
        if (existing == null) {
            nodes += Node(++lastId, path, false, content.encodeToByteArray(), 1, modifiedAt).also { touch(it) }
        } else {
            existing.content = content.encodeToByteArray()
            existing.version++
            existing.modifiedAt = modifiedAt
            touch(existing)
        }
    }

    fun remove(path: String) {
        nodes.filter { !it.deleted && (it.path == path || it.path.startsWith("$path/")) }.forEach {
            it.deleted = true
            it.version++
            touch(it)
        }
    }

    // ── uploading: the one call that needs a real listening server ─────────

    /** Applies one PUT .../files, the same rules regardless of which server code path called it. */
    private fun handleUpload(path: String, baseVersion: Long?, body: ByteArray, claimedSha256: String?, modifiedAtHeader: String?): Pair<Int, JsonObject> {
        val existing = live(path)
        return when {
            claimedSha256 != null && !claimedSha256.equals(sha256(body), ignoreCase = true) ->
                422 to buildJsonObject { put("message", "The content does not match its checksum.") }
            existing != null && existing.isFolder ->
                422 to buildJsonObject { put("message", "A folder already has this name.") }
            existing != null && existing.content.contentEquals(body) ->
                200 to buildJsonObject { put("data", nodeJson(existing)) }
            existing != null && baseVersion != existing.version ->
                409 to conflictJson(existing)
            existing == null && baseVersion != 0L ->
                409 to conflictJson(null)
            else -> {
                val modifiedAt = modifiedAtHeader?.toLongOrNull() ?: 0
                val node = if (existing == null) {
                    ensureFolders(path)
                    Node(++lastId, path, false, body, 1, modifiedAt).also { nodes += it }
                } else {
                    existing.also {
                        it.content = body
                        it.version++
                        it.modifiedAt = modifiedAt
                    }
                }
                touch(node)
                (if (existing == null) 201 else 200) to buildJsonObject { put("data", nodeJson(node)) }
            }
        }
    }

    private fun conflictJson(current: Node?): JsonObject = buildJsonObject {
        put("message", "This file changed on the server since you last saw it.")
        put("current", current?.let { nodeJson(it) } ?: JsonPrimitive(null as String?))
    }

    /** The real local server uploads land on. Started once, the first time it is needed. */
    private val rawServer: HttpServer by lazy {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/api/v1/departments/") { exchange ->
            try {
                if (offline) {
                    exchange.close() // an abrupt drop, the same shape a lost connection has
                    return@createContext
                }
                if (exchange.requestMethod != "PUT" || !exchange.requestURI.path.endsWith("/files")) {
                    exchange.sendResponseHeaders(404, -1)
                    exchange.close()
                    return@createContext
                }
                val query = parseQuery(exchange.requestURI.rawQuery.orEmpty())
                val path = query["path"].orEmpty()
                requests += "PUT ${exchange.requestURI.path.substringAfter("/departments/")}?${exchange.requestURI.rawQuery}"

                val (status, body) = handleUpload(
                    path = path,
                    baseVersion = query["base_version"]?.toLongOrNull(),
                    body = exchange.requestBody.readBytes(),
                    claimedSha256 = exchange.requestHeaders.getFirst("X-Content-SHA256"),
                    modifiedAtHeader = exchange.requestHeaders.getFirst("X-Modified-At"),
                )
                val bytes = body.toString().encodeToByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            } catch (e: Exception) {
                runCatching { exchange.sendResponseHeaders(500, -1) }
                exchange.close()
            }
        }
        server.start()
        server
    }

    private fun parseQuery(raw: String): Map<String, String> =
        raw.split("&").filter { it.isNotEmpty() }.associate { pair ->
            val (key, value) = pair.split("=", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            URLDecoder.decode(key, "UTF-8") to URLDecoder.decode(value, "UTF-8")
        }

    // ── the HTTP side ──────────────────────────────────────────────────────

    fun api(department: String = "engineering"): CtosApi {
        val engine = MockEngine { request ->
            if (offline) throw IOException("no connection")
            requests += "${request.method.value} ${request.url.encodedPath.substringAfter("/departments/")}?${request.url.encodedQuery}"
            handle(request, department)
        }
        return CtosApi(HttpClient(engine) { configureCtosClient() }, "http://127.0.0.1:${rawServer.address.port}/api/v1", { "1|token" })
    }

    private fun nodeJson(node: Node): JsonObject = buildJsonObject {
        put("id", node.id)
        put("path", node.path)
        put("type", if (node.isFolder) "folder" else "file")
        put("size", node.content.size.toLong())
        put("checksum", node.checksum)
        put("version", node.version)
        put("modified_at", node.modifiedAt)
        put("deleted", node.deleted)
    }

    private fun MockRequestHandleScope.json(status: HttpStatusCode, body: JsonObject) =
        respond(body.toString(), status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun MockRequestHandleScope.data(status: HttpStatusCode, body: JsonObject) = json(status, buildJsonObject { put("data", body) })

    private fun MockRequestHandleScope.error(status: HttpStatusCode, message: String) = json(status, buildJsonObject { put("message", message) })

    private suspend fun MockRequestHandleScope.handle(request: HttpRequestData, department: String): HttpResponseData {
        val route = request.url.encodedPath.substringAfter("/departments/$department/", "")
        val query = request.url.parameters
        val path = query["path"]

        return when {
            request.method == HttpMethod.Get && route == "changes" -> {
                val cursor = query["cursor"]!!.toLong()
                val limit = minOf(query["limit"]!!.toInt(), pageLimit)
                val changed = nodes.filter { it.changeId > cursor }.sortedBy { it.changeId }
                val page = changed.take(limit)
                data(
                    HttpStatusCode.OK,
                    buildJsonObject {
                        put("nodes", buildJsonArray { page.forEach { add(nodeJson(it)) } })
                        put("cursor", page.lastOrNull()?.changeId ?: cursor)
                        put("more", changed.size > page.size)
                    },
                )
            }

            request.method == HttpMethod.Get && route == "files" -> {
                val node = live(path!!)?.takeIf { !it.isFolder } ?: return error(HttpStatusCode.NotFound, "Not found.")
                val body = if (corruptDownloads) "damaged".encodeToByteArray() else node.content
                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/octet-stream"))
            }

            // PUT .../files (uploading) is never routed here: see rawServer above.

            request.method == HttpMethod.Post && route == "folders" -> {
                val folder = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).let { (it as JsonObject)["path"] as JsonPrimitive }.content
                val existing = live(folder)
                if (existing != null) {
                    data(HttpStatusCode.OK, nodeJson(existing))
                } else {
                    ensureFolders("$folder/x")
                    data(HttpStatusCode.Created, nodeJson(live(folder)!!))
                }
            }

            request.method == HttpMethod.Delete && route == "nodes" -> {
                val existing = live(path!!) ?: return error(HttpStatusCode.NotFound, "Not found.")
                val base = query["base_version"]?.toLongOrNull()
                if (base != null && base != existing.version) {
                    json(
                        HttpStatusCode.Conflict,
                        conflictJson(existing),
                    )
                } else {
                    remove(path)
                    data(HttpStatusCode.OK, buildJsonObject { put("deleted", 1) })
                }
            }

            else -> error(HttpStatusCode.NotFound, "Unknown route $route")
        }
    }
}

fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** A [SyncStore] that lives in memory, one per simulated device. */
class MemorySyncStore : SyncStore {
    private val nodes = mutableMapOf<Pair<String, String>, SyncNode>()
    private val cursors = mutableMapOf<String, Long>()

    override fun node(departmentSlug: String, path: String) = nodes[departmentSlug to path]

    override fun nodes(departmentSlug: String) = nodes.filterKeys { it.first == departmentSlug }.values.toList()

    override fun put(departmentSlug: String, node: SyncNode) {
        nodes[departmentSlug to node.path] = node
    }

    override fun delete(departmentSlug: String, path: String) {
        nodes.remove(departmentSlug to path)
    }

    override fun cursor(departmentSlug: String) = cursors[departmentSlug] ?: 0L

    override fun saveCursor(departmentSlug: String, cursor: Long) {
        cursors[departmentSlug] = cursor
    }

    override fun forget(departmentSlug: String) {
        nodes.keys.removeAll { it.first == departmentSlug }
        cursors.remove(departmentSlug)
    }
}
