package com.ctos.client.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.ctos.client.data.SecureDropKeyState
import com.ctos.client.network.Department
import com.ctos.client.network.SecureDropLists
import com.ctos.client.network.SecureDropSummary
import com.ctos.client.screens.widgets.AppCard
import com.ctos.client.screens.widgets.AppTextField
import com.ctos.client.screens.widgets.DangerButton
import com.ctos.client.screens.widgets.EmptyState
import com.ctos.client.screens.widgets.LinkButton
import com.ctos.client.screens.widgets.Page
import com.ctos.client.screens.widgets.PageTitle
import com.ctos.client.screens.widgets.PrimaryButton
import com.ctos.client.screens.widgets.SecondaryButton
import com.ctos.client.screens.widgets.SectionTitle
import com.ctos.client.theme.CtosColors
import com.ctos.client.theme.CtosShapes
import com.ctos.client.theme.CtosText

/**
 * Secure Drop (docs/secure-drop.md): files encrypted on this device before they ever leave it,
 * for one or more people to decrypt on theirs. This screen is the key pair's own setup (when
 * there is not one yet) or a summary of it, and everything sent or received.
 */
@Composable
fun SecureDropContent(
    keyState: SecureDropKeyState,
    keySetupState: FormState,
    /** Non-null once step 1 (below) has made or imported a key pair: step 2 (the passphrase) shows instead of step 1. */
    pendingKeyPairFingerprint: String?,
    lists: SecureDropLists,
    departments: List<Department>,
    onSetUpKeyPair: (importPublicPem: String?, importPrivatePem: String?) -> Unit,
    onRestartKeySetup: () -> Unit,
    onEncryptAndSaveKey: (passphrase: String, confirmation: String) -> Unit,
    onEditKeySetup: () -> Unit,
    onCopyKey: () -> Unit,
    onRemoveKey: () -> Unit,
    onSend: (Department) -> Unit,
    onSendByEmail: () -> Unit,
    onOpenDrop: (SecureDropSummary) -> Unit,
    onDeleteDrop: (SecureDropSummary) -> Unit,
) {
    Page {
        PageTitle(
            "Secure Drop",
            "Files encrypted on your own device before they leave it. Nobody but the people you pick — not even this server — can read them.",
        )

        when (keyState) {
            SecureDropKeyState.Loading -> Text("Loading…", style = CtosText.Small, color = CtosColors.TextMuted)
            SecureDropKeyState.None -> SecureDropKeySetupCard(
                keySetupState, pendingKeyPairFingerprint, onSetUpKeyPair, onRestartKeySetup, onEncryptAndSaveKey, onEditKeySetup,
            )
            is SecureDropKeyState.Ready -> {
                YourKeyCard(keyState, onCopyKey, onRemoveKey)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("Send one, encrypted")
                    SecondaryButton("By email", onSendByEmail, icon = Icons.Default.Lock)
                }
                if (departments.isEmpty()) {
                    Text("Join a department to send to its members, or send by email above.", style = CtosText.Small, color = CtosColors.TextMuted)
                } else {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            departments.forEachIndexed { index, department ->
                                if (index > 0) HorizontalDivider(color = CtosColors.Border)
                                DepartmentRow(department, onSend)
                            }
                        }
                    }
                }

                SectionTitle("Sent")
                SecureDropRows(lists.sent, empty = "Nothing sent yet.", onOpenDrop, onDeleteDrop, ownedByMe = true)

                SectionTitle("Received")
                SecureDropRows(lists.received, empty = "Nothing has been sent to you yet.", onOpenDrop, onDeleteDrop, ownedByMe = false)
            }
        }
    }
}

/**
 * Two steps, so the key pair itself (step 1, entirely on this device: generate or import) is a
 * separate, visible action from protecting it with a passphrase (step 2). Nothing is sent to the
 * server until step 2's encrypted result is ready.
 */
@Composable
private fun SecureDropKeySetupCard(
    state: FormState,
    pendingKeyPairFingerprint: String?,
    onSetUpKeyPair: (String?, String?) -> Unit,
    onRestart: () -> Unit,
    onEncryptAndSave: (String, String) -> Unit,
    onEdit: () -> Unit,
) {
    val working = state is FormState.Working

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.background(CtosColors.Primary100, CtosShapes.Control).padding(10.dp)) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = CtosColors.Primary)
                }
                Text("Set up your Secure Drop key", style = CtosText.BodyStrong, color = CtosColors.Text)
            }
            Text(
                "Made (or brought) right here on this device, in two steps: the key pair itself, then a passphrase " +
                    "to protect it. Nothing but the finished, encrypted result is ever sent anywhere — nobody else " +
                    "ever sees your private key or your passphrase, and it cannot be recovered if either is lost.",
                style = CtosText.Small,
                color = CtosColors.TextMuted,
            )

            if (pendingKeyPairFingerprint == null) {
                KeyPairStep(state, working, onSetUpKeyPair, onEdit)
            } else {
                PassphraseStep(state, working, pendingKeyPairFingerprint, onRestart, onEncryptAndSave, onEdit)
            }
        }
    }
}

