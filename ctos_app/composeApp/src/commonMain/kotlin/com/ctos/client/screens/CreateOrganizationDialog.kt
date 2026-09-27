package com.ctos.client.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
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

/** Creates a new organization, owned by the signed-in user, right from the app. */
@Composable
fun CreateOrganizationDialog(
    state: FormState,
    onSubmit: (name: String) -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppDialog(
        title = "Create an organization",
        onDismiss = onDismiss,
        icon = Icons.Default.Business,
        description = "Give it a name. You can add departments to it once it is created.",
    ) {
        var name by remember { mutableStateOf("") }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AppTextField(
                value = name,
                onValueChange = { name = it; onEdit() },
                label = "Organization name",
                placeholder = "Acme Corp",
                imeAction = ImeAction.Done,
                onImeAction = { onSubmit(name) },
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
            PrimaryButton("Create organization", onClick = { onSubmit(name) }, modifier = Modifier.fillMaxWidth(), loading = state is FormState.Working)
        }
    }
}
