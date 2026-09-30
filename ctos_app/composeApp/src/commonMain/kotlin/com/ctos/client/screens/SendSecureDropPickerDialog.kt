package com.ctos.client.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ctos.client.network.Department
import com.ctos.client.screens.widgets.AppDialog
import com.ctos.client.theme.CtosColors
import com.ctos.client.theme.CtosText

/** Where to send one, encrypted: a department's members, or straight to an email address (docs/secure-drop.md). */
@Composable
fun SendSecureDropPickerDialog(
    departments: List<Department>,
    onSend: (Department) -> Unit,
    onSendByEmail: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppDialog(title = "Send, encrypted", onDismiss = onDismiss, icon = Icons.Default.Lock) {
        SendSecureDropPickerForm(departments, onSend, onSendByEmail)
    }
}

/** Split out from [SendSecureDropPickerDialog] so it can be drawn without a real platform window (screenshot tests). */
@Composable
fun SendSecureDropPickerForm(
    departments: List<Department>,
    onSend: (Department) -> Unit,
    onSendByEmail: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (departments.isNotEmpty()) {
            Text("To a department", style = CtosText.SmallStrong, color = CtosColors.TextSubtle, modifier = Modifier.padding(bottom = 4.dp))
            departments.forEachIndexed { index, department ->
                if (index > 0) HorizontalDivider(color = CtosColors.Border)
                PickerRow(department.name, "Every member with a Secure Drop key", onClick = { onSend(department) })
            }
            HorizontalDivider(color = CtosColors.Border, modifier = Modifier.padding(vertical = 12.dp))
        }
        PickerRow("By email", "A one-time password if they don't have a key yet", icon = Icons.Default.Email, onClick = onSendByEmail)
    }
}

@Composable
private fun PickerRow(title: String, subtitle: String, onClick: () -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = CtosColors.Primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = CtosText.SmallStrong, color = CtosColors.Text)
            Text(subtitle, style = CtosText.Caption, color = CtosColors.TextSubtle)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = CtosColors.TextSubtle)
    }
}
