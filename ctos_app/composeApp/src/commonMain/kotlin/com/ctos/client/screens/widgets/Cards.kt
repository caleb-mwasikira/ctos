package com.ctos.client.screens.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ctos.client.theme.CtosColors
import com.ctos.client.theme.CtosShapes
import com.ctos.client.theme.CtosText

/** A white card with a thin border. With [onClick] the whole card is a button. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val border = BorderStroke(1.dp, CtosColors.Border)
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = CtosShapes.Card, color = CtosColors.Surface, border = border, shadowElevation = 1.dp) {
            Column(content = content)
        }
    } else {
        Surface(modifier = modifier, shape = CtosShapes.Card, color = CtosColors.Surface, border = border, shadowElevation = 1.dp) {
            Column(content = content)
        }
    }
}

/** The tinted square with an icon in it that starts every card. */
@Composable
fun IconTile(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 48.dp, filled: Boolean = false) {
    Row(
        modifier = modifier
            .size(size)
            .background(if (filled) CtosColors.Primary else CtosColors.Primary50, CtosShapes.Control),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (filled) CtosColors.OnPrimary else CtosColors.Primary, modifier = Modifier.size(size / 2))
    }
}

/**
 * The card an organization or a department is shown as, the same on every screen: an icon, a
 * title with a line of detail under it, and an optional tag on the right.
 */
@Composable
fun ResourceCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    badge: (@Composable () -> Unit)? = null,
) {
    AppCard(modifier = modifier.fillMaxSize(), onClick = onClick) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconTile(icon)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = CtosText.BodyStrong, color = CtosColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = CtosText.Small, color = CtosColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            badge?.invoke()
            if (onClick != null) {
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = CtosColors.TextSubtle)
            }
        }
    }
}

/** A big card that starts something: create an organization, join a department. */
@Composable
fun ActionCard(icon: ImageVector, title: String, text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier.fillMaxSize(), onClick = onClick) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconTile(icon, size = 56.dp, filled = true)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = CtosText.SectionTitle, color = CtosColors.Text)
                Spacer(Modifier.height(2.dp))
                Text(text, style = CtosText.Small, color = CtosColors.TextMuted)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = CtosColors.TextSubtle)
        }
    }
}

/** What a list shows when it has nothing to show, or when loading it failed. */
@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    AppCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.size(56.dp).background(CtosColors.SurfaceAlt, CtosShapes.Card),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, tint = CtosColors.TextSubtle, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(title, style = CtosText.SectionTitle, color = CtosColors.Text, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(text, style = CtosText.Small, color = CtosColors.TextMuted, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 420.dp))
            if (action != null) {
                Spacer(Modifier.height(20.dp))
                action()
            }
        }
    }
}

/** Lays cards out in as many columns as fit ([minCardWidth] each), left to right. */
@Composable
fun <T> CardGrid(items: List<T>, modifier: Modifier = Modifier, minCardWidth: Dp = 320.dp, content: @Composable (T) -> Unit) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val gap = 16.dp
        val columns = ((maxWidth + gap) / (minCardWidth + gap)).toInt().coerceIn(1, 3)
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            items.chunked(columns).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { item -> Column(modifier = Modifier.weight(1f).fillMaxHeight()) { content(item) } }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
