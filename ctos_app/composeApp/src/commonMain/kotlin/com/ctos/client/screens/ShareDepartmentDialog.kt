package com.ctos.client.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ctos.client.data.FormState
import com.ctos.client.screens.widgets.AppDialog
import com.ctos.client.screens.widgets.AppTextField
import com.ctos.client.screens.widgets.PrimaryButton
import com.ctos.client.theme.CtosColors
import com.ctos.client.theme.CtosShapes
import com.ctos.client.theme.CtosText

/**
 * Shares a department with an email address, right from the app: an existing, verified account
 * gets access straight away; anyone else is emailed an invitation that becomes access once they
 * sign up and confirm that address. Mirrors the website's own "Share" action.
 */
@Composable
fun ShareDepartmentDialog(
    departmentName: String,
    state: FormState,
    onSubmit: (email: String) -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppDialog(
        title = "Share $departmentName",
        onDismiss = onDismiss,
        icon = Icons.Default.Mail,
        description = "Enter an email address. They get access to this department. They do not need an account yet: they can sign up with that address.",
    ) {
        var email by remember { mutableStateOf("") }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AppTextField(
                value = email,
                onValueChange = { email = it; onEdit() },
                label = "Email address",
                placeholder = "colleague@example.com",
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Done,
                onImeAction = { onSubmit(email) },
                autofocus = true,
            )
            if (state is FormState.Failed) {
                Text(
                    state.message,
                    style = CtosText.Small,
                    color = CtosColors.Danger,
                    modifier = Modifier.fillMaxWidth().background(CtosColors.DangerSoft, CtosShapes.Control).padding(12.dp),
                )
            }
            PrimaryButton("Send invitation", onClick = { onSubmit(email) }, modifier = Modifier.fillMaxWidth(), loading = state is FormState.Working)
        }
    }
}
