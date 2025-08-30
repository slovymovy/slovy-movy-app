package com.slovy.slovymovyapp.data.db

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.db.SqlDriver
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.db.AppDatabase
import com.slovy.slovymovyapp.db.Settings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

object DatabaseProvider {
    fun createDatabase(driver: SqlDriver): AppDatabase = AppDatabase(
        driver = driver,
        settingsAdapter = Settings.Adapter(
            json_valueAdapter = object : ColumnAdapter<JsonElement, String> {
                override fun decode(databaseValue: String): JsonElement {
                    return Json.parseToJsonElement(databaseValue)
                }

                override fun encode(value: JsonElement): String {
                    return value.toString()
                }
            },
            idAdapter = object : ColumnAdapter<Setting.Name, String> {
                override fun decode(databaseValue: String): Setting.Name {
                    return Setting.Name.valueOf(databaseValue)
                }

                override fun encode(value: Setting.Name): String {
                    return value.name
                }
            }
        ),
    )
}
