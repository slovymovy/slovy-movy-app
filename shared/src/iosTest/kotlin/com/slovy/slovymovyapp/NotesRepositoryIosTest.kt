package com.slovy.slovymovyapp

import com.slovy.slovymovyapp.data.db.DatabaseProvider
import com.slovy.slovymovyapp.data.db.DriverFactory
import com.slovy.slovymovyapp.data.notes.Note
import com.slovy.slovymovyapp.data.notes.NotesRepository
import com.slovy.slovymovyapp.db.AppDatabase
import kotlin.test.Test
import kotlin.test.assertEquals

class NotesRepositoryIosTest {
    @Test
    fun repository_works_on_ios_with_native_db() {
        val driver = DriverFactory(null).createDriver(":memory:")
        val db: AppDatabase = DatabaseProvider.createDatabase(driver)
        val repo = NotesRepository(db)
        val note = Note("id-ios", "iOS", "Works", 1L)
        repo.insert(note)
        val all = repo.getAll()
        assertEquals(1, all.size)
        assertEquals("iOS", all.first().title)
    }
}
