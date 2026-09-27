package com.ctos.client.screens.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ctos.client.theme.CtosColors
import com.ctos.client.theme.CtosShapes
import com.ctos.client.theme.CtosText

/** The card of a popup: a title, an optional explanation, a close button, and the content. */
@Composable
fun DialogCard(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier.widthIn(min = 320.dp, max = 460.dp).fillMaxWidth(), shape = CtosShapes.Card, color = CtosColors.Surface, shadowElevation = 8.dp) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (icon != null) IconTile(icon, size = 40.dp, filled = true)
                Text(title, style = CtosText.SectionTitle, color = CtosColors.Text, modifier = Modifier.weight(1f))
                IconAction(Icons.Default.Close, "Close", onDismiss)
            }
            if (description != null) {
                Spacer(Modifier.height(12.dp))
                Text(description, style = CtosText.Small, color = CtosColors.TextMuted)
            }
            Spacer(Modifier.height(20.dp))
            content()
        }
    }
}

/** A popup over the screen. Tapping outside it, or the close button, dismisses it. */
@Composable
fun AppDialog(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        DialogCard(title, onDismiss, modifier, icon, description, content)
    }
}
