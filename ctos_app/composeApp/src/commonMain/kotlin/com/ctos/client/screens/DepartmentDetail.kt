package com.ctos.client.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ctos.client.data.DetailsState
import com.ctos.client.data.DepartmentSyncState
import com.ctos.client.data.ExplorerRow
import com.ctos.client.data.FormState
import com.ctos.client.data.SyncStatus
import com.ctos.client.data.breadcrumbs
import com.ctos.client.data.explorerRows
import com.ctos.client.network.Department
import com.ctos.client.network.DepartmentDetails
import com.ctos.client.network.DepartmentMember
import com.ctos.client.network.departmentRole
import com.ctos.client.screens.widgets.AppCard
import com.ctos.client.screens.widgets.Badge
import com.ctos.client.screens.widgets.BadgeTone
import com.ctos.client.screens.widgets.DangerButton
import com.ctos.client.screens.widgets.EmptyState
import com.ctos.client.screens.widgets.IconAction
import com.ctos.client.screens.widgets.IconTile
import com.ctos.client.screens.widgets.LinkButton
import com.ctos.client.screens.widgets.Page
import com.ctos.client.screens.widgets.PageTitle
import com.ctos.client.screens.widgets.PrimaryButton
import com.ctos.client.screens.widgets.RoleBadge
import com.ctos.client.screens.widgets.SecondaryButton
import com.ctos.client.theme.CtosColors
import com.ctos.client.theme.CtosShapes
import com.ctos.client.theme.CtosText

private const val MAX_ROWS_SHOWN = 300
private val SideBySideWidth = 860.dp

/** Below this width the explorer drops its Size column, like the website on a small screen. */
private val CompactWidth = 560.dp

