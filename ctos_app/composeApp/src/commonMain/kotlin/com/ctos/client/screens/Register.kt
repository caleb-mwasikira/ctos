package com.ctos.client.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import com.ctos.client.data.FormState
import com.ctos.client.screens.widgets.AppTextField
import com.ctos.client.screens.widgets.LinkButton
import com.ctos.client.screens.widgets.PrimaryButton
import com.ctos.client.theme.CtosColors
import com.ctos.client.theme.CtosText
import androidx.compose.material3.Text

/** Create an account, right from the app: name, email, a password, and its confirmation. */
@Composable
fun RegisterContent(
    state: FormState,
    onRegister: (name: String, email: String, password: String, confirmation: String) -> Unit,
    onEdit: () -> Unit,
    onBackToLogin: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val working = state is FormState.Working

    AuthShell(onBack = onBackToLogin) {
        AuthHeader("Create an account", "Set up a workspace for your team, or join one with an access code.")

        AppTextField(value = name, onValueChange = { name = it; onEdit() }, label = "Name", placeholder = "Alice Wanjiku", autofocus = true)
        AppTextField(value = email, onValueChange = { email = it; onEdit() }, label = "Email", placeholder = "you@example.com")
        AppTextField(value = password, onValueChange = { password = it; onEdit() }, label = "Password", isPassword = true)
        AppTextField(
            value = confirmation,
            onValueChange = { confirmation = it; onEdit() },
            label = "Confirm password",
            isPassword = true,
            imeAction = ImeAction.Done,
            onImeAction = { onRegister(name, email, password, confirmation) },
        )

        AuthError(state)

        PrimaryButton("Create account", onClick = { onRegister(name, email, password, confirmation) }, modifier = Modifier.fillMaxWidth(), loading = working)

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("Already have an account?", style = CtosText.Small, color = CtosColors.TextMuted)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            LinkButton("Log in instead", onBackToLogin)
        }
    }
}
