package com.ctos.client.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ctos.client.screens.widgets.IconTile
import com.ctos.client.theme.CtosColors
import com.ctos.client.theme.CtosText

@Composable
fun LoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize().background(CtosColors.Canvas),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        IconTile(Icons.Default.Folder, size = 64.dp, filled = true)
        Text("CTOS", style = CtosText.PageTitle, color = CtosColors.Text)
        CircularProgressIndicator(color = CtosColors.Primary, strokeWidth = 3.dp)
    }
}
