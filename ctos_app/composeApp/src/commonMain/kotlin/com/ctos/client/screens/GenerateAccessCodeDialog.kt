package com.ctos.client.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ctos.client.data.FormState
import com.ctos.client.screens.widgets.AppDialog
import com.ctos.client.screens.widgets.DialogCard
import com.ctos.client.screens.widgets.PrimaryButton
import com.ctos.client.screens.widgets.SecondaryButton
import com.ctos.client.theme.CtosColors
import com.ctos.client.theme.CtosShapes
import com.ctos.client.theme.CtosText

/**
 * Replaces a department's access code, right from the app. Asks first (the old code stops
 * working straight away, though existing members keep their access), then shows the new code
 * once, so it can be copied and shared β€” matching the website's own "Generate new code" flow.
 */
@Composable
fun GenerateAccessCodeDialog(
    state: FormState,
    newCode: String?,
    onGenerate: () -> Unit,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AppDialog(
        title = generateAccessCodeTitle(newCode),
        onDismiss = onDismiss,
        icon = Icons.Default.Sync,
        description = generateAccessCodeDescription(newCode),
    ) {
        GenerateAccessCodeContent(state, newCode, onGenerate, onCopy, onDismiss)
    }
}

/** The card's own content, without the popup window around it (so a screenshot test can draw it off-screen). */
@Composable
fun GenerateAccessCodeCard(
    state: FormState,
    newCode: String?,
    onGenerate: () -> Unit,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    DialogCard(
        title = generateAccessCodeTitle(newCode),
        onDismiss = onDismiss,
        icon = Icons.Default.Sync,
        description = generateAccessCodeDescription(newCode),
    ) {
        GenerateAccessCodeContent(state, newCode, onGenerate, onCopy, onDismiss)
    }
}

private fun generateAccessCodeTitle(newCode: String?) = if (newCode == null) "Generate a new access code" else "New access code"

private fun generateAccessCodeDescription(newCode: String?) = if (newCode == null) {
    "The current code stops working straight away, so anyone you gave it to will need the new one. People who already joined keep their access."
} else {
    null
}

@Composable
private fun GenerateAccessCodeContent(
    state: FormState,
    newCode: String?,
    onGenerate: () -> Unit,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (newCode == null) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state is FormState.Failed) {
                Text(
                    state.message,
                    style = CtosText.Small,
                    color = CtosColors.Danger,
                    modifier = Modifier.fillMaxWidth().background(CtosColors.DangerSoft, CtosShapes.Control).padding(12.dp),
                )
            }
            PrimaryButton("Generate new code", onGenerate, modifier = Modifier.fillMaxWidth(), loading = state is FormState.Working, icon = Icons.Default.Sync)
            SecondaryButton("Cancel", onDismiss, modifier = Modifier.fillMaxWidth())
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().background(CtosColors.SurfaceAlt, CtosShapes.Control).padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    newCode,
                    style = CtosText.Mono.copy(fontSize = CtosText.SectionTitle.fontSize, letterSpacing = CtosText.Mono.letterSpacing),
                    color = CtosColors.Text,
                )
            }
            PrimaryButton("Copy code", { onCopy(newCode) }, modifier = Modifier.fillMaxWidth())
            SecondaryButton("Done", onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}
