package com.ctos.client.data

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Copies [source] into [destination] file by file, keeping modification times, and skipping temporary and
 * hidden files (see [isIgnoredFile]). Empty folders are kept. Returns the number of files copied.
 */
fun copyTree(source: File, destination: File): Int {
    destination.mkdirs()
    var copied = 0
    for (entry in source.listFiles().orEmpty().sortedBy { it.name }) {
        if (isIgnoredFile(entry.name)) continue
        val target = File(destination, entry.name)
        if (entry.isDirectory) {
            copied += copyTree(entry, target)
        } else if (entry.isFile) {
            Files.copy(entry.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
            copied++
        }
    }
    return copied
}
