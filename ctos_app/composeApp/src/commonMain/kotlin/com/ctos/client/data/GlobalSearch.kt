package com.ctos.client.data

import com.ctos.client.network.Department

/** A file or folder found by a search, wherever it lives. */
data class FileSearchResult(
    val departmentSlug: String,
    val departmentName: String,
    val path: String,
    val isFolder: Boolean,
) {
    val name: String get() = path.substringAfterLast('/')

    /** The folder to open to see it: the file's own parent, or the folder itself. */
    val folderToOpen: String get() = if (isFolder) path else parentPath(path)
}

/** One thing a search turned up, of whichever kind. */
sealed interface SearchResult {
    data class OrganizationHit(val organization: OrganizationSummary) : SearchResult

    data class DepartmentHit(val department: Department) : SearchResult

    data class FileHit(val file: FileSearchResult) : SearchResult
}

/** Every match for one search, grouped the way the popup shows them. */
data class GlobalSearchResults(
    val organizations: List<OrganizationSummary> = emptyList(),
    val departments: List<Department> = emptyList(),
    val files: List<FileSearchResult> = emptyList(),
) {
    val isEmpty: Boolean get() = organizations.isEmpty() && departments.isEmpty() && files.isEmpty()
}

/**
 * Searches organizations, departments, and every synced department's files and folders, all by
 * name, all at once β€” everything the app already knows on this device, with no request to the
 * server. [syncStates] is [SyncManager.states]: a department not in it (not syncing, or never
 * opened) simply contributes no files, the same as one with an empty folder.
 */
fun searchEverything(
    query: String,
    organizations: List<OrganizationSummary>,
    departments: List<Department>,
    syncStates: Map<String, DepartmentSyncState>,
    maxPerGroup: Int = 30,
): GlobalSearchResults {
    val text = query.trim().lowercase()
    if (text.isEmpty()) return GlobalSearchResults()

    val organizationHits = organizations
        .filter { it.organization.name.lowercase().contains(text) }
        .sortedBy { it.organization.name.lowercase() }
        .take(maxPerGroup)

    val departmentHits = departments
        .filter { it.name.lowercase().contains(text) }
        .sortedBy { it.name.lowercase() }
        .take(maxPerGroup)

    val departmentNames = departments.associate { it.slug to it.name }
    val fileHits = syncStates.flatMap { (slug, state) ->
        val departmentName = departmentNames[slug] ?: slug
        val files = state.folder.files
            .filter { it.name.lowercase().contains(text) }
            .map { FileSearchResult(slug, departmentName, it.path, isFolder = false) }
        val folders = state.folder.directories
            .filter { it.substringAfterLast('/').lowercase().contains(text) }
            .map { FileSearchResult(slug, departmentName, it, isFolder = true) }
        files + folders
    }.sortedBy { it.name.lowercase() }.take(maxPerGroup)

    return GlobalSearchResults(organizationHits, departmentHits, fileHits)
}