/**
 * One department, laid out like its page on the website: the file explorer on the left, and on the right
 * the members, the access code and pending invitations (for people who manage the department), and a
 * button to leave. The explorer shows the department's folder on this device, which the sync keeps equal
 * to the server: a folder added here is uploaded, and what others add arrives here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DepartmentContent(
    department: Department?,
    details: DetailsState,
    sync: DepartmentSyncState?,
    explorerPath: String,
    nowMillis: Long,
    addingFolder: Boolean,
    canOpenFolder: Boolean,
    onOpenExplorerFolder: (String) -> Unit,
    onAddFolder: () -> Unit,
    onOpenFolder: () -> Unit,
    onOpenFile: (String) -> Unit,
    onSyncNow: () -> Unit,
    onRetryDetails: () -> Unit,
    onOpenMembers: () -> Unit,
    onOpenAccessCode: () -> Unit,
    onOpenInvitations: () -> Unit,
    onOpenLeave: () -> Unit,
) {
    Page {
        if (department == null) {
            EmptyState(Icons.Default.Folder, "Department not found", "It may have been removed, or you no longer have access to it.")
            return@Page
        }

        val loaded = (details as? DetailsState.Loaded)?.details
        val canManage = loaded?.canManage ?: department.departmentRole.canManage
        val memberCount = loaded?.memberCount

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val wide = maxWidth >= SideBySideWidth
            val compact = maxWidth < CompactWidth

            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                // The header: name, organization and member count, and what can be done with the department.
                val title: @Composable (Modifier) -> Unit = { modifier ->
                    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PageTitle(department.name)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Business, contentDescription = null, tint = CtosColors.TextMuted, modifier = Modifier.size(16.dp))
                            Text(
                                buildString {
                                    append(department.organization.name)
                                    if (memberCount != null) append("  ·  $memberCount ${if (memberCount == 1) "member" else "members"}")
                                },
                                style = CtosText.Small,
                                color = CtosColors.TextMuted,
                            )
                        }
                    }
                }
                val actions: @Composable () -> Unit = {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PrimaryButton(text = "Add folder", onClick = onAddFolder, loading = addingFolder, icon = Icons.Default.CreateNewFolder)
                        if (canManage) {
                            SecondaryButton("Share", onOpenInvitations, icon = Icons.Default.Mail)
                        }
                    }
                }
                if (wide) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        title(Modifier.weight(1f))
                        actions()
                    }
                } else {
                    title(Modifier.fillMaxWidth())
                    actions()
                }

                val explorer: @Composable (Modifier) -> Unit = { modifier ->
                    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Explorer(department, sync, explorerPath, nowMillis, addingFolder, canOpenFolder, compact = compact, onOpenExplorerFolder, onAddFolder, onOpenFolder, onOpenFile, onSyncNow)
                    }
                }
                val side: @Composable (Modifier) -> Unit = { modifier ->
                    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        SidePanel(details, canManage, onRetryDetails, onOpenMembers, onOpenAccessCode, onOpenInvitations, onOpenLeave)
                    }
                }

                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
                        explorer(Modifier.weight(2f))
                        side(Modifier.weight(1f))
                    }
                } else {
                    explorer(Modifier.fillMaxWidth())
                    side(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

// ── the file explorer ──────────────────────────────────────────────────────

@Composable
private fun Explorer(
    department: Department,
    sync: DepartmentSyncState?,
    path: String,
    nowMillis: Long,
    addingFolder: Boolean,
    canOpenFolder: Boolean,
    compact: Boolean,
    onOpenFolder: (String) -> Unit,
    onAddFolder: () -> Unit,
    onOpenLocalFolder: () -> Unit,
    onOpenFile: (String) -> Unit,
    onSyncNow: () -> Unit,
) {
    val rows = if (sync == null) emptyList() else explorerRows(sync.folder, path, sync.pending)

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Breadcrumbs(department.name, path, sync?.status, nowMillis, onOpenFolder)
        HorizontalDivider(color = CtosColors.Border)

        if (rows.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.size(56.dp).background(CtosColors.SurfaceAlt, CtosShapes.Card),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Cloud, contentDescription = null, tint = CtosColors.TextSubtle, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text(if (path.isEmpty()) "No files yet" else "This folder is empty", style = CtosText.SectionTitle, color = CtosColors.Text)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Add a folder and it is uploaded and shared with everyone in this department. What they add appears here too.",
                    style = CtosText.Small,
                    color = CtosColors.TextMuted,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Spacer(Modifier.height(20.dp))
                SecondaryButton("Add folder", onAddFolder, enabled = !addingFolder, icon = Icons.Default.CreateNewFolder)
            }
        } else {
            ExplorerHeader(compact)
            HorizontalDivider(color = CtosColors.Border)
            rows.take(MAX_ROWS_SHOWN).forEachIndexed { index, row ->
                if (index > 0) HorizontalDivider(color = CtosColors.Border)
                ExplorerLine(row, nowMillis, compact, onOpenFolder, onOpenFile)
            }
            if (rows.size > MAX_ROWS_SHOWN) {
                HorizontalDivider(color = CtosColors.Border)
                Text("and ${rows.size - MAX_ROWS_SHOWN} more", style = CtosText.Small, color = CtosColors.TextMuted, modifier = Modifier.padding(16.dp))
            }
        }
    }

    if (sync != null) {
        SyncDetails(sync, canOpenFolder, onOpenLocalFolder, onSyncNow)
    }
}

@Composable
private fun Breadcrumbs(departmentName: String, path: String, status: SyncStatus?, nowMillis: Long, onOpenFolder: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Default.Folder, contentDescription = null, tint = CtosColors.Primary, modifier = Modifier.size(18.dp))
        val steps = breadcrumbs(path)
        Crumb(departmentName, isLast = steps.isEmpty()) { onOpenFolder("") }
        steps.forEachIndexed { index, (label, target) ->
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = CtosColors.TextSubtle, modifier = Modifier.size(16.dp))
            Crumb(label, isLast = index == steps.lastIndex) { onOpenFolder(target) }
        }
        Spacer(Modifier.weight(1f))
        if (status != null) SyncBadge(status, nowMillis)
    }
}

@Composable
private fun Crumb(label: String, isLast: Boolean, onClick: () -> Unit) {
    if (isLast) {
        Text(label, style = CtosText.SmallStrong, color = CtosColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    } else {
        Text(label, style = CtosText.Small, color = CtosColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.clickable(onClick = onClick))
    }
}

@Composable
private fun SyncBadge(status: SyncStatus, nowMillis: Long) {
    when (status.phase) {
        SyncStatus.Phase.Starting, SyncStatus.Phase.Syncing -> Badge("Syncing…", BadgeTone.Brand)
        SyncStatus.Phase.Offline -> Badge("Not connected", BadgeTone.Warning)
        SyncStatus.Phase.Synced ->
            if (status.problems.isEmpty()) Badge("Up to date", BadgeTone.Success) else Badge("${status.problems.size} not synced", BadgeTone.Warning)
    }
}

@Composable
private fun ExplorerHeader(compact: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Name", style = CtosText.Caption, color = CtosColors.TextSubtle, modifier = Modifier.weight(1f))
        if (!compact) Text("Size", style = CtosText.Caption, color = CtosColors.TextSubtle, modifier = Modifier.width(72.dp))
        Text("Modified", style = CtosText.Caption, color = CtosColors.TextSubtle, modifier = Modifier.width(120.dp))
    }
}

@Composable
private fun ExplorerLine(row: ExplorerRow, nowMillis: Long, compact: Boolean, onOpenFolder: (String) -> Unit, onOpenFile: (String) -> Unit) {
    val modifier = Modifier.fillMaxWidth().clickable { if (row.isFolder) onOpenFolder(row.path) else onOpenFile(row.path) }
    Row(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                if (row.isFolder) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                tint = if (row.isFolder) CtosColors.Primary else CtosColors.TextSubtle,
                modifier = Modifier.size(20.dp),
            )
            Text(
                row.name,
                style = CtosText.Small.copy(fontWeight = FontWeight.Medium),
                color = CtosColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (row.waitingToUpload) {
                Icon(Icons.Default.CloudUpload, contentDescription = "Waiting to upload", tint = CtosColors.Primary, modifier = Modifier.size(16.dp))
            }
        }
        if (!compact) Text(if (row.isFolder) "—" else formatBytes(row.size), style = CtosText.Small, color = CtosColors.TextMuted, modifier = Modifier.width(72.dp))
        Text(row.modifiedAt?.let { formatTime(it, nowMillis) } ?: "—", style = CtosText.Small, color = CtosColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(120.dp))
    }
}

/** Where the files are kept on this device, how the sync is going, and anything that could not be synced. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SyncDetails(sync: DepartmentSyncState, canOpenFolder: Boolean, onOpenFolder: () -> Unit, onSyncNow: () -> Unit) {
    val status = sync.status
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            LinkButton("Sync now", onSyncNow)
            if (canOpenFolder) LinkButton("Open folder on this computer", onOpenFolder)
        }
        Text(
            "On this device: ${sync.folder.path}",
            style = CtosText.Caption,
            color = CtosColors.TextSubtle,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
        )
        if (status.phase == SyncStatus.Phase.Offline && status.error != null) {
            Text("${status.error} Your changes are kept and sent when the connection is back.", style = CtosText.Small, color = CtosColors.Warning)
        }
        if (sync.folder.error != null) {
            Text(sync.folder.error, style = CtosText.Small, color = CtosColors.Warning)
        }
        if (status.problems.isNotEmpty()) {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Some files could not be synced", style = CtosText.SmallStrong, color = CtosColors.Warning)
                    status.problems.take(5).forEach { Text(it, style = CtosText.Caption, color = CtosColors.TextMuted) }
                    if (status.problems.size > 5) Text("and ${status.problems.size - 5} more", style = CtosText.Caption, color = CtosColors.TextSubtle)
                }
            }
        }
    }
}

// ── the side panel ─────────────────────────────────────────────────────────

@Composable
private fun SidePanel(
    details: DetailsState,
    canManage: Boolean,
    onRetry: () -> Unit,
    onOpenMembers: () -> Unit,
    onOpenAccessCode: () -> Unit,
    onOpenInvitations: () -> Unit,
    onOpenLeave: () -> Unit,
) {
    val loaded = (details as? DetailsState.Loaded)?.details
    val pendingCount = loaded?.invitations?.size

    MembersCard(details, canManage, onRetry, onOpenMembers)

    if (canManage) {
        LinkCard(Icons.Default.VpnKey, "Access code", "View and generate a new one", onOpenAccessCode)
        LinkCard(Icons.Default.Mail, "Invitations", if (pendingCount != null) "$pendingCount pending" else "Loading…", onOpenInvitations)
    }

    DangerButton("Leave department", onOpenLeave, modifier = Modifier.fillMaxWidth())
}

/** A summary that opens its own page, like the website's own resource cards. */
@Composable
private fun LinkCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(modifier = Modifier.size(40.dp).background(CtosColors.Primary100, CtosShapes.Control), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = CtosColors.Primary, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = CtosText.BodyStrong, color = CtosColors.Text)
                Text(subtitle, style = CtosText.Small, color = CtosColors.TextMuted)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = CtosColors.TextSubtle, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun MembersCard(details: DetailsState, canManage: Boolean, onRetry: () -> Unit, onViewAll: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth(), onClick = if (canManage) onViewAll else null) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Members", style = CtosText.BodyStrong, color = CtosColors.Text, modifier = Modifier.weight(1f))
                if (canManage) {
                    Text("View all", style = CtosText.SmallStrong, color = CtosColors.Primary)
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = CtosColors.Primary, modifier = Modifier.size(16.dp))
                }
            }
            when (details) {
                DetailsState.Loading -> Text("Loading the members…", style = CtosText.Small, color = CtosColors.TextMuted)
                is DetailsState.Failed -> {
                    Text(details.message, style = CtosText.Small, color = CtosColors.Warning)
                    LinkButton("Try again", onRetry)
                }
                is DetailsState.Loaded -> details.details.members.forEach { MemberLine(it) }
            }
        }
    }
}

