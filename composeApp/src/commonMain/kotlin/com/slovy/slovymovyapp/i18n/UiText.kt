package com.slovy.slovymovyapp.i18n

import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource

sealed interface UiText {
    data class Plain(val value: String) : UiText
    data class Resource(
        val key: StringResource,
        val args: List<Any> = emptyList()
    ) : UiText
    data class Plural(
        val key: PluralStringResource,
        val quantity: Int,
        val args: List<Any> = emptyList()
    ) : UiText
}
