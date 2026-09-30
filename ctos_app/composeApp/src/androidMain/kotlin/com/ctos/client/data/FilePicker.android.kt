package com.ctos.client.data

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.ctos.client.AndroidContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Android's own file chooser (the Storage Access Framework): the app is only given the file the user picks. */
@Composable
actual fun rememberFilePicker(onPicked: (PickedFile) -> Unit): FilePicker {
    val latest = rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) latest.value(DocumentFile(uri))
    }
    return remember(launcher) { FilePicker { launcher.launch(arrayOf("*/*")) } }
}

/** A file chosen with the Storage Access Framework, read through its content:// address. */
class DocumentFile(private val uri: Uri) : PickedFile {
    override val name: String
        get() {
            val resolver = AndroidContext.application.contentResolver
            return resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
                ?: uri.lastPathSegment?.substringAfterLast('/') ?: "File"
        }

    override suspend fun readBytes(): ByteArray = withContext(Dispatchers.IO) {
        AndroidContext.application.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
    }
}
