package com.slovy.slovymovyapp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.slovy.slovymovyapp.data.db.DatabaseProvider
import com.slovy.slovymovyapp.data.db.DriverFactory
import com.slovy.slovymovyapp.data.notes.Note
import com.slovy.slovymovyapp.data.notes.NotesRepository
import com.slovy.slovymovyapp.db.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotesRepositoryAndroidInstrumentedTest {
    @Test
    fun repository_works_on_android_with_real_db() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Use a unique file name per run to avoid test pollution
        val dbName = "instrumented-${System.currentTimeMillis()}.db"
        val driver = DriverFactory(context).createDriver(dbName)
        val db: AppDatabase = DatabaseProvider.createDatabase(driver)
        val repo = NotesRepository(db)
        val note = Note("id-android", "Android", "Works", 1L)
        repo.insert(note)
        val all = repo.getAll()
        assertEquals(1, all.size)
        assertEquals("Android", all.first().title)
    }
}
