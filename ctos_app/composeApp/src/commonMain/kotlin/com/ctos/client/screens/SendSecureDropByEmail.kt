package com.ctos.client.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ctos.client.data.FormState
import com.ctos.client.data.PickedFile
import com.ctos.client.screens.widgets.AppCard
import com.ctos.client.screens.widgets.AppTextField
import com.ctos.client.screens.widgets.Page
import com.ctos.client.screens.widgets.PageTitle
import com.ctos.client.screens.widgets.PrimaryButton
import com.ctos.client.screens.widgets.SecondaryButton
import com.ctos.client.theme.CtosColors
import com.ctos.client.theme.CtosShapes
import com.ctos.client.theme.CtosText

/**
 * Sending one file, encrypted, directly by email — not scoped to any department (docs/secure-drop.md).
 * Whoever the address belongs to decides how: an account with its own key gets it end-to-end;
 * anyone else gets a one-time password instead, emailed to them along with an invitation to
 * create an account.
 */
@Composable
fun SendSecureDropByEmailContent(
    state: FormState,
    onChooseFile: () -> Unit,
    pickedFile: PickedFile?,
    onSend: (email: String, fileName: String) -> Unit,
    onEdit: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    val working = state is FormState.Working

    Page {
        PageTitle("Send, encrypted", "Directly to an email address, not scoped to any department.")

        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                AppTextField(email, { email = it; onEdit() }, "Their email")
                Text(
                    "Already has a Secure Drop key? It goes straight to them, end-to-end. Otherwise, it is protected " +
                        "with a one-time password instead, emailed to them along with an invitation to create an account.",
                    style = CtosText.Caption,
                    color = CtosColors.TextSubtle,
                )

                Text("File", style = CtosText.SmallStrong, color = CtosColors.Text)
                SecondaryButton(pickedFile?.name ?: "Choose a file", onChooseFile, icon = Icons.Default.AttachFile)

                if (state is FormState.Failed) {
                    Text(
                        state.message,
                        style = CtosText.Small,
                        color = CtosColors.Danger,
                        modifier = Modifier.fillMaxWidth().background(CtosColors.DangerSoft, CtosShapes.Control).padding(12.dp),
                    )
                }

                PrimaryButton(
                    "Encrypt and send",
                    onClick = { pickedFile?.let { onSend(email, it.name) } },
                    modifier = Modifier.fillMaxWidth(),
                    loading = working,
                    enabled = pickedFile != null,
                    icon = Icons.Default.Lock,
                )
            }
        }
    }
}
