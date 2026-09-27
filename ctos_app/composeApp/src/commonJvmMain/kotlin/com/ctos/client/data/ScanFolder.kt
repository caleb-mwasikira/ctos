package com.ctos.client.data

import java.io.File

// Shared by the desktop and Android builds: both are plain JVM file access.
actual fun scanFolder(root: String): List<LocalFile> {
    val folder = File(root)
    folder.mkdirs()
    return folder.walkTopDown()
        .onEnter { it == folder || !isIgnoredFile(it.name) }
        .filter { it.isFile && !isIgnoredFile(it.name) }
        .map { LocalFile(it.relativeTo(folder).invariantSeparatorsPath, it.length(), it.lastModified()) }
        .sortedBy { it.path }
        .toList()
}
