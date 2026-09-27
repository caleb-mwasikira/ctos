package com.ctos.client.data

import com.ctos.client.db.AppDatabase

/** The app's small local database: the login session, and what each department's local folder held. */
expect fun createDatabase(): AppDatabase

/** The folder that holds one sub-folder per organization, and inside it one per department. */
expect fun syncRootDirectory(): String
