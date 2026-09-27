package com.ctos.client.data

import com.ctos.client.network.FileSource
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

// Shared by the desktop and Android builds: both are plain JVM file access.
actual fun localFiles(root: String): LocalFiles = JvmLocalFiles(File(root))

private const val BUFFER_SIZE = 64 * 1024

private class JvmLocalFiles(private val folder: File) : LocalFiles {
    override val root: String get() = folder.path

    private val rootPath = folder.toPath().toAbsolutePath().normalize()

    /** The file for [path], which must stay inside the folder. */
    private fun resolve(path: String): File {
        val file = folder.toPath().resolve(path).toAbsolutePath().normalize()
        if (!file.startsWith(rootPath) || file == rootPath) throw IOException("\"$path\" is outside the folder.")
        return file.toFile()
    }

    override fun rootExists(): Boolean = folder.isDirectory

    override fun scan(): List<LocalFile> = scanFolder(root)

    override fun directories(): List<String> {
        folder.mkdirs()
        return folder.walkTopDown()
            .onEnter { it == folder || !isIgnoredFile(it.name) }
            .filter { it.isDirectory && it != folder }
            .map { it.relativeTo(folder).invariantSeparatorsPath }
            .sorted()
            .toList()
    }

    override fun stat(path: String): LocalFile? {
        val file = resolve(path)
        return if (file.isFile) LocalFile(path, file.length(), file.lastModified()) else null
    }

    override fun directoryExists(path: String): Boolean = resolve(path).isDirectory

    override fun sha256(path: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        FileInputStream(resolve(path)).use { input ->
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
    }

    override fun source(path: String): FileSource {
        val file = resolve(path)
        return FileSource { onChunk ->
            withContext(Dispatchers.IO) {
                FileInputStream(file).use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        onChunk(buffer, count)
                    }
                }
            }
        }
    }

    override suspend fun writeFrom(path: String, channel: ByteReadChannel, modifiedAt: Long, expectedSha256: String?): LocalFile {
        val target = resolve(path)
        target.parentFile.mkdirs()
        // ".part" files are ignored by the folder scan, so a half-written file is never uploaded.
        val temporary = File(target.parentFile, ".${target.name}.part")
        try {
            withContext(Dispatchers.IO) {
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(BUFFER_SIZE)
                FileOutputStream(temporary).use { output ->
                    while (true) {
                        val count = channel.readAvailable(buffer, 0, buffer.size)
                        if (count < 0) break
                        if (count == 0) continue
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
                if (expectedSha256 != null && !digest.digest().toHex().equals(expectedSha256, ignoreCase = true)) {
                    throw IOException("The downloaded content of \"$path\" is not what the server has.")
                }
                temporary.setLastModified(modifiedAt)
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
        return LocalFile(path, target.length(), target.lastModified())
    }

    override fun delete(path: String) {
        val file = resolve(path)
        if (file.isFile && !file.delete()) throw IOException("\"$path\" could not be deleted.")
    }

    override fun makeDirectory(path: String) {
        val directory = resolve(path)
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("The folder \"$path\" could not be created.")
    }

    override fun deleteDirectoryIfEmpty(path: String): Boolean {
        val directory = resolve(path)
        if (!directory.exists()) return true
        // Ignored files (a lock file, a ".part") do not keep a folder alive; a hidden folder does.
        val leftovers = directory.listFiles().orEmpty()
        if (leftovers.any { !it.isFile || !isIgnoredFile(it.name) }) return false
        leftovers.forEach { it.delete() }
        return directory.delete()
    }

    override fun move(from: String, to: String) {
        val target = resolve(to)
        target.parentFile.mkdirs()
        Files.move(resolve(from).toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
