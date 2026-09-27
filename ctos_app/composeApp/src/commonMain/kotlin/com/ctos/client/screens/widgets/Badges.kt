package com.ctos.client.screens.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ctos.client.network.DepartmentRole
import com.ctos.client.theme.CtosColors
import com.ctos.client.theme.CtosShapes
import com.ctos.client.theme.CtosText

enum class BadgeTone(val background: Color, val content: Color) {
    Neutral(CtosColors.SurfaceAlt, CtosColors.TextMuted),
    Brand(CtosColors.Primary50, CtosColors.PrimaryHover),
    Success(CtosColors.SuccessSoft, CtosColors.Success),
    Warning(CtosColors.WarningSoft, CtosColors.Warning),
}

/** A small rounded label, like the role tags on the website. */
@Composable
fun Badge(text: String, tone: BadgeTone = BadgeTone.Neutral, modifier: Modifier = Modifier) {
    Text(
        text,
        style = CtosText.Caption,
        color = tone.content,
        modifier = modifier.background(tone.background, CtosShapes.Pill).padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
fun RoleBadge(role: DepartmentRole, modifier: Modifier = Modifier) {
    Badge(
        role.label,
        tone = when (role) {
            DepartmentRole.Admin -> BadgeTone.Brand
            DepartmentRole.Lead -> BadgeTone.Success
            DepartmentRole.Member -> BadgeTone.Neutral
        },
        modifier = modifier,
    )
}
