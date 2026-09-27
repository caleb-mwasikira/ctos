package com.ctos.client.data

/** One line of the file explorer: a folder or a file directly inside the folder being looked at. */
data class ExplorerRow(
    val name: String,
    /** Relative to the department, written with "/". */
    val path: String,
    val isFolder: Boolean,
    /** Bytes; 0 for a folder. */
    val size: Long,
    /** The file's modification time, or for a folder the latest of what is inside it; null for an empty folder. */
    val modifiedAt: Long?,
    /** A file that the server does not have yet (new, or changed here since the last sync). */
    val waitingToUpload: Boolean = false,
)

/**
 * What is directly inside the folder [path] ("" is the department's top folder): folders first, then files,
 * each sorted by name without regard to case, like the website's explorer.
 */
fun explorerRows(folder: LocalFolderState, path: String, pending: Set<String> = emptySet()): List<ExplorerRow> {
    val prefix = if (path.isEmpty()) "" else "$path/"
    val folders = sortedMapOf<String, Long?>()

    for (directory in folder.directories) {
        if (!directory.startsWith(prefix)) continue
        folders.putIfAbsent(directory.removePrefix(prefix).substringBefore('/'), null)
    }

    val files = mutableListOf<ExplorerRow>()
    for (file in folder.files) {
        if (!file.path.startsWith(prefix)) continue
        val rest = file.path.removePrefix(prefix)
        if ('/' in rest) {
            val name = rest.substringBefore('/')
            val latest = folders[name]
            folders[name] = if (latest == null || file.modifiedAt > latest) file.modifiedAt else latest
        } else {
            files += ExplorerRow(rest, file.path, isFolder = false, size = file.size, modifiedAt = file.modifiedAt, waitingToUpload = file.path in pending)
        }
    }

    val folderRows = folders.map { (name, modifiedAt) -> ExplorerRow(name, prefix + name, isFolder = true, size = 0, modifiedAt = modifiedAt) }
    return folderRows.sortedBy { it.name.lowercase() } + files.sortedBy { it.name.lowercase() }
}

/** The steps from the department's top folder to [path]: ("reports/2026" gives reports, reports/2026). */
fun breadcrumbs(path: String): List<Pair<String, String>> {
    if (path.isEmpty()) return emptyList()
    val parts = path.split('/')
    return parts.indices.map { index -> parts[index] to parts.take(index + 1).joinToString("/") }
}

fun parentPath(path: String): String = path.substringBeforeLast('/', missingDelimiterValue = "")
