package com.ctos.client.screens.widgets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ctos.client.Toast
import com.ctos.client.ToastKind
import com.ctos.client.ToastManager
import com.ctos.client.theme.CtosColors
import com.ctos.client.theme.CtosShapes
import com.ctos.client.theme.CtosText
import kotlinx.coroutines.delay

/** Shows the messages queued in [ToastManager], one at a time, for a few seconds each. */
@Composable
fun ToastHost(modifier: Modifier = Modifier) {
    var current by remember { mutableStateOf<Toast?>(null) }

    LaunchedEffect(Unit) {
        for (toast in ToastManager.toasts) {
            current = toast
            delay(3_500)
            current = null
            delay(200)
        }
    }

    Box(modifier = modifier.padding(16.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(visible = current != null, enter = fadeIn() + slideInVertically { it / 2 }) {
            current?.let { ToastCard(it) }
        }
    }
}

@Composable
fun ToastCard(toast: Toast) {
    val (icon, tint) = when (toast.kind) {
        ToastKind.Success -> Icons.Default.CheckCircle to CtosColors.SuccessSoft
        ToastKind.Error -> Icons.Default.ErrorOutline to CtosColors.DangerSoft
        ToastKind.Info -> Icons.Default.Info to CtosColors.Primary200
    }
    Row(
        modifier = Modifier
            .widthIn(max = 520.dp)
            .background(CtosColors.Text, CtosShapes.Control)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(toast.message, style = CtosText.Body, color = CtosColors.Surface)
    }
}
