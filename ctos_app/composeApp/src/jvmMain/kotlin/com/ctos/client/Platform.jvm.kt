package com.ctos.client

import androidx.compose.runtime.Composable
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.net.InetAddress
import java.net.URI

actual fun platformName(): String {
    val os = System.getProperty("os.name").lowercase()
    return when {
        os.contains("win") -> "windows"
        os.contains("mac") -> "macos"
        else -> "linux"
    }
}

actual fun deviceName(): String {
    val computer = try {
        InetAddress.getLocalHost().hostName
    } catch (e: Exception) {
        System.getenv("HOSTNAME") ?: System.getenv("COMPUTERNAME") ?: "computer"
    }
    return "$computer (${platformName().replaceFirstChar { it.uppercase() }})".take(100)
}

actual fun openUrl(url: String) {
    try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
        } else {
            runOpener(url)
        }
    } catch (e: Exception) {
        println("openUrl() error: ${e.message}")
    }
}

actual fun openFolder(path: String): Boolean = try {
    val folder = File(path).also { it.mkdirs() }
    openWithSystem(folder)
    true
} catch (e: Exception) {
    println("openFolder() error: ${e.message}")
    false
}

actual fun openFile(path: String): Boolean = try {
    openWithSystem(File(path))
    true
} catch (e: Exception) {
    println("openFile() error: ${e.message}")
    false
}

private fun openWithSystem(target: File) {
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
        Desktop.getDesktop().open(target)
    } else {
        runOpener(target.absolutePath)
    }
}

// Linux desktops without Java's desktop support (some window managers) still have xdg-open.
private fun runOpener(target: String) {
    val command = when (platformName()) {
        "macos" -> "open"
        "windows" -> "explorer"
        else -> "xdg-open"
    }
    ProcessBuilder(command, target).redirectErrorStream(true).start()
}

actual fun copyToClipboard(text: String) {
    try {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    } catch (e: Exception) {
        println("copyToClipboard() error: ${e.message}")
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // A computer has no system Back button.
}
