package com.ctos.client.data

import com.ctos.client.network.CtosApi
import com.ctos.client.network.Department
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/** How a department's sync is going. */
data class SyncStatus(
    val phase: Phase = Phase.Starting,
    /** When the last run finished without an error, in milliseconds since 1970. */
    val lastSyncedAt: Long? = null,
    /** Why the last run could not finish (no connection...). */
    val error: String? = null,
    /** Files the last run could not sync, each with the reason. */
    val problems: List<String> = emptyList(),
) {
    enum class Phase { Starting, Syncing, Synced, Offline }
}

/** What the screen of one department needs: its folder on this device, how the sync is going, and what has not been sent yet. */
data class DepartmentSyncState(
    val folder: LocalFolderState,
    val status: SyncStatus = SyncStatus(),
    /** Files here that the server does not have yet (new, or changed since the last sync). */
    val pending: Set<String> = emptySet(),
)

/**
 * Keeps one department's folder and the server the same, for as long as it runs: the folder is watched
 * (see [LocalFolderTracker]), and whatever changes here, or is announced by the server ([requestSync]), or
 * simply time passing, starts a run of the [SyncEngine]. Runs never overlap.
 */
class DepartmentSync(
    private val scope: CoroutineScope,
    val departmentSlug: String,
    val root: String,
    api: CtosApi,
    private val store: SyncStore,
    deviceLabel: String,
    private val onState: (DepartmentSyncState) -> Unit,
    /** How long things must be quiet before a run starts, so a burst of changes is one run. */
    private val quietMillis: Long = 500,
    private val periodMillis: Long = 60_000,
    /** How soon to try again after a run that could not finish. */
    private val retryMillis: Long = 15_000,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val engine = SyncEngine(api, departmentSlug, localFiles(root), store, deviceLabel)
    private val current = MutableStateFlow(DepartmentSyncState(LocalFolderState(root)))
    private val requests = Channel<Unit>(Channel.CONFLATED)
    private var job: Job? = null
    private var tracker: LocalFolderTracker? = null
    private var lastLook: Pair<List<LocalFile>, List<String>>? = null

    fun start() {
        if (job != null) return
        tracker = LocalFolderTracker(scope, departmentSlug, root, onUpdate = ::folderChanged).also { it.start() }

        job = scope.launch(Dispatchers.IO) {
            launch {
                while (isActive) {
                    val struggling = current.value.status.let { it.phase == SyncStatus.Phase.Offline || it.problems.isNotEmpty() }
                    delay(if (struggling) retryMillis else periodMillis)
                    requests.trySend(Unit)
                }
            }

            requests.trySend(Unit)
            var first = true
            for (request in requests) {
                delay(quietMillis)
                requests.tryReceive()
                runOnce(full = first)
                first = false
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        tracker?.stop()
        tracker = null
    }

    /** Runs a sync soon (something changed on the server, or the user asked for it). */
    fun requestSync() {
        requests.trySend(Unit)
    }

    private fun folderChanged(folder: LocalFolderState) {
        publish { it.copy(folder = folder, pending = pendingIn(folder)) }
        val look = folder.files to folder.directories
        if (look != lastLook) {
            lastLook = look
            requestSync()
        }
    }

    private suspend fun runOnce(full: Boolean) {
        publish { it.copy(status = it.status.copy(phase = SyncStatus.Phase.Syncing)) }
        try {
            var report = engine.sync(full)
            var runs = 1
            while (report.again && report.error == null && runs < MAX_RUNS_IN_A_ROW) {
                report = engine.sync()
                runs++
            }
            publish {
                it.copy(
                    status = if (report.error != null) {
                        it.status.copy(phase = SyncStatus.Phase.Offline, error = report.error, problems = report.problems)
                    } else {
                        SyncStatus(SyncStatus.Phase.Synced, lastSyncedAt = now(), problems = report.problems)
                    },
                    pending = pendingIn(it.folder),
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            publish { it.copy(status = it.status.copy(phase = SyncStatus.Phase.Offline, error = e.message ?: "The sync failed.")) }
        }
    }

    private fun pendingIn(folder: LocalFolderState): Set<String> {
        val known = store.nodes(departmentSlug).associateBy { it.path }
        return folder.files.filter { file ->
            val base = known[file.path]
            base == null || base.isFolder || base.size != file.size || base.modifiedAt != file.modifiedAt
        }.mapTo(HashSet()) { it.path }
    }

    private fun publish(change: (DepartmentSyncState) -> DepartmentSyncState) {
        onState(current.updateAndGet(change))
    }

    private fun <T> MutableStateFlow<T>.updateAndGet(change: (T) -> T): T {
        update(change)
        return value
    }

    private companion object {
        const val MAX_RUNS_IN_A_ROW = 3
    }
}

/**
 * Runs a [DepartmentSync] for every department the user belongs to, and reports the state of each.
 * Departments that are added (joined) start syncing; ones that are gone (left, locked out) stop.
 */
class SyncManager(
    private val scope: CoroutineScope,
    private val api: () -> CtosApi,
    private val store: SyncStore,
    private val deviceLabel: String,
    private val folderOf: (Department) -> String = ::departmentFolder,
) {
    private val syncs = mutableMapOf<String, DepartmentSync>()
    private val slugById = mutableMapOf<Long, String>()
    private val _states = MutableStateFlow<Map<String, DepartmentSyncState>>(emptyMap())
    val states: StateFlow<Map<String, DepartmentSyncState>> = _states.asStateFlow()

    fun setDepartments(departments: List<Department>) {
        val wanted = departments.associateBy { it.slug }
        for (slug in syncs.keys - wanted.keys) stop(slug)
        for ((slug, department) in wanted) {
            slugById[department.id] = slug
            if (slug in syncs) continue
            val sync = DepartmentSync(
                scope = scope,
                departmentSlug = slug,
                root = folderOf(department),
                api = api(),
                store = store,
                deviceLabel = deviceLabel,
                onState = { state -> _states.update { it + (slug to state) } },
            )
            syncs[slug] = sync
            sync.start()
        }
    }

    /** The server says a department changed. */
    fun nudge(departmentId: Long) {
        slugById[departmentId]?.let { syncs[it]?.requestSync() }
    }

    fun syncNow(departmentSlug: String) {
        syncs[departmentSlug]?.requestSync()
    }

    fun syncAll() {
        syncs.values.forEach { it.requestSync() }
    }

    /** Stops syncing a department and forgets what was synced (its folder on this device stays). */
    fun stop(departmentSlug: String) {
        syncs.remove(departmentSlug)?.stop()
        slugById.values.remove(departmentSlug)
        _states.update { it - departmentSlug }
        store.forget(departmentSlug)
    }

    /** Signing out: stop everything, but keep what was synced, so signing in again carries on. */
    fun stopAll() {
        syncs.values.forEach { it.stop() }
        syncs.clear()
        slugById.clear()
        _states.value = emptyMap()
    }
}
