package com.slovy.slovymovyapp.data.notes

import com.slovy.slovymovyapp.db.AppDatabase

class NotesRepository(private val db: AppDatabase) {

    /**
     * Inserts or replaces a note in the database
     */
    fun insert(note: Note) {
        try {
            db.notesQueries.insertNote(
                id = note.id,
                title = note.title,
                content = note.content,
                created_at = note.createdAt
            )
        } catch (e: Exception) {
            throw RepositoryException("Failed to insert note with id ${note.id}", e)
        }
    }

    /**
     * Updates an existing note (same as insert due to INSERT OR REPLACE)
     */
    fun update(note: Note) = insert(note)

    /**
     * Retrieves all notes ordered by creation time (newest first)
     */
    fun getAll(): List<Note> = try {
        db.notesQueries.selectAll().executeAsList().map { row ->
            Note(id = row.id, title = row.title, content = row.content, createdAt = row.created_at)
        }
    } catch (e: Exception) {
        throw RepositoryException("Failed to retrieve all notes", e)
    }

    /**
     * Retrieves a note by its ID
     */
    fun getById(id: String): Note? = try {
        require(id.isNotBlank()) { "Note ID cannot be blank" }
        db.notesQueries.selectById(id).executeAsOneOrNull()?.let { row ->
            Note(id = row.id, title = row.title, content = row.content, createdAt = row.created_at)
        }
    } catch (e: Exception) {
        throw RepositoryException("Failed to retrieve note with id $id", e)
    }

    /**
     * Deletes a note by its ID
     */
    fun deleteById(id: String) {
        try {
            require(id.isNotBlank()) { "Note ID cannot be blank" }
            db.notesQueries.deleteById(id)
        } catch (e: Exception) {
            throw RepositoryException("Failed to delete note with id $id", e)
        }
    }
}

/**
 * Exception thrown by repository operations
 */
class RepositoryException(message: String, cause: Throwable? = null) : Exception(message, cause)
