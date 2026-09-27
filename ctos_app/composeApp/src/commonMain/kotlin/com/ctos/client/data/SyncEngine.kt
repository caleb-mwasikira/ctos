package com.ctos.client.data

import com.ctos.client.network.ApiException
import com.ctos.client.network.CtosApi
import com.ctos.client.network.RemoteNode
import com.ctos.client.network.WriteOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/** What one run of the sync did. [error] is set when the run could not be finished at all (no connection). */
data class SyncReport(
    val downloaded: Int = 0,
    val uploaded: Int = 0,
    val removedHere: Int = 0,
    val removedOnServer: Int = 0,
    /** Files changed on this device and by someone else: the device's version was kept as a "conflict copy". */
    val conflicts: Int = 0,
    /** Files that could not be synced, each with the reason. The rest was synced. */
    val problems: List<String> = emptyList(),
    val error: String? = null,
    /** True when something was found that the next run should take up straight away. */
    val again: Boolean = false,
) {
    val changedSomething: Boolean get() = downloaded + uploaded + removedHere + removedOnServer + conflicts > 0
}

/** A run that cannot go on (no connection, signed out), as opposed to a problem with one file. */
private class SyncStopped(message: String) : Exception(message)

/**
 * Makes one department's folder on this device and the department on the server the same.
 *
 * A run first **pulls**: it reads the server's change log from where it stopped last time and brings
 * each changed file or folder here. Then it **pushes**: it compares the folder with what was the same
 * at the last sync ([SyncStore]) and sends what is new, changed or gone.
 *
 * Nothing is ever overwritten without knowing what was there. An upload states the server's version it
 * is based on, and the server refuses it if the file moved on; a download replaces the local file only
 * if the local file is still the one from the last sync. When both sides changed a file, the local one
 * is kept beside it as "name (conflict copy ...)" and the server's version takes the original's place;
 * the copy is then uploaded like any new file. So the worst that can happen is an extra copy.
 */
