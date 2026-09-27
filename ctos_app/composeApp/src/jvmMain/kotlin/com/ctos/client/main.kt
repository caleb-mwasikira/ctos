package com.ctos.client

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.ctos.client.data.AppViewModel
import com.ctos.client.screens.App
import java.awt.Dimension

fun main() = application {
    val state = rememberWindowState(size = DpSize(1180.dp, 780.dp))

    Tray(
        icon = BitmapPainter(useResource("ctos-tray-icon.png", ::loadImageBitmap)),
        tooltip = "CTOS",
        onAction = { state.isMinimized = false },
        menu = {
            Item("Open CTOS", onClick = { state.isMinimized = false })
            Separator()
            Item("Quit", onClick = ::exitApplication)
        },
    )

    Window(
        onCloseRequest = ::exitApplication,
        state = state,
        title = "CTOS",
        icon = BitmapPainter(useResource("ctos-icon.png", ::loadImageBitmap)),
    ) {
        window.minimumSize = Dimension(420, 560)
        val viewModel = remember { AppViewModel() }
        App(viewModel)
    }
}