@Composable
private fun MemberLine(member: DepartmentMember) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(modifier = Modifier.size(36.dp).background(CtosColors.Primary100, CtosShapes.Pill), contentAlignment = Alignment.Center) {
            Text(member.name.take(1).uppercase(), style = CtosText.SmallStrong, color = CtosColors.PrimaryHover)
        }
        Text(member.name, style = CtosText.Small.copy(fontWeight = FontWeight.Medium), color = CtosColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        RoleBadge(member.departmentRole)
    }
}

@Composable
private fun AccessCodeCard(details: DepartmentDetails?, onCopy: (String) -> Unit, onGenerate: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.size(40.dp).background(CtosColors.WarningSoft, CtosShapes.Control), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.VpnKey, contentDescription = null, tint = CtosColors.Warning, modifier = Modifier.size(20.dp))
                }
                Text("Access code", style = CtosText.BodyStrong, color = CtosColors.Text)
            }

            val code = details?.accessCode
            when {
                details == null -> Text("Loading…", style = CtosText.Small, color = CtosColors.TextMuted)
                code != null -> {
                    Surface(
                        onClick = { onCopy(code) },
                        shape = CtosShapes.Control,
                        color = CtosColors.SurfaceAlt,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(code, style = CtosText.Mono.copy(fontSize = CtosText.SectionTitle.fontSize, letterSpacing = CtosText.Mono.letterSpacing), color = CtosColors.Text, modifier = Modifier.weight(1f))
                            Text("Copy", style = CtosText.Caption, color = CtosColors.Primary)
                        }
                    }
                    Text("People join from their dashboard with this code alone.", style = CtosText.Small, color = CtosColors.TextMuted)
                }
                else -> Text(
                    "This code was made before codes could be copied. Generate a new one on the website to see it here.",
                    style = CtosText.Small,
                    color = CtosColors.TextMuted,
                )
            }

            HorizontalDivider(color = CtosColors.Border)
            SecondaryButton("Generate new code", onGenerate, modifier = Modifier.fillMaxWidth(), icon = Icons.Default.Sync)
        }
    }
}

