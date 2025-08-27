package com.slovy.slovymovyapp.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.slovy.slovymovyapp.db.AppDatabase

actual class DriverFactory actual constructor(androidContext: Any?) {
    actual fun createDriver(dbName: String): SqlDriver {
        // Use in-memory if requested, otherwise a file-backed DB
        val connectionString = if (dbName == DatabaseConstants.IN_MEMORY_DATABASE_NAME) {
            "jdbc:sqlite::memory:"
        } else {
            "jdbc:sqlite:$dbName"
        }
        val driver = JdbcSqliteDriver(connectionString)
        // Ensure schema is created for a new database
        AppDatabase.Schema.create(driver)
        return driver
    }
}
