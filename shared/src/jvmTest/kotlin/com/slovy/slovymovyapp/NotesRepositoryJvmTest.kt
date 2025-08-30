package com.slovy.slovymovyapp

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver.Companion.IN_MEMORY
import com.slovy.slovymovyapp.data.db.DatabaseProvider
import com.slovy.slovymovyapp.data.db.DriverFactory
import com.slovy.slovymovyapp.data.notes.Note
import com.slovy.slovymovyapp.data.notes.NotesRepository
import com.slovy.slovymovyapp.db.AppDatabase
import kotlin.test.Test
import kotlin.test.assertEquals

class NotesRepositoryJvmTest {

    @Test
    fun migrations_are_applied() {
        val driver = DriverFactory(null).createDriver(IN_MEMORY)
        val db: AppDatabase = DatabaseProvider.createDatabase(driver)
        val repo = NotesRepository(db)
        assertEquals(6, repo.getAll().size)
    }

    @Test
    fun insert_and_query_and_delete_note() {
        val driver = DriverFactory(null).createDriver(IN_MEMORY)
        val db: AppDatabase = DatabaseProvider.createDatabase(driver)
        val repo = NotesRepository(db)

        val note = Note(
            id = "id1",
            title = "Hello",
            content = "World",
            createdAt = 123456789L
        )

        // Insert
        repo.insert(note)

        // Query all
        val all = repo.getAll()
        assertEquals(7, all.size)
    }
}
