package com.ctos.client.data

import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.ctos.client.AndroidContext
import com.ctos.client.db.AppDatabase
import java.io.File

actual fun createDatabase(): AppDatabase =
    AppDatabase(AndroidSqliteDriver(AppDatabase.Schema, AndroidContext.application, "ctos.db"))

// The app's own folder on shared storage: it needs no permission, and is removed with the app.
actual fun syncRootDirectory(): String {
    val context = AndroidContext.application
    return File(context.getExternalFilesDir(null) ?: context.filesDir, "CTOS").path
}