class SyncEngine(
    private val api: CtosApi,
    private val departmentSlug: String,
    private val files: LocalFiles,
    private val store: SyncStore,
    private val deviceLabel: String,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val today: () -> String = { Clock.System.todayIn(TimeZone.currentSystemDefault()).toString() },
) {
    private var fullNext = true

    /** Set while the department's folder had vanished: files missing from it are fetched again, not taken as deleted. */
    private var restoreMissing = false

    private class Progress {
        var downloaded = 0
        var uploaded = 0
        var removedHere = 0
        var removedOnServer = 0
        var conflicts = 0
        var again = false
        val problems = mutableListOf<String>()
    }

    /**
     * Runs the sync once. With [full], the whole change log is read again instead of continuing from the
     * saved position (the app does this when it starts, and after a run that had problems); files that
     * are already in sync are skipped, so it only costs the listing.
     */
    suspend fun sync(full: Boolean = false): SyncReport {
        val progress = Progress()
        var error: String? = null
        try {
            // A folder that was removed under our feet is put back, never taken as "delete everything".
            val rootMissing = !files.rootExists()
            restoreMissing = rootMissing
            val readAll = full || fullNext || rootMissing
            fullNext = false
            pull(readAll, progress)
            if (!rootMissing) push(progress)
            if (progress.problems.isNotEmpty()) fullNext = true
        } catch (e: SyncStopped) {
            error = e.message
            fullNext = true
        }
        return SyncReport(
            downloaded = progress.downloaded,
            uploaded = progress.uploaded,
            removedHere = progress.removedHere,
            removedOnServer = progress.removedOnServer,
            conflicts = progress.conflicts,
            problems = progress.problems,
            error = error,
            again = progress.again,
        )
    }

    // ── server -> here ─────────────────────────────────────────────────────

    private suspend fun pull(full: Boolean, progress: Progress) {
        var cursor = if (full) 0L else store.cursor(departmentSlug)
        while (true) {
            val page = api.getChanges(departmentSlug, cursor).getOrElse { throw stopped(it) }
            // Folders come before what is in them, and the removal of what is in a folder before the folder's own.
            // A path that was deleted and made again is in the log twice; only the file that is there now counts.
            val alive = page.nodes.filter { !it.deleted }
            val alivePaths = alive.mapTo(HashSet()) { it.path }
            val ordered = alive.sortedBy { it.path } + page.nodes.filter { it.deleted && it.path !in alivePaths }.sortedByDescending { it.path }
            for (node in ordered) applyGuarded(node, progress)

            cursor = page.cursor
            if (cursor > store.cursor(departmentSlug)) store.saveCursor(departmentSlug, cursor)
            if (!page.more) break
        }
    }

    /** Applies one node from the server; a problem with it must not stop the others. */
    private suspend fun applyGuarded(node: RemoteNode, progress: Progress) {
        try {
            applyRemote(node, progress)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SyncStopped) {
            throw e
        } catch (e: ApiException) {
            if (e.status == null) throw SyncStopped(e.message ?: "The connection was lost.")
            progress.problems += "${node.path}: ${e.message}"
        } catch (e: Exception) {
            progress.problems += "${node.path}: ${e.message ?: e::class.simpleName}"
        }
    }

    private suspend fun applyRemote(node: RemoteNode, progress: Progress) {
        val base = store.node(departmentSlug, node.path)

        if (node.deleted) {
            removeHere(node.path, base, progress)
            return
        }
        if (node.isFolder) {
            if (files.stat(node.path) != null) throw IllegalStateException("a file here has the name of a folder on the server")
            // A folder we already knew about that is missing here was deleted here: the push tells the server.
            if (base == null || !base.isFolder || restoreMissing) files.makeDirectory(node.path)
            store.put(departmentSlug, SyncNode(node.path, true, node.version, "", 0, 0))
            return
        }

        val checksum = node.checksum ?: throw IllegalStateException("the server sent no checksum")
        if (base != null && !base.isFolder && base.checksum == checksum && (!restoreMissing || files.stat(node.path) != null)) {
            // The content is what we already synced (only the version number moved, for instance after a delete and re-create).
            // A file that is missing here is not fetched again: the push takes that as a deletion.
            if (base.version != node.version) store.put(departmentSlug, base.copy(version = node.version))
            return
        }

        val local = files.stat(node.path)
        val unchangedSinceSync = local != null && base != null && !base.isFolder && local.size == base.size && local.modifiedAt == base.modifiedAt
        when {
            local == null || unchangedSinceSync -> download(node, checksum, progress)
            local.size == node.size && files.sha256(node.path) == checksum ->
                // The same file was put on both sides: nothing to fetch.
                store.put(departmentSlug, SyncNode(node.path, false, node.version, checksum, local.size, local.modifiedAt))
            base != null && !base.isFolder && files.sha256(node.path) == base.checksum ->
                // Only the modification time moved (a "touch"): the content is still the synced one.
                download(node, checksum, progress)
            else -> {
                val copy = conflictCopyPath(node.path, deviceLabel, today()) { files.stat(it) != null || files.directoryExists(it) }
                files.move(node.path, copy)
                progress.conflicts++
                download(node, checksum, progress)
            }
        }
    }

    private suspend fun download(node: RemoteNode, checksum: String, progress: Progress) {
        var written: LocalFile? = null
        api.downloadFile(departmentSlug, node.path) { channel ->
            written = files.writeFrom(node.path, channel, node.modifiedAt ?: now(), checksum)
        }.getOrElse { throw it }
        val file = written ?: throw IllegalStateException("nothing was written")
        store.put(departmentSlug, SyncNode(node.path, false, node.version, checksum, file.size, file.modifiedAt))
        progress.downloaded++
    }

    /** The server deleted [path]: delete it here too, unless it was changed here since the last sync. */
    private fun removeHere(path: String, base: SyncNode?, progress: Progress) {
        if (base == null) return

        if (base.isFolder) {
            // A folder that still holds something (it is being emptied by the next changes, or has files
            // that were added here) stays, and is looked at again by the next full run.
            if (files.deleteDirectoryIfEmpty(path)) store.delete(departmentSlug, path) else fullNext = true
            return
        }

        val local = files.stat(path)
        val untouched = local != null && (local.size == base.size && local.modifiedAt == base.modifiedAt || files.sha256(path) == base.checksum)
        if (local != null && untouched) {
            files.delete(path)
            progress.removedHere++
        }
        // A file edited here since the last sync is kept, and goes back to the server as a new file.
        store.delete(departmentSlug, path)
    }

    // ── here -> server ─────────────────────────────────────────────────────

    private suspend fun push(progress: Progress) {
        val local = files.scan()
        val bases = store.nodes(departmentSlug).associateBy { it.path }
        val localPaths = local.mapTo(HashSet()) { it.path }

        // New and changed files.
        for (file in local) {
            if (!isSyncablePath(file.path)) {
                progress.problems += "${file.path}: this name cannot be synced"
                continue
            }
            val base = bases[file.path]
            attempt(file.path, progress) {
                when {
                    base == null -> upload(file, 0, progress)
                    base.isFolder -> progress.problems += "${file.path}: the server has a folder with this name"
                    file.size == base.size && file.modifiedAt == base.modifiedAt -> Unit
                    files.sha256(file.path) == base.checksum ->
                        store.put(departmentSlug, base.copy(size = file.size, modifiedAt = file.modifiedAt))
                    else -> upload(file, base.version, progress)
                }
            }
        }

        // Files that are gone from here.
        for (base in bases.values.filter { !it.isFolder && it.path !in localPaths }.sortedBy { it.path }) {
            attempt(base.path, progress) {
                deleteOnServer(base, progress) { progress.removedOnServer++ }
            }
        }

        // Folders: new ones here, and ones that are gone from here.
        val directories = files.directories()
        val directorySet = directories.toSet()
        for (directory in directories) {
            if (directory in bases || !isSyncablePath(directory)) continue
            attempt(directory, progress) {
                val node = api.createFolder(departmentSlug, directory).getOrElse { throw it }
                store.put(departmentSlug, SyncNode(directory, true, node.version, "", 0, 0))
                rememberFoldersAbove(directory)
            }
        }
        for (base in bases.values.filter { it.isFolder && it.path !in directorySet }.sortedByDescending { it.path }) {
            // Only once nothing below it is left (a file that could not be deleted keeps it).
            if (store.nodes(departmentSlug).any { it.path.startsWith(base.path + "/") }) continue
            attempt(base.path, progress) { deleteOnServer(base, progress) { } }
        }
    }

    private suspend fun upload(file: LocalFile, baseVersion: Long, progress: Progress) {
        val checksum = files.sha256(file.path)
        val outcome = api.uploadFile(departmentSlug, file.path, baseVersion, file.size, checksum, file.modifiedAt, files.source(file.path))
            .getOrElse { throw it }
        when (outcome) {
            is WriteOutcome.Done -> {
                val node = outcome.node
                store.put(departmentSlug, SyncNode(file.path, false, node?.version ?: (baseVersion + 1), node?.checksum ?: checksum, file.size, file.modifiedAt))
                rememberFoldersAbove(file.path)
                progress.uploaded++
            }
            is WriteOutcome.Conflict -> resolveConflict(file.path, outcome.current, progress)
        }
    }

    /**
     * The server makes the folders on the way to a file by itself. Remember them as synced, so that when
     * the change log later reports them, a folder deleted here in the meantime is not made again.
     * (Folders start at version 1; if that is wrong the server answers a later delete with a conflict, which is resolved.)
     */
    private fun rememberFoldersAbove(path: String) {
        val parts = path.split('/').dropLast(1)
        for (count in 1..parts.size) {
            val folder = parts.take(count).joinToString("/")
            if (store.node(departmentSlug, folder) == null) store.put(departmentSlug, SyncNode(folder, true, 1, "", 0, 0))
        }
    }

    private suspend fun deleteOnServer(base: SyncNode, progress: Progress, onDone: () -> Unit) {
        val result = api.deleteNode(departmentSlug, base.path, base.version)
        val outcome = result.getOrElse { error ->
            if (error is ApiException && error.status == 404) {
                store.delete(departmentSlug, base.path) // already gone from the server
                return
            }
            throw error
        }
        when (outcome) {
            is WriteOutcome.Done -> {
                store.delete(departmentSlug, base.path)
                onDone()
            }
            is WriteOutcome.Conflict -> resolveConflict(base.path, outcome.current, progress)
        }
    }

    /**
     * The server refused a change because the file moved on. Take the server's side in (which keeps the
     * local file as a conflict copy if it differs), and look again soon.
     */
    private suspend fun resolveConflict(path: String, current: RemoteNode?, progress: Progress) {
        progress.again = true
        if (current == null || current.deleted) {
            // Gone from the server: forget it; a file still here goes up again as a new file.
            store.delete(departmentSlug, path)
        } else {
            applyRemote(current, progress)
        }
    }

    /** Runs one step of the push; a problem with one file must not stop the others. */
    private suspend fun attempt(path: String, progress: Progress, step: suspend () -> Unit) {
        try {
            step()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SyncStopped) {
            throw e
        } catch (e: ApiException) {
            if (e.status == null) throw SyncStopped(e.message ?: "The connection was lost.")
            progress.problems += "$path: ${e.message}"
        } catch (e: Exception) {
            progress.problems += "$path: ${e.message ?: e::class.simpleName}"
        }
    }

    private fun stopped(error: Throwable): SyncStopped = SyncStopped(error.message ?: "The server could not be reached.")
}