@Composable
private fun InvitationsCard(details: DepartmentDetails, onWithdraw: (Long) -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Pending invitations", style = CtosText.BodyStrong, color = CtosColors.Text)
            Text("They get access once they sign up and confirm their email.", style = CtosText.Small, color = CtosColors.TextMuted)
            details.invitations.forEachIndexed { index, invitation ->
                if (index > 0) HorizontalDivider(color = CtosColors.Border)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(invitation.email, style = CtosText.Small, color = CtosColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    IconAction(Icons.Default.Close, "Withdraw the invitation for ${invitation.email}", { onWithdraw(invitation.id) })
                }
            }
        }
    }
}

// ── the department's own pages, each reached from a card in the side panel above ───────────

@Composable
fun DepartmentMembersContent(department: Department?, details: DetailsState, onRetry: () -> Unit) {
    Page {
        if (department == null) {
            EmptyState(Icons.Default.Folder, "Department not found", "It may have been removed, or you no longer have access to it.")
            return@Page
        }
        PageTitle("Members", "Who has access to ${department.name}.")
        when (details) {
            DetailsState.Loading -> Text("Loading the members…", style = CtosText.Small, color = CtosColors.TextMuted)
            is DetailsState.Failed -> EmptyState(Icons.Default.Folder, "Could not load the members", details.message) { LinkButton("Try again", onRetry) }
            is DetailsState.Loaded -> AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    details.details.members.forEach { MemberLine(it) }
                }
            }
        }
    }
}

