package com.ctos.client.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/** One change found in a department's folder. [whileClosed] marks one made while the app was not running. */
data class RecentChange(
    val path: String,
    val kind: ChangeKind,
    val at: Long,
    val whileClosed: Boolean = false,
)

data class LocalFolderState(
    val path: String,
    val files: List<LocalFile> = emptyList(),
    /** Every folder below the root, including empty ones. */
    val directories: List<String> = emptyList(),
    val recentChanges: List<RecentChange> = emptyList(),
    /** True while the operating system is reporting changes to us. */
    val watching: Boolean = false,
    val error: String? = null,
)

/**
 * Follows one department's local folder.
 *
 * The operating system's notifications only say "something changed": they can arrive in bursts
 * (saving one file can raise several) and can be lost. So a notification never says what changed.
 * It starts a short pause ([debounceMillis]); when things go quiet the folder is looked at again
 * and compared with the last look. That look is also repeated every [rescanMillis] regardless,
 * and once at the start, to find what changed while the app was closed.
 */
class LocalFolderTracker(
    private val scope: CoroutineScope,
    private val departmentSlug: String,
    private val root: String,
    private val onUpdate: (LocalFolderState) -> Unit,
    private val debounceMillis: Long = 400,
    private val rescanMillis: Long = 30_000,
    private val watch: (String, (FolderChange) -> Unit) -> FolderWatcher = ::watchFolder,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private var job: Job? = null
    private var watcher: FolderWatcher? = null
    private var state = LocalFolderState(root)
    private var known: List<LocalFile> = emptyList()
    private val local = localFiles(root)

    fun start() {
        if (job != null) return
        val looksNeeded = Channel<Unit>(Channel.CONFLATED)

        job = scope.launch(Dispatchers.IO) {
            look(firstLook = true)

            try {
                watcher = watch(root) { looksNeeded.trySend(Unit) }
                publish(state.copy(watching = true))
            } catch (e: Exception) {
                publish(state.copy(watching = false, error = "Changes to this folder are only noticed every ${rescanMillis / 1000} seconds: ${e.message}"))
            }

            launch {
                while (isActive) {
                    delay(rescanMillis)
                    looksNeeded.trySend(Unit)
                }
            }

            for (need in looksNeeded) {
                delay(debounceMillis)
                looksNeeded.tryReceive()
                look(firstLook = false)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        watcher?.close()
        watcher = null
    }

    /** Looks at the folder, records what differs from the last look, and tells the screen. */
    private fun look(firstLook: Boolean) {
        val directories: List<String>
        val files = try {
            scanFolder(root).also { directories = local.directories() }
        } catch (e: Exception) {
            publish(state.copy(error = "This folder cannot be read: ${e.message}"))
            return
        }

        val before = if (firstLook) AppRepository.savedFiles(departmentSlug) else known
        val diff = diffFolders(before, files)
        known = files

        // Nothing saved yet means this folder was never looked at: what is in it is not news.
        // Otherwise, on the first look, what differs from the saved list is what happened while
        // the app was closed.
        val isBaseline = firstLook && before.isEmpty()
        val at = now()
        val found = if (isBaseline) {
            emptyList()
        } else {
            buildList {
                diff.added.forEach { add(RecentChange(it.path, ChangeKind.Created, at, firstLook)) }
                diff.changed.forEach { add(RecentChange(it.path, ChangeKind.Modified, at, firstLook)) }
                diff.removed.forEach { add(RecentChange(it.path, ChangeKind.Deleted, at, firstLook)) }
            }
        }

        if (!diff.isEmpty || firstLook) {
            AppRepository.saveFiles(departmentSlug, files)
        }
        publish(state.copy(files = files, directories = directories, recentChanges = (found + state.recentChanges).take(MAX_RECENT_CHANGES), error = null))
    }

    private fun publish(newState: LocalFolderState) {
        state = newState
        onUpdate(newState)
    }

    private companion object {
        const val MAX_RECENT_CHANGES = 30
    }
}
