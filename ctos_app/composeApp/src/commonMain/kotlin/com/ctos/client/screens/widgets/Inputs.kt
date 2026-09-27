package com.ctos.client.screens.widgets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ctos.client.theme.CtosColors
import com.ctos.client.theme.CtosShapes
import com.ctos.client.theme.CtosText

/**
 * A labelled text field with an optional error message under it. A password field gets a
 * show/hide button. [onImeAction] runs when the user presses Enter (or the keyboard's action button).
 */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    error: String? = null,
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
    leadingIcon: ImageVector? = null,
    monospace: Boolean = false,
    focusRequester: FocusRequester? = null,
    autofocus: Boolean = false,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val requester = focusRequester ?: remember { FocusRequester() }
    if (autofocus) {
        LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(label, style = CtosText.SmallStrong, color = CtosColors.Text, modifier = Modifier.padding(bottom = 6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().focusRequester(requester),
            singleLine = true,
            isError = error != null,
            textStyle = if (monospace) CtosText.Mono else CtosText.Body,
            placeholder = placeholder?.let { { Text(it, style = CtosText.Body, color = CtosColors.TextSubtle, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null, tint = CtosColors.TextSubtle) } },
            trailingIcon = if (isPassword) {
                {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "Hide the password" else "Show the password",
                            tint = CtosColors.TextSubtle,
                        )
                    }
                }
            } else {
                null
            },
            visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(onAny = { onImeAction?.invoke() }),
            shape = CtosShapes.Control,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CtosColors.Primary,
                unfocusedBorderColor = CtosColors.BorderStrong,
                errorBorderColor = CtosColors.Danger,
                focusedContainerColor = CtosColors.Surface,
                unfocusedContainerColor = CtosColors.Surface,
                cursorColor = CtosColors.Primary,
                focusedTextColor = CtosColors.Text,
                unfocusedTextColor = CtosColors.Text,
            ),
        )
        if (error != null) {
            Text(error, style = CtosText.Small, color = CtosColors.Danger, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
