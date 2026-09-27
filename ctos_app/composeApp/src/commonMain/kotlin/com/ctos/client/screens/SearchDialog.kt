package com.ctos.client.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ctos.client.data.GlobalSearchResults
import com.ctos.client.data.SearchResult
import com.ctos.client.network.departmentRole
import com.ctos.client.screens.widgets.AppDialog
import com.ctos.client.screens.widgets.AppTextField
import com.ctos.client.screens.widgets.RoleBadge
import com.ctos.client.theme.CtosColors
import com.ctos.client.theme.CtosText

/**
 * Searches everything the app knows about at once: organizations, departments, and every synced
 * department's files and folders. Mirrors the website's own search popup, just wider: the website
 * only searches departments, but the app already has the rest on the device to search for free.
 */
@Composable
fun SearchDialog(
    query: String,
    results: GlobalSearchResults,
    onQueryChange: (String) -> Unit,
    onSelect: (SearchResult) -> Unit,
    onDismiss: () -> Unit,
) {
    AppDialog(title = "Search", onDismiss = onDismiss, icon = Icons.Default.Search) {
        SearchDialogContent(query, results, onQueryChange, onSelect)
    }
}

/** The dialog's own content, without the popup window around it (so a screenshot test can draw it off-screen). */
@Composable
fun SearchDialogContent(
    query: String,
    results: GlobalSearchResults,
    onQueryChange: (String) -> Unit,
    onSelect: (SearchResult) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AppTextField(
            value = query,
            onValueChange = onQueryChange,
            label = "Search",
            placeholder = "Organizations, departments, files, folders…",
            autofocus = true,
        )

        when {
            query.isBlank() -> Text(
                "Start typing to search everything you can see: your organizations, your departments, and the files and folders synced to this device.",
                style = CtosText.Small,
                color = CtosColors.TextMuted,
            )
            results.isEmpty -> Text("No matches for “$query”.", style = CtosText.Small, color = CtosColors.TextMuted)
            else -> Column(
                modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (results.organizations.isNotEmpty()) {
                    ResultGroup("Organizations") {
                        results.organizations.forEach { organization ->
                            ResultRow(
                                icon = Icons.Default.Business,
                                title = organization.organization.name,
                                subtitle = "Organization",
                                onClick = { onSelect(SearchResult.OrganizationHit(organization)) },
                            )
                        }
                    }
                }
                if (results.departments.isNotEmpty()) {
                    ResultGroup("Departments") {
                        results.departments.forEach { department ->
                            ResultRow(
                                icon = Icons.Default.Folder,
                                title = department.name,
                                subtitle = department.organization.name,
                                onClick = { onSelect(SearchResult.DepartmentHit(department)) },
                                badge = { RoleBadge(department.departmentRole) },
                            )
                        }
                    }
                }
                if (results.files.isNotEmpty()) {
                    ResultGroup("Files & folders") {
                        results.files.forEach { file ->
                            ResultRow(
                                icon = if (file.isFolder) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                                title = file.name,
                                subtitle = file.departmentName,
                                onClick = { onSelect(SearchResult.FileHit(file)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultGroup(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title.uppercase(), style = CtosText.Caption, color = CtosColors.TextSubtle, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        content()
    }
}

@Composable
private fun ResultRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    badge: (@Composable () -> Unit)? = null,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = CtosColors.TextSubtle, modifier = Modifier.size(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = CtosText.BodyStrong, color = CtosColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = CtosText.Caption, color = CtosColors.TextSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            badge?.invoke()
        }
        HorizontalDivider(color = CtosColors.Border)
    }
}
