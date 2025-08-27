package com.slovy.slovymovyapp

import com.slovy.slovymovyapp.data.db.DatabaseProvider
import com.slovy.slovymovyapp.data.db.DriverFactory
import com.slovy.slovymovyapp.data.notes.Note
import com.slovy.slovymovyapp.data.notes.NotesRepository
import com.slovy.slovymovyapp.db.AppDatabase
import kotlin.test.Test
import kotlin.test.assertEquals

class NotesRepositoryServerTest {
    @Test
    fun repository_works_on_server_with_in_memory_db() {
        val driver = DriverFactory(null).createDriver(":memory:")
        val db: AppDatabase = DatabaseProvider.createDatabase(driver)
        val repo = NotesRepository(db)
        val note = Note("id-server", "Server", "Works", 1L)
        repo.insert(note)
        val all = repo.getAll()
        assertEquals(1, all.size)
        assertEquals("Server", all.first().title)
    }
}
