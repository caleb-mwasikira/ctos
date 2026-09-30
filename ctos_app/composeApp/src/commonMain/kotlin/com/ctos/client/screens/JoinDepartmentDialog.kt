package com.ctos.client.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ctos.client.data.FormState
import com.ctos.client.screens.widgets.AppDialog
import com.ctos.client.screens.widgets.AppTextField
import com.ctos.client.screens.widgets.PrimaryButton
import com.ctos.client.theme.CtosColors
import com.ctos.client.theme.CtosShapes
import com.ctos.client.theme.CtosText

/** Joins the department whose access code this is. */
@Composable
fun JoinDepartmentDialog(
    state: FormState,
    onSubmit: (code: String) -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppDialog(
        title = "Join a department",
        onDismiss = onDismiss,
        icon = Icons.Default.VpnKey,
        description = "Ask your team for its access code.",
    ) {
        JoinDepartmentForm(state, onSubmit, onEdit)
    }
}

/** The field and the button of the join dialog, without the popup around them. */
@Composable
fun JoinDepartmentForm(
    state: FormState,
    onSubmit: (code: String) -> Unit,
    onEdit: () -> Unit,
) {
    var code by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AppTextField(
            value = code,
            onValueChange = { code = it; onEdit() },
            label = "Access code",
            placeholder = "K7M2Q9XP4T",
            monospace = true,
            autofocus = true,
            imeAction = ImeAction.Done,
            onImeAction = { onSubmit(code) },
        )
        if (state is FormState.Failed) {
            Text(
                state.message,
                style = CtosText.Small,
                color = CtosColors.Danger,
                modifier = Modifier.fillMaxWidth().background(CtosColors.DangerSoft, CtosShapes.Control).padding(12.dp),
            )
        }
        PrimaryButton("Join department", onClick = { onSubmit(code) }, modifier = Modifier.fillMaxWidth(), loading = state is FormState.Working)
    }
}
