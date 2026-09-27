package com.ctos.client.data

enum class ChangeKind { Created, Modified, Deleted }

/** A change the operating system reported. An empty [path] means "something changed, look again". */
data class FolderChange(val path: String, val kind: ChangeKind)

interface FolderWatcher {
    fun close()
}

/**
 * Watches [root] and everything below it, using the operating system's own change notifications,
 * and calls [onChange] (on a background thread) for each change. The notifications are only
 * hints: they can be lost or arrive in bursts, so the app also looks at the folder again from time
 * to time, and after a pause following a burst (see LocalFolderTracker).
 */
expect fun watchFolder(root: String, onChange: (FolderChange) -> Unit): FolderWatcher
