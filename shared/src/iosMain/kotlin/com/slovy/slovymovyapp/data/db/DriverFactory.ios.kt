package com.slovy.slovymovyapp.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.slovy.slovymovyapp.db.AppDatabase

actual class DriverFactory actual constructor(androidContext: Any?) {
    actual fun createDriver(dbName: String): SqlDriver {
        return NativeSqliteDriver(AppDatabase.Schema, dbName)
    }
}
