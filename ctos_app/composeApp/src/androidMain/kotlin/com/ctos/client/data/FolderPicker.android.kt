package com.ctos.client.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.ctos.client.AndroidContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Android's own folder chooser (the Storage Access Framework): the app is only given the folder the user picks. */
@Composable
actual fun rememberFolderPicker(onPicked: (PickedFolder) -> Unit): FolderPicker {
    val latest = rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree: Uri? ->
        if (tree != null) latest.value(DocumentTreeFolder(AndroidContext.application.contentResolver, tree))
    }
    return remember(launcher) { FolderPicker { launcher.launch(null) } }
}

/** A folder chosen with the Storage Access Framework, read through its content:// address. */
class DocumentTreeFolder(private val resolver: ContentResolver, private val tree: Uri) : PickedFolder {
    private val rootId = DocumentsContract.getTreeDocumentId(tree)

    override val name: String
        get() = query(DocumentsContract.buildDocumentUriUsingTree(tree, rootId), DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            ?: rootId.substringAfterLast(':').substringAfterLast('/').ifEmpty { "Folder" }

    private fun query(uri: Uri, column: String): String? =
        resolver.query(uri, arrayOf(column), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }

    override suspend fun copyInto(destination: String): Int = withContext(Dispatchers.IO) {
        copyChildren(rootId, File(destination))
    }

    private fun copyChildren(documentId: String, into: File): Int {
        into.mkdirs()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, documentId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        // Read the list first: the cursor must not stay open while sub-folders are opened.
        class Entry(val id: String, val name: String, val isFolder: Boolean, val modified: Long)
        val entries = mutableListOf<Entry>()
        resolver.query(children, columns, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                entries += Entry(
                    cursor.getString(0),
                    cursor.getString(1) ?: continue,
                    cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
                    if (cursor.isNull(3)) 0L else cursor.getLong(3),
                )
            }
        }

        var copied = 0
        for (entry in entries.sortedBy { it.name }) {
            if (isIgnoredFile(entry.name) || entry.name.contains('/')) continue
            val target = File(into, entry.name)
            if (entry.isFolder) {
                copied += copyChildren(entry.id, target)
            } else {
                val uri = DocumentsContract.buildDocumentUriUsingTree(tree, entry.id)
                resolver.openInputStream(uri)?.use { input -> FileOutputStream(target).use { input.copyTo(it) } } ?: continue
                if (entry.modified > 0) target.setLastModified(entry.modified)
                copied++
            }
        }
        return copied
    }
}
