package com.ctos.client.data

import com.ctos.client.network.FileSource
import io.ktor.utils.io.ByteReadChannel

/**
 * The files of one department's folder on this device, and everything the sync needs to do with
 * them. Paths are relative to the folder and written with "/". Anything that would leave the folder
 * (a ".." part) is refused.
 */
interface LocalFiles {
    val root: String

    /** Whether the department's folder itself exists (it is created by [scan], and could have been removed since). */
    fun rootExists(): Boolean

    /** Every file below the folder, sorted by path. The folder is created when it does not exist yet. */
    fun scan(): List<LocalFile>

    /** Every sub-folder below the folder (not the folder itself), sorted by path. */
    fun directories(): List<String>

    /** The file at [path], or null when there is none (or it is a folder). */
    fun stat(path: String): LocalFile?

    fun directoryExists(path: String): Boolean

    /** The SHA-256 of the content, in lower-case hex. */
    fun sha256(path: String): String

    fun source(path: String): FileSource

    /**
     * Writes what [channel] delivers to [path] and gives it the modification time [modifiedAt]. The
     * content goes to a temporary file first and only replaces the file once it is complete and, when
     * [expectedSha256] is given, has that checksum: an interrupted or damaged download leaves the
     * old file alone. Folders on the way are created.
     */
    suspend fun writeFrom(path: String, channel: ByteReadChannel, modifiedAt: Long, expectedSha256: String?): LocalFile

    fun delete(path: String)

    fun makeDirectory(path: String)

    /** Removes the folder if it is empty; returns whether it is gone afterwards. */
    fun deleteDirectoryIfEmpty(path: String): Boolean

    fun move(from: String, to: String)
}

expect fun localFiles(root: String): LocalFiles

/**
 * Whether the server would accept [path] (the same rules as the API and the website): relative, "/"
 * separated, with no empty, "." or ".." part, no backslash and no control character.
 */
fun isSyncablePath(path: String): Boolean {
    if (path.isEmpty() || path.encodeToByteArray().size > 1024) return false
    if (path.any { it == '\\' || it.code < 0x20 || it.code == 0x7f }) return false
    return path.split('/').all { it.isNotEmpty() && it != "." && it != ".." && it.encodeToByteArray().size <= 255 }
}

/**
 * A name for the copy of a file that was changed on this device and by someone else at the same
 * time: "report.docx" becomes "report (conflict copy alice-laptop 2026-09-27).docx". [taken] says
 * whether a name is in use, so an older copy is never overwritten.
 */
fun conflictCopyPath(path: String, label: String, date: String, taken: (String) -> Boolean): String {
    val folder = path.substringBeforeLast('/', missingDelimiterValue = "")
    val name = path.substringAfterLast('/')
    val dot = name.lastIndexOf('.')
    val (stem, extension) = if (dot > 0) name.substring(0, dot) to name.substring(dot) else name to ""
    val safeLabel = label.filter { it.isLetterOrDigit() || it in " -_()" }.trim().ifEmpty { "device" }.take(40)
    val prefix = if (folder.isEmpty()) "" else "$folder/"

    var candidate = "$prefix$stem (conflict copy $safeLabel $date)$extension"
    var number = 2
    while (taken(candidate)) {
        candidate = "$prefix$stem (conflict copy $safeLabel $date $number)$extension"
        number++
    }
    return candidate
}
