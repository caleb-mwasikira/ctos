package com.ctos.client.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.ctos.client.data.FormState
import com.ctos.client.screens.widgets.AppTextField
import com.ctos.client.screens.widgets.PrimaryButton

/**
 * Two steps, the same screen: enter the email address to get a code, then enter that code and a
 * new password. [emailSent] is null for the first step, and the address the code went to once
 * it has been sent β€” the caller (the view model) is what remembers which step this is, the same
 * way [com.ctos.client.screens.GenerateAccessCodeDialog] remembers whether a code was made yet.
 */
@Composable
fun ForgotPasswordContent(
    state: FormState,
    emailSent: String?,
    onRequestCode: (email: String) -> Unit,
    onResetPassword: (code: String, password: String, confirmation: String) -> Unit,
    onEdit: () -> Unit,
    onBackToLogin: () -> Unit,
) {
    val working = state is FormState.Working

    AuthShell(onBack = onBackToLogin) {
        if (emailSent == null) {
            var email by remember { mutableStateOf("") }
            AuthHeader("Forgot your password?", "Enter your email address and we will send you a code to choose a new one.")
            AppTextField(
                value = email,
                onValueChange = { email = it; onEdit() },
                label = "Email",
                placeholder = "you@example.com",
                autofocus = true,
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
                onImeAction = { onRequestCode(email) },
            )
            AuthError(state)
            PrimaryButton("Send code", onClick = { onRequestCode(email) }, modifier = Modifier.fillMaxWidth(), loading = working)
        } else {
            var code by remember { mutableStateOf("") }
            var password by remember { mutableStateOf("") }
            var confirmation by remember { mutableStateOf("") }
            AuthHeader("Check your email", "We sent a code to $emailSent. Enter it below with your new password.")
            AppTextField(
                value = code,
                onValueChange = { code = it; onEdit() },
                label = "Code",
                placeholder = "123456",
                autofocus = true,
                keyboardType = KeyboardType.Number,
                monospace = true,
            )
            AppTextField(value = password, onValueChange = { password = it; onEdit() }, label = "New password", isPassword = true)
            AppTextField(
                value = confirmation,
                onValueChange = { confirmation = it; onEdit() },
                label = "Confirm new password",
                isPassword = true,
                imeAction = ImeAction.Done,
                onImeAction = { onResetPassword(code, password, confirmation) },
            )
            AuthError(state)
            PrimaryButton("Reset password", onClick = { onResetPassword(code, password, confirmation) }, modifier = Modifier.fillMaxWidth(), loading = working)
        }
    }
}
