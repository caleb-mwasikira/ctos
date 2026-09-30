package com.ctos.client.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ctos.client.data.FormState
import com.ctos.client.network.SecureDropSummary
import com.ctos.client.screens.widgets.AppDialog
import com.ctos.client.screens.widgets.AppTextField
import com.ctos.client.screens.widgets.PrimaryButton
import com.ctos.client.theme.CtosColors
import com.ctos.client.theme.CtosShapes
import com.ctos.client.theme.CtosText

/**
 * The passphrase is asked for right here, used once in memory to decrypt the drop, and never
 * stored (see docs/secure-drop.md).
 */
@Composable
fun OpenSecureDropDialog(
    drop: SecureDropSummary,
    state: FormState,
    onConfirm: (passphrase: String) -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppDialog(title = "Open \"${drop.name}\"", onDismiss = onDismiss, icon = Icons.Default.Lock) {
        OpenSecureDropForm(state, onConfirm, onEdit)
    }
}

/** The form itself, split out from [OpenSecureDropDialog] so it can be drawn without a real platform window (screenshot tests). */
@Composable
fun OpenSecureDropForm(
    state: FormState,
    onConfirm: (passphrase: String) -> Unit,
    onEdit: () -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    val working = state is FormState.Working

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AppTextField(
            value = passphrase,
            onValueChange = { passphrase = it; onEdit() },
            label = "Your passphrase",
            isPassword = true,
            imeAction = ImeAction.Done,
            onImeAction = { onConfirm(passphrase) },
            autofocus = true,
        )
        if (state is FormState.Failed) {
            Text(
                state.message,
                style = CtosText.Small,
                color = CtosColors.Danger,
                modifier = Modifier.fillMaxWidth().background(CtosColors.DangerSoft, CtosShapes.Control).padding(12.dp),
            )
        }
        PrimaryButton("Decrypt and save", onClick = { onConfirm(passphrase) }, modifier = Modifier.fillMaxWidth(), loading = working, icon = Icons.Default.Lock)
    }
}
