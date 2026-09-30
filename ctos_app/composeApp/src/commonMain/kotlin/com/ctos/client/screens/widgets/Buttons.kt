package com.ctos.client.screens.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ctos.client.theme.CtosColors
import com.ctos.client.theme.CtosShapes
import com.ctos.client.theme.CtosText

private val ButtonPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)

@Composable
private fun ButtonContent(text: String, icon: ImageVector?, loading: Boolean, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = color)
            Spacer(Modifier.width(10.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = CtosText.Button)
    }
}

/** The main action of a screen or a dialog. While [loading] it shows a spinner and ignores clicks. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 46.dp),
        enabled = enabled && !loading,
        shape = CtosShapes.Field,
        contentPadding = ButtonPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = CtosColors.Primary,
            contentColor = CtosColors.OnPrimary,
            disabledContainerColor = CtosColors.Primary.copy(alpha = if (loading) 1f else 0.5f),
            disabledContentColor = CtosColors.OnPrimary,
        ),
    ) {
        ButtonContent(text, icon, loading, CtosColors.OnPrimary)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 46.dp),
        enabled = enabled,
        shape = CtosShapes.Field,
        contentPadding = ButtonPadding,
        border = BorderStroke(1.dp, CtosColors.BorderStrong),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = CtosColors.Surface, contentColor = CtosColors.Text),
    ) {
        ButtonContent(text, icon, loading = false, color = CtosColors.Text)
    }
}

@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 46.dp),
        shape = CtosShapes.Field,
        contentPadding = ButtonPadding,
        border = BorderStroke(1.dp, CtosColors.DangerSoft),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = CtosColors.Surface, contentColor = CtosColors.Danger),
    ) {
        ButtonContent(text, icon, loading = false, color = CtosColors.Danger)
    }
}

@Composable
fun LinkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier, colors = ButtonDefaults.textButtonColors(contentColor = CtosColors.Primary)) {
        Text(text, style = CtosText.SmallStrong)
    }
}

/** A round icon-only button, for a top bar. */
@Composable
fun IconAction(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier.height(44.dp).width(44.dp)) {
        Icon(icon, contentDescription = description, tint = CtosColors.TextMuted)
    }
}
