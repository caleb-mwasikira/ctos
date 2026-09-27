package com.ctos.client.data

import com.ctos.client.network.Department
import com.ctos.client.network.DepartmentRole
import com.ctos.client.network.OrganizationRef
import com.ctos.client.network.OrganizationSummaryDto
import com.ctos.client.network.departmentRole

/**
 * An organization as the app shows it. The API has no organization list of its own: an
 * organization is known through the departments the user has joined in it.
 */
data class OrganizationSummary(
    val organization: OrganizationRef,
    val departments: List<Department>,
) {
    /** True when the user manages at least one department here. */
    val managesAnyDepartment: Boolean get() = departments.any { it.departmentRole.canManage }

    /** The most powerful role the user has in any department of this organization, or null when
     * there are none yet (a fresh organization with no departments, which only its owner sees). */
    val highestRole: DepartmentRole? get() = departments.map { it.departmentRole }.minByOrNull { it.ordinal }
}

/** The organizations behind a list of departments, sorted by name, each with its departments sorted by name. */
fun groupByOrganization(departments: List<Department>): List<OrganizationSummary> =
    departments
        .groupBy { it.organization.id }
        .map { (_, group) ->
            OrganizationSummary(group.first().organization, group.sortedWith(compareBy({ it.name.lowercase() }, { it.id })))
        }
        .sortedWith(compareBy({ it.organization.name.lowercase() }, { it.organization.id }))

/**
 * The organizations from [groupByOrganization], plus one for every organization in [all] that has
 * no departments yet (a fresh one, just created): those still belong on the dashboard, with an
 * empty department list, or they would be nowhere to be seen after creating them.
 */
fun mergeEmptyOrganizations(fromDepartments: List<OrganizationSummary>, all: List<OrganizationSummaryDto>): List<OrganizationSummary> {
    val known = fromDepartments.associateBy { it.organization.id }
    val empties = all.filter { it.id !in known }.map { OrganizationSummary(OrganizationRef(it.id, it.name, it.slug), emptyList()) }
    return (fromDepartments + empties).sortedWith(compareBy({ it.organization.name.lowercase() }, { it.organization.id }))
}

/**
 * Departments whose name contains [query], ignoring case, across every organization. The text is
 * matched literally. An empty query keeps everything. Sorted by name.
 */
fun searchDepartments(departments: List<Department>, query: String): List<Department> {
    val text = query.trim().lowercase()
    return departments
        .filter { text.isEmpty() || it.name.lowercase().contains(text) }
        .sortedWith(compareBy({ it.name.lowercase() }, { it.id }))
}
