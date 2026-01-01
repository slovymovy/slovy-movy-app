package com.slovy.slovymovyapp.api

import com.slovy.slovymovyapp.ingestion.LanguageCardResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Stream stages for the /word API endpoint.
 * - base: Initial word data with senses (without translations or with existing translations)
 * - translated: Updated word data with newly added translations
 */
@Serializable
enum class WordStreamStage {
    @SerialName("base")
    BASE,

    @SerialName("translated")
    TRANSLATED
}

/**
 * A chunk of streaming word data from the server.
 * The server returns NDJSON (newline-delimited JSON) with one chunk per line.
 */
@Serializable
data class WordStreamChunk(
    val stage: WordStreamStage,
    val payload: LanguageCardResponse
)
