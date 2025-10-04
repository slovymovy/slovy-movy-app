package com.slovy.slovymovyapp.data.favorites

data class Favorite(
    val senseId: String,
    val targetLang: String,
    val lemma: String,
    val createdAt: Long = 0
)
