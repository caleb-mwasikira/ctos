package com.ctos.client.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ctos.client.data.DepartmentsState
import com.ctos.client.data.searchDepartments
import com.ctos.client.network.Department
import com.ctos.client.network.departmentRole
import com.ctos.client.screens.widgets.EmptyState
import com.ctos.client.screens.widgets.Page
import com.ctos.client.screens.widgets.PageTitle
import com.ctos.client.screens.widgets.PrimaryButton
import com.ctos.client.screens.widgets.ResourceCard
import com.ctos.client.screens.widgets.RoleBadge
import com.ctos.client.theme.CtosColors
import com.ctos.client.theme.CtosText

/**
 * Every department the user has joined, across all organizations, sorted by name. Searching by
 * name across everything (departments included) is the search popup's job now (the top bar's
 * search icon); this screen is just the plain browsable list.
 */
@Composable
fun DepartmentsContent(
    state: DepartmentsState,
    onOpenDepartment: (Department) -> Unit,
    onRetry: () -> Unit,
) {
    Page {
        PageTitle("Departments", "Every department you have joined, across all organizations.")

        when (state) {
            is DepartmentsState.Loading -> Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CtosColors.Primary)
            }
            is DepartmentsState.Failed -> EmptyState(
                icon = Icons.Default.CloudOff,
                title = "Could not load your departments",
                text = state.message,
                action = { PrimaryButton("Try again", onRetry) },
            )
            is DepartmentsState.Loaded -> {
                val departments = searchDepartments(state.departments, "")
                if (departments.isEmpty()) {
                    EmptyState(
                        Icons.Default.Folder,
                        "No departments",
                        "Departments you join with an access code appear here.",
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "${departments.size} ${if (departments.size == 1) "department" else "departments"}",
                            style = CtosText.Small,
                            color = CtosColors.TextMuted,
                        )
                        departments.forEach { department ->
                            ResourceCard(
                                icon = Icons.Default.Folder,
                                title = department.name,
                                subtitle = department.organization.name,
                                onClick = { onOpenDepartment(department) },
                                badge = { RoleBadge(department.departmentRole) },
                            )
                        }
                    }
                }
            }
        }
    }
}
