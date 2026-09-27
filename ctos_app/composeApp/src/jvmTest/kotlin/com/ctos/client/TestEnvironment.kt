package com.ctos.client

import java.io.File
import java.nio.file.Files

/**
 * Points the app's own data and the folder synced files live in at temporary folders, so tests never
 * touch the real ones. Touch this object (ensure()) before anything that opens the local database.
 */
object TestEnvironment {
    val home: File = Files.createTempDirectory("ctos-test-home").toFile().apply { deleteOnExit() }
    val syncRoot: File = Files.createTempDirectory("ctos-test-sync").toFile().apply { deleteOnExit() }

    init {
        System.setProperty("ctos.home", home.path)
        System.setProperty("ctos.sync", syncRoot.path)
    }

    fun ensure() = Unit

    /** A new empty folder to watch. */
    fun newFolder(name: String = "folder"): File = Files.createTempDirectory(syncRoot.toPath(), name).toFile()
}

/** Waits (polling) until [condition] is true, for at most [timeoutMillis]. */
fun waitUntil(timeoutMillis: Long = 5_000, description: String = "the condition", condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (System.currentTimeMillis() < deadline) {
        if (condition()) return
        Thread.sleep(25)
    }
    if (!condition()) throw AssertionError("Timed out after $timeoutMillis ms waiting for $description")
}
