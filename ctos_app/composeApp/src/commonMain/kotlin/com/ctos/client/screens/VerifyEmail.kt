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
import com.ctos.client.screens.widgets.LinkButton
import com.ctos.client.screens.widgets.PrimaryButton

/** The last step of creating an account: enter the code emailed to confirm the address. */
@Composable
fun VerifyEmailContent(
    email: String,
    state: FormState,
    onVerify: (code: String) -> Unit,
    onResend: () -> Unit,
    onEdit: () -> Unit,
    onBack: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    val working = state is FormState.Working

    AuthShell(onBack = onBack) {
        AuthHeader("Confirm your email", "We sent a code to $email. Enter it below to finish creating your account.")

        AppTextField(
            value = code,
            onValueChange = { code = it; onEdit() },
            label = "Code",
            placeholder = "123456",
            autofocus = true,
            keyboardType = KeyboardType.Number,
            monospace = true,
            imeAction = ImeAction.Done,
            onImeAction = { onVerify(code) },
        )

        AuthError(state)

        PrimaryButton("Confirm", onClick = { onVerify(code) }, modifier = Modifier.fillMaxWidth(), loading = working)
        LinkButton("Send the code again", onResend)
    }
}
