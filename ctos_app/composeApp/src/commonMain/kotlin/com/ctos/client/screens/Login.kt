package com.ctos.client.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.ctos.client.BuildKonfig
import com.ctos.client.data.FormState
import com.ctos.client.screens.widgets.AppTextField
import com.ctos.client.screens.widgets.LinkButton
import com.ctos.client.screens.widgets.Logo
import com.ctos.client.screens.widgets.PrimaryButton
import com.ctos.client.theme.CtosColors
import com.ctos.client.theme.CtosShapes
import com.ctos.client.theme.CtosText

@Composable
fun LoginContent(
    state: FormState,
    onLogin: (email: String, password: String) -> Unit,
    onEdit: () -> Unit,
    onForgotPassword: () -> Unit,
    onCreateAccount: () -> Unit,
    serverUrl: String,
) {
    // safeDrawing includes the on-screen keyboard, so the form moves up (and scrolls) instead of hiding behind it.
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(CtosColors.Surface).windowInsetsPadding(WindowInsets.safeDrawing)) {
        val showBrandPanel = maxWidth >= 900.dp
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                LoginForm(state, onLogin, onEdit, onForgotPassword, onCreateAccount, serverUrl)
            }
            if (showBrandPanel) {
                BrandPanel(modifier = Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun LoginForm(
    state: FormState,
    onLogin: (String, String) -> Unit,
    onEdit: () -> Unit,
    onForgotPassword: () -> Unit,
    onCreateAccount: () -> Unit,
    serverUrl: String,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val working = state is FormState.Working

    Column(
        modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Logo()
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Welcome back", style = CtosText.PageTitle, color = CtosColors.Text)
            Text("Log in to see your departments and files.", style = CtosText.Body, color = CtosColors.TextMuted)
        }

        AppTextField(
            value = email,
            onValueChange = { email = it; onEdit() },
            label = "Email",
            placeholder = "you@example.com",
            autofocus = true,
        )
        AppTextField(
            value = password,
            onValueChange = { password = it; onEdit() },
            label = "Password",
            isPassword = true,
            imeAction = androidx.compose.ui.text.input.ImeAction.Done,
            onImeAction = { onLogin(email, password) },
        )

        if (state is FormState.Failed) {
            Text(
                state.message,
                style = CtosText.Small,
                color = CtosColors.Danger,
                modifier = Modifier.fillMaxWidth().background(CtosColors.DangerSoft, CtosShapes.Control).padding(12.dp),
            )
        }

        PrimaryButton("Log in", onClick = { onLogin(email, password) }, modifier = Modifier.fillMaxWidth(), loading = working)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            LinkButton("Forgot password?", onForgotPassword)
            LinkButton("Create an account", onCreateAccount)
        }

        Text(serverUrl, style = CtosText.Caption, color = CtosColors.TextSubtle)
    }
}

/** The dark, always-brand panel; the website's own version is two radial glows (top-right, bottom-left) over `bg-brand-950` — this is the same idea, in one gradient Compose can express directly. */
@Composable
private fun BrandPanel(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.background(CtosColors.Primary950), contentAlignment = Alignment.CenterStart) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val glow = Brush.radialGradient(
            colors = listOf(CtosColors.Primary600.copy(alpha = 0.55f), Color.Transparent),
            center = Offset(widthPx * 0.9f, 0f),
            radius = widthPx * 0.9f,
        )
        Box(modifier = Modifier.fillMaxSize().background(glow))
        Column(modifier = Modifier.padding(64.dp), verticalArrangement = Arrangement.spacedBy(36.dp)) {
            Text(
                "One shared folder, on every machine.",
                style = CtosText.Display,
                color = CtosColors.Surface,
                modifier = Modifier.widthIn(max = 460.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                Bullet(Icons.Default.Business, "Organise by department", "Give every team its own space behind its own access code.")
                Bullet(Icons.Default.Sync, "Always in sync", "Edit a file once and it updates everywhere.")
                Bullet(Icons.Default.Lock, "Private by default", "Only members of a department can see its files.")
            }
        }
    }
}

@Composable
private fun Bullet(icon: ImageVector, title: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            modifier = Modifier.background(CtosColors.Primary800, CtosShapes.Control).padding(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(icon, contentDescription = null, tint = CtosColors.Primary200)
        }
        Column(modifier = Modifier.widthIn(max = 380.dp)) {
            Text(title, style = CtosText.BodyStrong, color = CtosColors.Surface)
            Text(text, style = CtosText.Small, color = CtosColors.Primary200)
        }
    }
}

/** The connected login screen. */
@Composable
fun LoginScreen(
    state: FormState,
    onLogin: (String, String) -> Unit,
    onEdit: () -> Unit,
    onForgotPassword: () -> Unit,
    onCreateAccount: () -> Unit,
) {
    LoginContent(
        state = state,
        onLogin = onLogin,
        onEdit = onEdit,
        onForgotPassword = onForgotPassword,
        onCreateAccount = onCreateAccount,
        serverUrl = BuildKonfig.APP_URL,
    )
}
