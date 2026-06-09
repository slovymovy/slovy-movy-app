package com.slovy.slovymovyapp.i18n

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun UiText.resolve(): String = when (this) {
    is UiText.Plain -> value
    is UiText.Resource -> stringResource(key, *args.toTypedArray())
    is UiText.Plural -> pluralStringResource(key, quantity, *args.toTypedArray())
    is UiText.Joined -> items.map { it.resolve() }.joinToString(separator)
}
