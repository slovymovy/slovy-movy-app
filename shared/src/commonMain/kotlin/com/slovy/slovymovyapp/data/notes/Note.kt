package com.slovy.slovymovyapp.data.notes

data class Note(
    val id: String,
    val title: String,
    val content: String?,
    val createdAt: Long,
)
