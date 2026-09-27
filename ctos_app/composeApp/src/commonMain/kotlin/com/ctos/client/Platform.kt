package com.ctos.client

import androidx.compose.runtime.Composable

/** A name for this device, shown in the user's list of signed-in devices ("alice-laptop (Linux)"). */
expect fun deviceName(): String

/** "linux", "macos", "windows" or "android". */
expect fun platformName(): String

/** Opens a web page in the system browser. */
expect fun openUrl(url: String)

/** Opens a folder in the system file manager. Returns false where that is not possible (a phone). */
expect fun openFolder(path: String): Boolean

/** Opens a file with whatever app the device offers for it. Returns false when none can. */
expect fun openFile(path: String): Boolean

/** Puts [text] on the clipboard. */
expect fun copyToClipboard(text: String)

/** Calls [onBack] when the user presses the system Back button, while [enabled]. Only Android has one. */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
