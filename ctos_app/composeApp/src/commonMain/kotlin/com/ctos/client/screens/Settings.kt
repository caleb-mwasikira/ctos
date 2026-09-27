package com.ctos.client.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ctos.client.screens.layouts.Avatar
import com.ctos.client.screens.widgets.AppCard
import com.ctos.client.screens.widgets.DangerButton
import com.ctos.client.screens.widgets.Page
import com.ctos.client.screens.widgets.PageTitle
import com.ctos.client.screens.widgets.SecondaryButton
import com.ctos.client.screens.widgets.SectionTitle
import com.ctos.client.theme.CtosColors
import com.ctos.client.theme.CtosText

@Composable
fun SettingsContent(
    userName: String,
    userEmail: String,
    deviceName: String,
    serverUrl: String,
    syncRoot: String,
    version: String,
    canOpenFolder: Boolean,
    onOpenFolder: () -> Unit,
    onChangePassword: () -> Unit,
    onLogout: () -> Unit,
) {
    Page {
        PageTitle("Settings")

        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Avatar(userName, size = 52.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(userName, style = CtosText.SectionTitle, color = CtosColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(userEmail, style = CtosText.Body, color = CtosColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        SecondaryButton("Change password", onChangePassword, icon = Icons.Default.Lock)

        SectionTitle("This device")
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Setting("Device", deviceName)
            HorizontalDivider(color = CtosColors.Border)
            Setting("Files are kept in", syncRoot)
            HorizontalDivider(color = CtosColors.Border)
            Setting("Server", serverUrl)
            HorizontalDivider(color = CtosColors.Border)
            Setting("App version", version)
        }
        if (canOpenFolder) SecondaryButton("Open the files folder", onOpenFolder, icon = Icons.Default.FolderOpen)

        DangerButton("Log out", onLogout, icon = Icons.AutoMirrored.Filled.Logout)
    }
}

@Composable
private fun Setting(label: String, value: String) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = CtosText.Caption, color = CtosColors.TextSubtle)
        Text(value, style = CtosText.Body, color = CtosColors.Text)
    }
}
