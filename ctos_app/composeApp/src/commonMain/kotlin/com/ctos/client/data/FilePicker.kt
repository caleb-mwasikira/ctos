package com.ctos.client.data

import androidx.compose.runtime.Composable

/** A file the user picked, to send with Secure Drop (docs/secure-drop.md). Nothing is read until [readBytes] is called. */
interface PickedFile {
    val name: String

    suspend fun readBytes(): ByteArray
}

/** Opens the system's "choose a file" screen. */
fun interface FilePicker {
    fun open()
}

/** Remembers a [FilePicker] that calls [onPicked] with the file the user chooses (and nothing when they cancel). */
@Composable
expect fun rememberFilePicker(onPicked: (PickedFile) -> Unit): FilePicker
