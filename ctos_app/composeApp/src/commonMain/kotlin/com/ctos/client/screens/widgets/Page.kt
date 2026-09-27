package com.ctos.client.screens.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ctos.client.theme.CtosColors
import com.ctos.client.theme.CtosText

/** A scrolling page: content no wider than 1100dp, centered, with roomier margins on a big screen. */
@Composable
fun Page(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val margin = if (maxWidth >= 760.dp) 32.dp else 16.dp
        Column(
            modifier = Modifier
                .widthIn(max = 1100.dp + margin * 2)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = margin, vertical = margin),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            content = content,
        )
    }
}

@Composable
fun PageTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = CtosText.PageTitle, color = CtosColors.Text)
        if (subtitle != null) Text(subtitle, style = CtosText.Body, color = CtosColors.TextMuted)
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(title, style = CtosText.SectionTitle, color = CtosColors.Text)
}
