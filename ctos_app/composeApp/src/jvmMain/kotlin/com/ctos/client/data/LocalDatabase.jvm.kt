package com.ctos.client.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ctos.client.db.AppDatabase
import java.io.File

// The app's own hidden folder under the user's home. The property lets tests use a temporary one.
private fun appDataDir(): File {
    val dir = File(System.getProperty("ctos.home") ?: File(System.getProperty("user.home"), ".ctos").path)
    dir.mkdirs()
    return dir
}

actual fun createDatabase(): AppDatabase {
    val databaseFile = File(appDataDir(), "ctos.db")
    // Decided before the driver exists: opening the driver creates an empty file as a side effect.
    val isNew = !databaseFile.exists() || databaseFile.length() == 0L
    val driver = JdbcSqliteDriver("jdbc:sqlite:${databaseFile.absolutePath}")

    // SQLite keeps a version number for us; a file made before it was used has 0 and is schema 1.
    val schema = AppDatabase.Schema
    if (isNew) {
        schema.create(driver)
        setSchemaVersion(driver, schema.version)
    } else {
        val saved = schemaVersion(driver).let { if (it == 0L) 1L else it }
        if (saved < schema.version) {
            schema.migrate(driver, saved, schema.version)
            setSchemaVersion(driver, schema.version)
        }
    }

    // The login token is in this file, so keep it readable by the user only.
    databaseFile.setReadable(false, false)
    databaseFile.setReadable(true, true)
    databaseFile.setWritable(false, false)
    databaseFile.setWritable(true, true)

    return AppDatabase(driver)
}

private fun schemaVersion(driver: JdbcSqliteDriver): Long =
    driver.executeQuery(null, "PRAGMA user_version", { cursor ->
        QueryResult.Value(if (cursor.next().value) cursor.getLong(0) ?: 0L else 0L)
    }, 0).value

private fun setSchemaVersion(driver: JdbcSqliteDriver, version: Long) {
    driver.execute(null, "PRAGMA user_version = $version", 0)
}

actual fun syncRootDirectory(): String =
    System.getProperty("ctos.sync") ?: File(System.getProperty("user.home"), "CTOS").path
