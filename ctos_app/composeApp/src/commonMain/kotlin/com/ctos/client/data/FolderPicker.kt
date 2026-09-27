package com.ctos.client.data

import androidx.compose.runtime.Composable

/** A folder the user picked to add to a department. Nothing is copied until [copyInto] is called. */
interface PickedFolder {
    /** The folder's own name ("Holiday photos"): it becomes a folder of that name in the department. */
    val name: String

    /**
     * Copies everything in the folder into [destination] (created when missing), keeping file times, and
     * leaving out the temporary files nobody wants synced. Returns how many files were copied.
     */
    suspend fun copyInto(destination: String): Int
}

/** Opens the system's "choose a folder" screen. */
fun interface FolderPicker {
    fun open()
}

/** Remembers a [FolderPicker] that calls [onPicked] with the folder the user chooses (and nothing when they cancel). */
@Composable
expect fun rememberFolderPicker(onPicked: (PickedFolder) -> Unit): FolderPicker

/**
 * A name for a new folder inside [parent] that no file or folder there has yet: "Photos", then "Photos (2)",
 * "Photos (3)"... so adding a folder never mixes into (or overwrites) one that is already there.
 */
fun uniqueFolderName(wanted: String, taken: (String) -> Boolean): String {
    if (!taken(wanted)) return wanted
    var number = 2
    while (taken("$wanted ($number)")) number++
    return "$wanted ($number)"
}
