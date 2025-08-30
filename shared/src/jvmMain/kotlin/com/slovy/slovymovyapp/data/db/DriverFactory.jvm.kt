package com.slovy.slovymovyapp.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.slovy.slovymovyapp.db.AppDatabase

actual class DriverFactory actual constructor(androidContext: Any?) {
    actual fun createDriver(dbName: String): SqlDriver {
        return JdbcSqliteDriver(
            url = dbName,
            schema = AppDatabase.Schema
        )
    }
}
