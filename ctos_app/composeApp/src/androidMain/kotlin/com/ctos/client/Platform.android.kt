package com.ctos.client

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

actual fun platformName(): String = "android"

actual fun deviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(100)

actual fun openUrl(url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        AndroidContext.application.startActivity(intent)
    } catch (e: Exception) {
        println("openUrl() error: ${e.message}")
    }
}

// The app's own files folder is not reachable from a file manager on a phone.
actual fun openFolder(path: String): Boolean = false

actual fun openFile(path: String): Boolean {
    val context = AndroidContext.application
    val file = File(path)
    if (!file.isFile) return false

    return try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val type = context.contentResolver.getType(uri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
            ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, type)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        // Most often ActivityNotFoundException: no app on the device can open this kind of file.
        println("openFile() error: ${e.message}")
        false
    }
}

actual fun copyToClipboard(text: String) {
    val clipboard = AndroidContext.application.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("CTOS", text))
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}
