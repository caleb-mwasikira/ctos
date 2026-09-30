package com.ctos.client.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ctos.client.screens.widgets.AppDialog
import com.ctos.client.screens.widgets.SecondaryButton
import com.ctos.client.theme.CtosColors
import com.ctos.client.theme.CtosShapes
import com.ctos.client.theme.CtosText

/** The full SHA-256 fingerprint, shown on request so it can be read aloud or compared out of band (docs/secure-drop.md). */
@Composable
fun SecureDropFingerprintDialog(fingerprint: String, onCopy: () -> Unit, onDismiss: () -> Unit) {
    AppDialog(
        title = "Your key's fingerprint",
        onDismiss = onDismiss,
        icon = Icons.Default.Key,
        description = "Compare this with someone out of band — a call, in person — before trusting a key you were not there to set up yourself.",
    ) {
        SecureDropFingerprintForm(fingerprint, onCopy)
    }
}

/** Split out from [SecureDropFingerprintDialog] so it can be drawn without a real platform window (screenshot tests). */
@Composable
fun SecureDropFingerprintForm(fingerprint: String, onCopy: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            fingerprint,
            style = CtosText.Mono,
            color = CtosColors.Text,
            modifier = Modifier.fillMaxWidth().background(CtosColors.Canvas, CtosShapes.Control).padding(12.dp),
        )
        SecondaryButton("Copy", onCopy, modifier = Modifier.fillMaxWidth(), icon = Icons.Default.ContentCopy)
    }
}
