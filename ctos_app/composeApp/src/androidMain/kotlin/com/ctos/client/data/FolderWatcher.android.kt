package com.ctos.client.data

import android.os.FileObserver
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Android's FileObserver (inotify underneath) watches one folder only, not the folders inside
 * it. This keeps one observer per folder, and adds one when a new folder appears.
 */
@Suppress("DEPRECATION") // The FileObserver(String) constructor is the only one before Android 10.
actual fun watchFolder(root: String, onChange: (FolderChange) -> Unit): FolderWatcher {
    val folder = File(root).also { it.mkdirs() }
    val observers = ConcurrentHashMap<String, FileObserver>()
    val events = FileObserver.CREATE or FileObserver.MODIFY or FileObserver.CLOSE_WRITE or
        FileObserver.DELETE or FileObserver.MOVED_FROM or FileObserver.MOVED_TO

    fun watch(directory: File) {
        if (observers.containsKey(directory.path)) return
        val observer = object : FileObserver(directory.path, events) {
            override fun onEvent(event: Int, path: String?) {
                if (path == null || isIgnoredFile(path.substringAfterLast('/'))) return
                val changed = File(directory, path)
                val relative = changed.relativeTo(folder).invariantSeparatorsPath
                val kind = when (event and ALL_EVENTS) {
                    CREATE, MOVED_TO -> ChangeKind.Created
                    DELETE, MOVED_FROM -> ChangeKind.Deleted
                    else -> ChangeKind.Modified
                }
                if (kind == ChangeKind.Created && changed.isDirectory) {
                    // A new folder: watch it too, and let the caller look at what it already holds.
                    changed.walkTopDown().filter { it.isDirectory }.forEach(::watch)
                    onChange(FolderChange("", ChangeKind.Modified))
                } else if (!changed.isDirectory) {
                    onChange(FolderChange(relative, kind))
                }
            }
        }
        observers[directory.path] = observer
        observer.startWatching()
    }

    folder.walkTopDown().filter { it.isDirectory }.forEach(::watch)

    return object : FolderWatcher {
        override fun close() {
            observers.values.forEach { it.stopWatching() }
            observers.clear()
        }
    }
}
