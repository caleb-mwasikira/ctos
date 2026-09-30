package com.ctos.client.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities

@Composable
actual fun rememberFilePicker(onPicked: (PickedFile) -> Unit): FilePicker {
    val scope = rememberCoroutineScope()
    val latest = rememberUpdatedState(onPicked)
    return remember {
        FilePicker {
            scope.launch {
                val chosen = withContext(Dispatchers.IO) { chooseFile() }
                if (chosen != null) latest.value(DiskFile(chosen))
            }
        }
    }
}

/** Shows the operating system's file chooser, and waits for the answer. Null when the user cancels. */
private fun chooseFile(): File? {
    var chosen: File? = null
    SwingUtilities.invokeAndWait {
        val chooser = JFileChooser(System.getProperty("user.home")).apply {
            fileSelectionMode = JFileChooser.FILES_ONLY
            dialogTitle = "Choose a file to send"
            approveButtonText = "Send"
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chosen = chooser.selectedFile
    }
    return chosen
}

/** A file on this computer's disk. */
class DiskFile(private val file: File) : PickedFile {
    override val name: String get() = file.name.ifEmpty { "File" }

    override suspend fun readBytes(): ByteArray = withContext(Dispatchers.IO) { file.readBytes() }
}
