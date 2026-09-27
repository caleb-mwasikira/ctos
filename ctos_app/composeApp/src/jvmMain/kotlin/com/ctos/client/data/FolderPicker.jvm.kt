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
actual fun rememberFolderPicker(onPicked: (PickedFolder) -> Unit): FolderPicker {
    val scope = rememberCoroutineScope()
    val latest = rememberUpdatedState(onPicked)
    return remember {
        FolderPicker {
            scope.launch {
                val chosen = withContext(Dispatchers.IO) { chooseFolder() }
                if (chosen != null) latest.value(DiskFolder(chosen))
            }
        }
    }
}

/** Shows the operating system's folder chooser, and waits for the answer. Null when the user cancels. */
private fun chooseFolder(): File? {
    var chosen: File? = null
    SwingUtilities.invokeAndWait {
        val chooser = JFileChooser(System.getProperty("user.home")).apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = "Choose a folder to add"
            approveButtonText = "Add folder"
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chosen = chooser.selectedFile
    }
    return chosen
}

/** A folder on this computer's disk. */
class DiskFolder(private val folder: File) : PickedFolder {
    override val name: String get() = folder.name.ifEmpty { "Folder" }

    override suspend fun copyInto(destination: String): Int = withContext(Dispatchers.IO) {
        copyTree(folder, File(destination))
    }
}
