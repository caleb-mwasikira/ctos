package com.ctos.client.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import javax.net.ssl.SSLSocketFactory

private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 30_000

// Shared by the desktop and Android builds: both are plain JVM sockets. Everything here talks to
// the raw bytes of the connection directly (never through a BufferedInputStream, which could read
// past the header lines into the answer frame); reads that need more than one byte at a time
// (the frame itself) only start once the header lines are known to be fully consumed.
internal actual suspend fun sendRawUpload(
    secure: Boolean,
    host: String,
    port: Int,
    requestLine: String,
    headers: List<Pair<String, String>>,
    contentLength: Long,
    source: FileSource,
): RawTransferAnswer = withContext(Dispatchers.IO) {
    val socket: Socket = if (secure) {
        (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket() as Socket
    } else {
        Socket()
    }
    socket.use {
        socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
        socket.soTimeout = READ_TIMEOUT_MS

        val output = socket.getOutputStream()
        writeRequest(output, requestLine, headers)
        source.read { bytes, count -> output.write(bytes, 0, count) }
        output.flush()

        val input = socket.getInputStream()
        val (status, responseHeaders) = readStatusLineAndHeaders(input)
        if (status != 101) {
            RawTransferAnswer(status, readPlainBody(input, responseHeaders))
        } else {
            readAnswerFrame(input)
        }
    }
}

private fun writeRequest(output: OutputStream, requestLine: String, headers: List<Pair<String, String>>) {
    val text = buildString {
        append(requestLine).append("\r\n")
        headers.forEach { (name, value) -> append(name).append(": ").append(value).append("\r\n") }
        append("\r\n")
    }
    output.write(text.toByteArray(Charsets.US_ASCII))
    output.flush()
}

/**
 * Reads the status line and the header lines that follow it, one byte at a time: only a few dozen
 * bytes ever cross this, so simplicity matters more than speed, and it guarantees nothing beyond
 * the blank line that ends the headers is ever touched.
 */
private fun readStatusLineAndHeaders(input: InputStream): Pair<Int, Map<String, String>> {
    val statusLine = readLine(input) ?: throw IOException("the connection closed before it answered")
    val status = statusLine.split(" ").getOrNull(1)?.toIntOrNull()
        ?: throw IOException("unexpected status line: $statusLine")

    val headers = mutableMapOf<String, String>()
    while (true) {
        val line = readLine(input) ?: throw IOException("the connection closed before it answered")
        if (line.isEmpty()) break
        val (name, value) = line.split(":", limit = 2).let { it[0] to it.getOrElse(1) { "" }.trim() }
        headers[name.lowercase()] = value
    }
    return status to headers
}

private fun readLine(input: InputStream): String? {
    val line = ByteArrayOutputStream()
    while (true) {
        val byte = input.read()
        if (byte < 0) return if (line.size() == 0) null else line.toString(Charsets.ISO_8859_1.name())
        if (byte == '\n'.code) {
            val bytes = line.toByteArray()
            val end = if (bytes.isNotEmpty() && bytes.last() == '\r'.code.toByte()) bytes.size - 1 else bytes.size
            return String(bytes, 0, end, Charsets.ISO_8859_1)
        }
        line.write(byte)
    }
}

/** The connection refused to upgrade: an ordinary HTTP response, whose body (if any) is Content-Length framed. */
private fun readPlainBody(input: InputStream, headers: Map<String, String>): String {
    val length = headers["content-length"]?.toIntOrNull() ?: return ""
    if (length <= 0) return ""
    val body = ByteArray(length)
    readFullyOrThrow(input, body)
    return body.toString(Charsets.UTF_8)
}

/** The connection upgraded: read the 8-byte frame header, then exactly that many JSON bytes. */
private fun readAnswerFrame(input: InputStream): RawTransferAnswer = try {
    val data = DataInputStream(input)
    val status = data.readInt()
    val length = data.readInt()
    val payload = if (length == 0) ByteArray(0) else ByteArray(length).also { data.readFully(it) }
    RawTransferAnswer(status, payload.toString(Charsets.UTF_8))
} catch (e: java.io.EOFException) {
    throw IOException("the connection closed before it answered", e)
} catch (e: SocketTimeoutException) {
    throw IOException("the connection timed out", e)
}

private fun readFullyOrThrow(input: InputStream, into: ByteArray) {
    var read = 0
    while (read < into.size) {
        val count = try {
            input.read(into, read, into.size - read)
        } catch (e: SocketTimeoutException) {
            throw IOException("the connection timed out", e)
        }
        if (count < 0) throw IOException("the connection closed before it answered")
        read += count
    }
}
