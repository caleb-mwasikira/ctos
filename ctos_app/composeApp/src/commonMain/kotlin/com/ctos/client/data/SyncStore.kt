package com.ctos.client.data

import com.ctos.client.db.AppDatabaseQueries

/**
 * What a file or folder was like on the server, and on this device, the last time they were the same.
 * A file whose size and modification time still match has not been changed here; one whose [version]
 * is behind the server's has been changed by someone else.
 */
data class SyncNode(
    val path: String,
    val isFolder: Boolean,
    val version: Long,
    val checksum: String,
    val size: Long,
    val modifiedAt: Long,
)

/** Where the sync keeps what it knows between runs. */
interface SyncStore {
    fun node(departmentSlug: String, path: String): SyncNode?

    fun nodes(departmentSlug: String): List<SyncNode>

    fun put(departmentSlug: String, node: SyncNode)

    fun delete(departmentSlug: String, path: String)

    /** How far into the department's change log this device has read; 0 for "not at all". */
    fun cursor(departmentSlug: String): Long

    fun saveCursor(departmentSlug: String, cursor: Long)

    /** Forgets everything about a department (its folder on this device is left alone). */
    fun forget(departmentSlug: String)
}

class SqlSyncStore(private val queries: AppDatabaseQueries) : SyncStore {
    override fun node(departmentSlug: String, path: String): SyncNode? =
        queries.getSyncNode(departmentSlug, path).executeAsOneOrNull()?.let {
            SyncNode(it.path, it.is_folder != 0L, it.version, it.checksum, it.size, it.modified_at)
        }

    override fun nodes(departmentSlug: String): List<SyncNode> =
        queries.getSyncNodes(departmentSlug).executeAsList().map {
            SyncNode(it.path, it.is_folder != 0L, it.version, it.checksum, it.size, it.modified_at)
        }

    override fun put(departmentSlug: String, node: SyncNode) {
        queries.upsertSyncNode(departmentSlug, node.path, if (node.isFolder) 1L else 0L, node.version, node.checksum, node.size, node.modifiedAt)
    }

    override fun delete(departmentSlug: String, path: String) {
        queries.deleteSyncNode(departmentSlug, path)
    }

    override fun cursor(departmentSlug: String): Long = queries.getSyncCursor(departmentSlug).executeAsOneOrNull() ?: 0L

    override fun saveCursor(departmentSlug: String, cursor: Long) {
        queries.saveSyncCursor(departmentSlug, cursor)
    }

    override fun forget(departmentSlug: String) {
        queries.transaction {
            queries.deleteSyncNodes(departmentSlug)
            queries.deleteSyncCursor(departmentSlug)
        }
    }
}
