package com.ctos.client.screens.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ctos.client.theme.CtosColors
import com.ctos.client.theme.CtosShapes
import com.ctos.client.theme.CtosText

/**
 * The ctOS wordmark: a folder icon on a brand-500-to-brand-700 gradient tile, then the name —
 * exactly `resources/views/components/logo.blade.php`, which this always matches (that gradient
 * and this icon are the one place the website's own dark: mode does not change it either).
 */
@Composable
fun Logo(modifier: Modifier = Modifier, iconSize: Dp = 32.dp, textStyle: TextStyle = CtosText.SectionTitle, textColor: Color = CtosColors.Text) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier.size(iconSize).background(Brush.linearGradient(listOf(CtosColors.Primary500, CtosColors.Primary700)), CtosShapes.Field),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = Color.White, modifier = Modifier.size(iconSize * 0.5625f))
        }
        Text("ctOS", style = textStyle, color = textColor)
    }
}
