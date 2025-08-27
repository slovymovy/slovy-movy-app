package com.slovy.slovymovyapp

import com.slovy.slovymovyapp.data.db.DriverFactory
import com.slovy.slovymovyapp.data.db.DatabaseProvider
import com.slovy.slovymovyapp.data.db.DatabaseConstants
import com.slovy.slovymovyapp.data.notes.Note
import com.slovy.slovymovyapp.data.notes.NotesRepository
import com.slovy.slovymovyapp.db.AppDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NotesRepositoryJvmTest {
    @Test
    fun insert_and_query_and_delete_note() {
        val driver = DriverFactory(null).createDriver(DatabaseConstants.IN_MEMORY_DATABASE_NAME)
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
        assertEquals(1, all.size)
        val loaded = all.first()
        assertEquals(note.id, loaded.id)
        assertEquals(note.title, loaded.title)
        assertEquals(note.content, loaded.content)
        assertEquals(note.createdAt, loaded.createdAt)

        // Query by id
        val byId = repo.getById("id1")
        assertNotNull(byId)
        assertEquals("Hello", byId.title)

        // Delete
        repo.deleteById("id1")
        assertEquals(0, repo.getAll().size)
        assertNull(repo.getById("id1"))
    }
}
