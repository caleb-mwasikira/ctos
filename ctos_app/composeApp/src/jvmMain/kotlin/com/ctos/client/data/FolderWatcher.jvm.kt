package com.ctos.client.data

import io.methvin.watcher.DirectoryChangeEvent
import io.methvin.watcher.DirectoryWatcher
import java.nio.file.Files
import java.nio.file.Path

/**
 * Uses directory-watcher, which asks each operating system for its native notifications:
 * inotify on Linux, FSEvents on macOS and ReadDirectoryChangesW on Windows. (Java's own
 * WatchService polls on macOS, which is slow.) File hashing is off: it would read every file
 * in the folder when the watcher starts, and a change is checked against size and time anyway.
 */
actual fun watchFolder(root: String, onChange: (FolderChange) -> Unit): FolderWatcher {
    val folder = Path.of(root)
    Files.createDirectories(folder)

    val watcher = DirectoryWatcher.builder()
        .path(folder)
        .fileHashing(false)
        .listener { event ->
            val kind = when (event.eventType()) {
                DirectoryChangeEvent.EventType.CREATE -> ChangeKind.Created
                DirectoryChangeEvent.EventType.MODIFY -> ChangeKind.Modified
                DirectoryChangeEvent.EventType.DELETE -> ChangeKind.Deleted
                // Events were lost: the caller must look at the whole folder again.
                DirectoryChangeEvent.EventType.OVERFLOW -> {
                    onChange(FolderChange("", ChangeKind.Modified))
                    return@listener
                }
            }
            val path = folder.relativize(event.path()).joinToString("/")
            if (!event.isDirectory && !isIgnoredFile(path.substringAfterLast('/'))) {
                onChange(FolderChange(path, kind))
            }
        }
        .build()
    watcher.watchAsync()

    return object : FolderWatcher {
        override fun close() = watcher.close()
    }
}
