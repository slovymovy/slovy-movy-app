package com.slovy.slovymovyapp.data.notes

import com.slovy.slovymovyapp.db.AppDatabase

class NotesRepository(private val db: AppDatabase) {

    fun insert(note: Note) {
        db.notesQueries.insertNote(
            id = note.id,
            title = note.title,
            content = note.content,
            created_at = note.createdAt
        )
    }

    fun getAll(): List<Note> = db.notesQueries.selectAll().executeAsList().map { row ->
        Note(id = row.id, title = row.title, content = row.content, createdAt = row.created_at)
    }

    fun getById(id: String): Note? = db.notesQueries.selectById(id).executeAsOneOrNull()?.let { row ->
        Note(id = row.id, title = row.title, content = row.content, createdAt = row.created_at)
    }

    fun deleteById(id: String) {
        db.notesQueries.deleteById(id)
    }
}
