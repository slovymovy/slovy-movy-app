package com.slovy.slovymovyapp.data.db

import app.cash.sqldelight.db.SqlDriver

// Expect/actual DriverFactory providing a platform-specific SqlDriver.
// On Android, androidContext should be an android.content.Context; on other platforms pass null or ignore.
expect class DriverFactory(androidContext: Any? = null) {
    fun createDriver(dbName: String = DatabaseConstants.DEFAULT_DATABASE_NAME): SqlDriver
}
