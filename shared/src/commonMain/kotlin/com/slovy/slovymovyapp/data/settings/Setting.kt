package com.slovy.slovymovyapp.data.settings

import kotlinx.serialization.json.JsonElement

/**
 * Generic key/value setting stored as JSON in SqlDelight.
 */
data class Setting(
    val id: Name,
    val value: JsonElement
) {
    enum class Name() {
        TEST_PROPERTY,
        WELCOME_MESSAGE,
        LANGUAGE,
        DICTIONARY,
        DATA_VERSION
    }
}
