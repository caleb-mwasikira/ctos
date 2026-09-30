package com.ctos.client.data

import com.ctos.client.network.Department

/** A file in a department's local folder. [path] is relative to that folder, written with "/". */
data class LocalFile(
    val path: String,
    val size: Long,
    /** When it was last changed, in milliseconds since 1970. */
    val modifiedAt: Long,
) {
    val name: String get() = path.substringAfterLast('/')
    val folder: String get() = path.substringBeforeLast('/', missingDelimiterValue = "")
}

/** What changed in a folder between two looks at it. */
data class FolderDiff(
    val added: List<LocalFile>,
    val changed: List<LocalFile>,
    val removed: List<LocalFile>,
) {
    val isEmpty: Boolean get() = added.isEmpty() && changed.isEmpty() && removed.isEmpty()
}

/** Compares two looks at a folder. A file is "changed" when its size or its modification time differs. */
fun diffFolders(before: Collection<LocalFile>, after: Collection<LocalFile>): FolderDiff {
    val beforeByPath = before.associateBy { it.path }
    val afterByPath = after.associateBy { it.path }
    return FolderDiff(
        added = after.filter { it.path !in beforeByPath }.sortedBy { it.path },
        changed = after.filter { file -> beforeByPath[file.path]?.let { it.size != file.size || it.modifiedAt != file.modifiedAt } == true }.sortedBy { it.path },
        removed = before.filter { it.path !in afterByPath }.sortedBy { it.path },
    )
}

/**
 * Files nobody wants synced: the temporary files editors and browsers leave beside the real
 * one (a half-saved copy, a lock file). They come and go while a file is being saved.
 */
fun isIgnoredFile(name: String): Boolean =
    name.startsWith(".") ||
        name.startsWith("~$") ||
        name.endsWith("~") ||
        name.endsWith(".tmp") ||
        name.endsWith(".swp") ||
        name.endsWith(".part") ||
        name.endsWith(".crdownload") ||
        name == "Thumbs.db" ||
        name == "desktop.ini"

/** Where a department's files live on this device: <sync root>/<organization>/<department>. */
fun departmentFolder(department: Department): String =
    "${syncRootDirectory()}/${department.organization.slug}/${department.slug}"

/** Where a Secure Drop (docs/secure-drop.md) is saved once decrypted: it is not part of any department's own synced folder. */
fun secureDropDownloadsFolder(): String = "${syncRootDirectory()}/Secure Drop"

/**
 * Every file below [root] (folders are not listed), sorted by path. The folder is created when it
 * does not exist yet, so a department always has a place to drop files into.
 */
expect fun scanFolder(root: String): List<LocalFile>
