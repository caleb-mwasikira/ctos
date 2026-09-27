package com.ctos.client.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ctos.client.data.DepartmentsState
import com.ctos.client.data.OrganizationSummary
import com.ctos.client.screens.widgets.ActionCard
import com.ctos.client.screens.widgets.CardGrid
import com.ctos.client.screens.widgets.EmptyState
import com.ctos.client.screens.widgets.Page
import com.ctos.client.screens.widgets.PageTitle
import com.ctos.client.screens.widgets.PrimaryButton
import com.ctos.client.screens.widgets.ResourceCard
import com.ctos.client.screens.widgets.RoleBadge
import com.ctos.client.screens.widgets.SectionTitle
import com.ctos.client.theme.CtosColors

/**
 * The home screen: the two things a person can do (start an organization, join a department) and
 * the organizations they belong to. Departments are one step further in, like on the website.
 */
@Composable
fun DashboardContent(
    firstName: String,
    state: DepartmentsState,
    organizations: List<OrganizationSummary>,
    onCreateOrganization: () -> Unit,
    onJoinDepartment: () -> Unit,
    onOpenOrganization: (OrganizationSummary) -> Unit,
    onRetry: () -> Unit,
) {
    Page {
        PageTitle("Welcome back, $firstName", "Start a new workspace or join your team's.")

        CardGrid(items = listOf(true, false), minCardWidth = 360.dp) { create ->
            if (create) {
                ActionCard(
                    icon = Icons.Default.Business,
                    title = "Create an organization",
                    text = "Set up a workspace for your company. Add a department for each team on the website once it is created.",
                    onClick = onCreateOrganization,
                )
            } else {
                ActionCard(
                    icon = Icons.Default.VpnKey,
                    title = "Join a department",
                    text = "Already have an access code? Enter it to open your team's files.",
                    onClick = onJoinDepartment,
                )
            }
        }

        SectionTitle("Your organizations")
        when (state) {
            is DepartmentsState.Loading -> Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CtosColors.Primary)
            }
            is DepartmentsState.Failed -> EmptyState(
                icon = Icons.Default.CloudOff,
                title = "Could not load your organizations",
                text = state.message,
                action = { PrimaryButton("Try again", onRetry) },
            )
            is DepartmentsState.Loaded -> if (organizations.isEmpty()) {
                EmptyState(
                    icon = Icons.Default.Business,
                    title = "No organizations",
                    text = "Create an organization, or join a department with an access code, and it will appear here.",
                )
            } else {
                CardGrid(organizations) { summary ->
                    val count = summary.departments.size
                    ResourceCard(
                        icon = Icons.Default.Business,
                        title = summary.organization.name,
                        subtitle = if (count == 0) "No departments yet" else "$count ${if (count == 1) "department" else "departments"} joined",
                        onClick = { onOpenOrganization(summary) },
                        badge = summary.highestRole?.let { role -> { RoleBadge(role) } },
                    )
                }
            }
        }
    }
}
