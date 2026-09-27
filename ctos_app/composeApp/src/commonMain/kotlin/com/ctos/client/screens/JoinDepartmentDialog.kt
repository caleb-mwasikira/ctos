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

/** Joins a department with the ID shown on the website and its access code. */
@Composable
fun JoinDepartmentDialog(
    state: FormState,
    onSubmit: (departmentId: String, code: String) -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppDialog(
        title = "Join a department",
        onDismiss = onDismiss,
        icon = Icons.Default.VpnKey,
        description = "Ask your team for the department ID and its access code.",
    ) {
        JoinDepartmentForm(state, onSubmit, onEdit)
    }
}

/** The fields and the button of the join dialog, without the popup around them. */
@Composable
fun JoinDepartmentForm(
    state: FormState,
    onSubmit: (departmentId: String, code: String) -> Unit,
    onEdit: () -> Unit,
) {
    var departmentId by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        AppTextField(
            value = departmentId,
            onValueChange = { departmentId = it; onEdit() },
            label = "Department ID",
            placeholder = "engineering",
            autofocus = true,
        )
        AppTextField(
            value = code,
            onValueChange = { code = it; onEdit() },
            label = "Access code",
            placeholder = "K7M2Q9XP4T",
            monospace = true,
            imeAction = ImeAction.Done,
            onImeAction = { onSubmit(departmentId, code) },
        )
        if (state is FormState.Failed) {
            Text(
                state.message,
                style = CtosText.Small,
                color = CtosColors.Danger,
                modifier = Modifier.fillMaxWidth().background(CtosColors.DangerSoft, CtosShapes.Control).padding(12.dp),
            )
        }
        PrimaryButton("Join department", onClick = { onSubmit(departmentId, code) }, modifier = Modifier.fillMaxWidth(), loading = state is FormState.Working)
    }
}
