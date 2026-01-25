package com.slovy.slovymovyapp.data.favorites

import com.slovy.slovymovyapp.data.Language

data class Favorite(
    val senseId: String,
    val language: Language,
    val lemma: String,
    val createdAt: Long = 0
)
