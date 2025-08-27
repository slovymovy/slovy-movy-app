package com.slovy.slovymovyapp.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.slovy.slovymovyapp.db.AppDatabase

actual class DriverFactory actual constructor(androidContext: Any?) {
    actual fun createDriver(dbName: String): SqlDriver {
        val driver = NativeSqliteDriver(AppDatabase.Schema, dbName)
        // Ensure schema is created for a new database (consistency with other platforms)
        AppDatabase.Schema.create(driver)
        return driver
    }
}
