package com.ctos.client.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ctos.client.data.FormState
import com.ctos.client.screens.widgets.IconAction
import com.ctos.client.screens.widgets.Logo
import com.ctos.client.theme.CtosColors
import com.ctos.client.theme.CtosShapes
import com.ctos.client.theme.CtosText

/**
 * The plain backdrop every screen before sign-in shares (Login, create an account, forgot
 * password, confirm your email): the app's own surface color, centered in a narrow column that
 * scrolls, with the safe-drawing inset so the on-screen keyboard does not cover it. Login keeps
 * its own wider layout (it also shows the brand panel on a big window); the others are short
 * single-purpose forms, so this is all they need.
 */
@Composable
fun AuthShell(onBack: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(CtosColors.Surface).windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (onBack != null) {
                IconAction(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
            }
            content()
        }
    }
}

@Composable
fun AuthHeader(title: String, subtitle: String) {
    Logo()
    Spacer(Modifier.height(8.dp))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = CtosText.PageTitle, color = CtosColors.Text)
        Text(subtitle, style = CtosText.Body, color = CtosColors.TextMuted)
    }
}

@Composable
fun AuthError(state: FormState) {
    if (state is FormState.Failed) {
        Text(
            state.message,
            style = CtosText.Small,
            color = CtosColors.Danger,
            modifier = Modifier.fillMaxWidth().background(CtosColors.DangerSoft, CtosShapes.Control).padding(12.dp),
        )
    }
}
