package com.slovy.slovymovyapp.data.notes

import com.slovy.slovymovyapp.data.db.DatabaseConstants

data class Note(
    val id: String,
    val title: String,
    val content: String?,
    val createdAt: Long,
) {
    init {
        require(id.isNotBlank()) { "Note ID cannot be blank" }
        require(title.isNotBlank()) { "Note title cannot be blank" }
        require(title.length <= DatabaseConstants.MAX_TITLE_LENGTH) { 
            "Note title cannot exceed ${DatabaseConstants.MAX_TITLE_LENGTH} characters" 
        }
        content?.let { 
            require(it.length <= DatabaseConstants.MAX_CONTENT_LENGTH) { 
                "Note content cannot exceed ${DatabaseConstants.MAX_CONTENT_LENGTH} characters" 
            }
        }
        require(createdAt > 0) { "Note creation time must be positive" }
    }
}
