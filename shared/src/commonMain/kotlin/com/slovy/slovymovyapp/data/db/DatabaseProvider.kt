package com.slovy.slovymovyapp.data.db

import app.cash.sqldelight.db.SqlDriver
import com.slovy.slovymovyapp.db.AppDatabase

object DatabaseProvider {
    fun createDatabase(driver: SqlDriver): AppDatabase = AppDatabase(driver)
}
