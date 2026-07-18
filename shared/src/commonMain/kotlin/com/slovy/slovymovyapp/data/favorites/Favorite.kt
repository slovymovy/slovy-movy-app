package com.slovy.slovymovyapp.data.favorites

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.recovery.RecoverableSense

data class Favorite(
    override val senseId: String,
    override val language: Language,
    override val lemma: String,
    val createdAt: Long = 0
) : RecoverableSense
