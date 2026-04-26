package com.slovy.slovymovyapp.i18n

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource

@Composable
fun UiText.resolve(): String = when (this) {
    is UiText.Plain -> value
    is UiText.Resource -> stringResource(key, *args.toTypedArray())
}
