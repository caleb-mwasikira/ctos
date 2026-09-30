package com.ctos.client.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
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
import com.ctos.client.screens.widgets.PrimaryButton
import com.ctos.client.screens.widgets.SecondaryButton
import com.ctos.client.screens.widgets.SectionTitle
import com.ctos.client.theme.CtosColors
import com.ctos.client.theme.CtosShapes
import com.ctos.client.theme.CtosText
import kotlinx.datetime.Clock

private val SideBySideWidth = 760.dp

/**
 * Secure Drop (docs/secure-drop.md): files encrypted on this device before they ever leave it,
 * for one or more people to decrypt on theirs. This screen is the key pair's own setup (when
 * there is not one yet) or a summary of it, and everything sent or received — the same split as
 * the website's own /secure-drop page: "Send, encrypted" in the title row (it opens a dialog to
 * pick a department or send by email, same as the website's), Sent/Received as the main column,
 * "Your key" and a department shortcut list as the side column.
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
    onShowFingerprint: () -> Unit,
    onCopyKey: () -> Unit,
    onRemoveKey: () -> Unit,
    onOpenSendPicker: () -> Unit,
    onSend: (Department) -> Unit,
    onOpenDrop: (SecureDropSummary) -> Unit,
    onDeleteDrop: (SecureDropSummary) -> Unit,
) {
    Page {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val wide = maxWidth >= SideBySideWidth

            Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                val title: @Composable (Modifier) -> Unit = { modifier ->
                    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Secure Drop", style = CtosText.PageTitle, color = CtosColors.Text)
                        Text(
                            "Files encrypted on your own device before they leave it. Nobody but the people you pick — not even this server — can read them.",
                            style = CtosText.Body,
                            color = CtosColors.TextMuted,
                        )
                    }
                }

                if (keyState is SecureDropKeyState.Ready) {
                    val actions: @Composable () -> Unit = {
                        PrimaryButton("Send, encrypted", onOpenSendPicker, icon = Icons.Default.Lock)
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
                } else {
                    title(Modifier.fillMaxWidth())
                }

                when (keyState) {
                    SecureDropKeyState.Loading -> Text("Loading…", style = CtosText.Small, color = CtosColors.TextMuted)
                    SecureDropKeyState.None -> SecureDropKeySetupCard(
                        keySetupState, pendingKeyPairFingerprint, onSetUpKeyPair, onRestartKeySetup, onEncryptAndSaveKey, onEditKeySetup,
                    )
                    is SecureDropKeyState.Ready -> {
                        val main: @Composable (Modifier) -> Unit = { modifier ->
                            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                                SectionTitle("Sent")
                                SecureDropRows(lists.sent, empty = "Nothing sent yet.", onOpenDrop, onDeleteDrop, ownedByMe = true)

                                SectionTitle("Received")
                                SecureDropRows(lists.received, empty = "Nothing has been sent to you yet.", onOpenDrop, onDeleteDrop, ownedByMe = false)
                            }
                        }
                        val side: @Composable (Modifier) -> Unit = { modifier ->
                            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                YourKeyCard(keyState, onShowFingerprint, onCopyKey, onRemoveKey)
                                SendShortcutsCard(departments, onSend)
                            }
                        }
                        if (wide) {
                            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
                                main(Modifier.weight(2f))
                                side(Modifier.weight(1f))
                            }
                        } else {
                            main(Modifier.fillMaxWidth())
                            side(Modifier.fillMaxWidth())
                        }
                    }
                }
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

/** The fingerprint is truncated here — clicking it shows it in full in a dialog, the same as the website's own chip. */
@Composable
private fun YourKeyCard(state: SecureDropKeyState.Ready, onShowFingerprint: () -> Unit, onCopyKey: () -> Unit, onRemoveKey: () -> Unit) {
    val nowMillis = remember(state) { Clock.System.now().toEpochMilliseconds() }

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.background(CtosColors.Primary100, CtosShapes.Control).padding(10.dp)) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = CtosColors.Primary)
                }
                Text("Your key", style = CtosText.BodyStrong, color = CtosColors.Text)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Fingerprint", style = CtosText.Small, color = CtosColors.TextSubtle)
                Text(
                    state.fingerprint.take(16) + "…",
                    style = CtosText.Mono,
                    color = CtosColors.Text,
                    maxLines = 1,
                    modifier = Modifier.background(CtosColors.SurfaceAlt, CtosShapes.Small)
                        .clickable(onClick = onShowFingerprint)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Text(
                "Made ${formatTime(state.createdAtMillis, nowMillis)}. Compare this fingerprint with someone out of band (a call, in person) before trusting a key you were not there to set up yourself.",
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

/** Quick shortcuts to a department's own send page — the header's "Send, encrypted" button (a dialog) is the other way in, same as the website's. */
@Composable
private fun SendShortcutsCard(departments: List<Department>, onSend: (Department) -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Send one, encrypted", style = CtosText.BodyStrong, color = CtosColors.Text)
            if (departments.isEmpty()) {
                Text("Join a department to send to its members, or send by email.", style = CtosText.Small, color = CtosColors.TextMuted)
            } else {
                Text("Pick a department to send a file to its members who also have a Secure Drop key.", style = CtosText.Small, color = CtosColors.TextMuted)
                Column {
                    departments.forEach { department -> DepartmentRow(department, onSend) }
                }
            }
        }
    }
}

@Composable
private fun DepartmentRow(department: Department, onSend: (Department) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onSend(department) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(department.name, style = CtosText.Small, color = CtosColors.Text, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = CtosColors.TextSubtle, modifier = Modifier.padding(start = 4.dp))
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
                            (drop.departmentName ?: "Sent by email") + if (ownedByMe) "" else " · from ${drop.ownerName}",
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
