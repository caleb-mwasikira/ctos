package com.ctos.client.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ctos.client.data.FormState
import com.ctos.client.data.PickedFile
import com.ctos.client.network.SecureDropRecipientOption
import com.ctos.client.screens.widgets.AppCard
import com.ctos.client.screens.widgets.EmptyState
import com.ctos.client.screens.widgets.Page
import com.ctos.client.screens.widgets.PageTitle
import com.ctos.client.screens.widgets.PrimaryButton
import com.ctos.client.screens.widgets.SecondaryButton
import com.ctos.client.theme.CtosColors
import com.ctos.client.theme.CtosShapes
import com.ctos.client.theme.CtosText

/** The form to send one file, encrypted, to the members of a department who have a Secure Drop key. */
@Composable
fun SendSecureDropContent(
    departmentName: String,
    recipients: List<SecureDropRecipientOption>,
    myUserId: Long,
    state: FormState,
    onChooseFile: () -> Unit,
    pickedFile: PickedFile?,
    onSend: (fileName: String, recipientIds: Set<Long>) -> Unit,
    onEdit: () -> Unit,
) {
    var selected by remember(recipients) { mutableStateOf(setOf(myUserId)) }
    val working = state is FormState.Working

    Page {
        PageTitle("Send, encrypted", "To members of $departmentName who have a Secure Drop key.")

        if (recipients.isEmpty()) {
            EmptyState(Icons.Default.Lock, "Nobody here has a Secure Drop key yet", "Ask the other members of $departmentName to set one up, then come back.")
        } else {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("File", style = CtosText.SmallStrong, color = CtosColors.Text)
                    SecondaryButton(pickedFile?.name ?: "Choose a file", onChooseFile, icon = Icons.Default.AttachFile)

                    Text("Send to", style = CtosText.SmallStrong, color = CtosColors.Text)
                    Column {
                        recipients.forEach { recipient ->
                            val isMe = recipient.id == myUserId
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Checkbox(
                                    checked = recipient.id in selected,
                                    onCheckedChange = { checked -> selected = if (checked) selected + recipient.id else selected - recipient.id; onEdit() },
                                    enabled = !isMe,
                                    colors = CheckboxDefaults.colors(checkedColor = CtosColors.Primary),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(recipient.name + if (isMe) " (you)" else "", style = CtosText.Small, color = CtosColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(recipient.fingerprint.take(16) + "…", style = CtosText.Caption, color = CtosColors.TextSubtle, maxLines = 1)
                                }
                            }
                        }
                    }

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
                        onClick = { pickedFile?.let { onSend(it.name, selected) } },
                        modifier = Modifier.fillMaxWidth(),
                        loading = working,
                        enabled = pickedFile != null,
                        icon = Icons.Default.Lock,
                    )
                }
            }
        }
    }
}
