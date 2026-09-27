package com.ctos.client.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.runtime.Composable
import com.ctos.client.data.OrganizationSummary
import com.ctos.client.network.Department
import com.ctos.client.network.departmentRole
import com.ctos.client.screens.widgets.CardGrid
import com.ctos.client.screens.widgets.EmptyState
import com.ctos.client.screens.widgets.Page
import com.ctos.client.screens.widgets.PageTitle
import com.ctos.client.screens.widgets.ResourceCard
import com.ctos.client.screens.widgets.RoleBadge
import com.ctos.client.screens.widgets.SecondaryButton
import com.ctos.client.screens.widgets.SectionTitle

/** One organization: the departments the user has joined in it. */
@Composable
fun OrganizationContent(
    organization: OrganizationSummary?,
    onOpenDepartment: (Department) -> Unit,
    onOpenWebsite: () -> Unit,
) {
    Page {
        if (organization == null) {
            EmptyState(Icons.Default.Business, "Organization not found", "It may have been removed, or you no longer have access to it.")
            return@Page
        }

        PageTitle(organization.organization.name, "The departments you have joined in this organization.")
        SectionTitle("Departments")
        if (organization.departments.isEmpty()) {
            EmptyState(
                Icons.Default.Folder,
                "No departments yet",
                "Add one on the website, then share its access code with your team.",
            )
        } else {
            CardGrid(organization.departments) { department ->
                ResourceCard(
                    icon = Icons.Default.Folder,
                    title = department.name,
                    subtitle = department.slug,
                    onClick = { onOpenDepartment(department) },
                    badge = { RoleBadge(department.departmentRole) },
                )
            }
        }
        SecondaryButton("Open on the website", onOpenWebsite, icon = Icons.AutoMirrored.Filled.OpenInNew)
    }
}