@Composable
fun DepartmentAccessCodeContent(department: Department?, details: DetailsState, onCopy: (String) -> Unit, onGenerate: () -> Unit) {
    Page {
        if (department == null) {
            EmptyState(Icons.Default.Folder, "Department not found", "It may have been removed, or you no longer have access to it.")
            return@Page
        }
        PageTitle("Access code", "People join ${department.name} from the app with this code alone.")
        AccessCodeCard((details as? DetailsState.Loaded)?.details, onCopy, onGenerate)
    }
}

@Composable
fun DepartmentInvitationsContent(department: Department?, details: DetailsState, onWithdraw: (Long) -> Unit, onInvite: () -> Unit) {
    Page {
        if (department == null) {
            EmptyState(Icons.Default.Folder, "Department not found", "It may have been removed, or you no longer have access to it.")
            return@Page
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PageTitle("Invitations", "They get access once they sign up and confirm their email.")
            Spacer(Modifier.weight(1f))
            PrimaryButton("Invite by email", onInvite, icon = Icons.Default.Mail)
        }
        val loaded = (details as? DetailsState.Loaded)?.details
        when {
            details == DetailsState.Loading -> Text("Loading…", style = CtosText.Small, color = CtosColors.TextMuted)
            loaded == null -> Text((details as DetailsState.Failed).message, style = CtosText.Small, color = CtosColors.Warning)
            loaded.invitations.isEmpty() -> EmptyState(Icons.Default.Mail, "No pending invitations", "Invite someone by email and it will show up here until they join.") {
                SecondaryButton("Invite by email", onInvite, icon = Icons.Default.Mail)
            }
            else -> InvitationsCard(loaded, onWithdraw)
        }
    }
}

@Composable
fun DepartmentLeaveContent(department: Department?, leaveState: FormState, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Page {
        if (department == null) {
            EmptyState(Icons.Default.Folder, "Department not found", "It may have been removed, or you no longer have access to it.")
            return@Page
        }
        PageTitle("Leave ${department.name}")
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.size(40.dp).background(CtosColors.DangerSoft, CtosShapes.Control), contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = CtosColors.Danger, modifier = Modifier.size(20.dp))
                    }
                    Text("Are you sure?", style = CtosText.BodyStrong, color = CtosColors.Text)
                }
                Text(
                    "You will lose access to its files. Someone who can manage ${department.name} can give it back to you with the access code.",
                    style = CtosText.Small,
                    color = CtosColors.TextMuted,
                )
                if (leaveState is FormState.Failed) {
                    Text(leaveState.message, style = CtosText.Small, color = CtosColors.Danger)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SecondaryButton("Cancel", onCancel, modifier = Modifier.weight(1f))
                    DangerButton(if (leaveState is FormState.Working) "Leaving…" else "Leave department", onConfirm, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
