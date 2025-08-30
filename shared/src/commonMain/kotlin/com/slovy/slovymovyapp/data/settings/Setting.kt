package com.slovy.slovymovyapp.data.settings

import kotlinx.serialization.json.JsonElement

data class Setting(
    val id: Name,
    val value: JsonElement
) {
    enum class Name() {
        TEST_PROPERTY,
        WELCOME_MESSAGE
    }
}
