package com.slovy.slovymovyapp.data.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.slovy.slovymovyapp.db.AppDatabase

actual class DriverFactory actual constructor(private val androidContext: Any?) {
    actual fun createDriver(dbName: String): SqlDriver {
        val ctx = androidContext as? Context
            ?: error("Android Context is required to create database driver on Android")
        
        val driver = AndroidSqliteDriver(AppDatabase.Schema, ctx, dbName)
        
        // Ensure schema is created for a new database (consistency with JVM implementation)
        AppDatabase.Schema.create(driver)
        
        return driver
    }
}