/** Step 1: the key pair itself. Generating or importing happens entirely on this device. */
@Composable
private fun KeyPairStep(
    state: FormState,
    working: Boolean,
    onSetUpKeyPair: (String?, String?) -> Unit,
    onEdit: () -> Unit,
) {
    var importing by remember { mutableStateOf(false) }
    var importPublic by remember { mutableStateOf("") }
    var importPrivate by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(if (!importing) "Generate a new key" else "Cancel importing", onClick = { importing = false })
            SecondaryButton(if (importing) "Bring my own key pair" else "Bring my own key pair instead", onClick = { importing = true })
        }

        if (importing) {
            AppTextField(importPublic, { importPublic = it; onEdit() }, "Public key", monospace = true)
            AppTextField(importPrivate, { importPrivate = it; onEdit() }, "Private key", monospace = true)
        } else {
            Text("A fresh RSA key pair, generated on this device. Nothing leaves it at this step.", style = CtosText.Small, color = CtosColors.TextMuted)
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
            if (importing) "Use these keys" else "Generate my key pair",
            onClick = { onSetUpKeyPair(if (importing) importPublic else null, if (importing) importPrivate else null) },
            modifier = Modifier.fillMaxWidth(),
            loading = working,
            icon = Icons.Default.Key,
        )
    }
}

/** Step 2: protects step 1's key pair with a passphrase, then sends only the encrypted result (plus the public key). */
@Composable
private fun PassphraseStep(
    state: FormState,
    working: Boolean,
    fingerprint: String,
    onRestart: () -> Unit,
    onEncryptAndSave: (String, String) -> Unit,
    onEdit: () -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().background(CtosColors.Canvas, CtosShapes.Control).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Your key pair is ready.", style = CtosText.SmallStrong, color = CtosColors.Text)
            Text("SHA-256: $fingerprint", style = CtosText.Caption, color = CtosColors.TextSubtle)
            LinkButton("Use a different key instead", onClick = onRestart)
        }

        AppTextField(passphrase, { passphrase = it; onEdit() }, "Passphrase", isPassword = true)
        AppTextField(confirmation, { confirmation = it; onEdit() }, "Confirm passphrase", isPassword = true)

        if (state is FormState.Failed) {
            Text(
                state.message,
                style = CtosText.Small,
                color = CtosColors.Danger,
                modifier = Modifier.fillMaxWidth().background(CtosColors.DangerSoft, CtosShapes.Control).padding(12.dp),
            )
        }

        PrimaryButton(
            "Encrypt and save my key",
            onClick = { onEncryptAndSave(passphrase, confirmation) },
            modifier = Modifier.fillMaxWidth(),
            loading = working,
            icon = Icons.Default.Lock,
        )
        Text(
            "A copy of your encrypted key is also saved to this device, as a backup.",
            style = CtosText.Caption,
            color = CtosColors.TextSubtle,
        )
    }
}

@Composable
private fun YourKeyCard(state: SecureDropKeyState.Ready, onCopyKey: () -> Unit, onRemoveKey: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.background(CtosColors.Primary100, CtosShapes.Control).padding(10.dp)) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = CtosColors.Primary)
                }
                Text("Your key", style = CtosText.BodyStrong, color = CtosColors.Text)
            }
            Text(state.fingerprint, style = CtosText.Mono, color = CtosColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "Compare this fingerprint with someone out of band (a call, in person) before trusting a key you were not there to set up yourself.",
                style = CtosText.Caption,
                color = CtosColors.TextSubtle,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Copy my encrypted key", onCopyKey, icon = Icons.Default.Download)
                DangerButton("Remove my key", onRemoveKey)
            }
        }
    }
}

@Composable
private fun DepartmentRow(department: Department, onSend: (Department) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(department.name, style = CtosText.Small, color = CtosColors.Text, modifier = Modifier.weight(1f))
        LinkButton("Send", onClick = { onSend(department) })
    }
}

@Composable
private fun SecureDropRows(
    drops: List<SecureDropSummary>,
    empty: String,
    onOpenDrop: (SecureDropSummary) -> Unit,
    onDeleteDrop: (SecureDropSummary) -> Unit,
    ownedByMe: Boolean,
) {
    if (drops.isEmpty()) {
        EmptyState(Icons.Default.Lock, empty, "")
        return
    }
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(8.dp)) {
            drops.forEachIndexed { index, drop ->
                if (index > 0) HorizontalDivider(color = CtosColors.Border)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = CtosColors.Primary)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(drop.name, style = CtosText.SmallStrong, color = CtosColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            drop.departmentName + if (ownedByMe) "" else " · from ${drop.ownerName}",
                            style = CtosText.Caption,
                            color = CtosColors.TextSubtle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    LinkButton("Open", onClick = { onOpenDrop(drop) })
                    if (ownedByMe) {
                        LinkButton("Delete", onClick = { onDeleteDrop(drop) })
                    }
                }
            }
        }
    }
}
