package com.slovy.slovymovyapp.i18n

import com.slovy.slovymovyapp.data.remote.NetworkError
import com.slovy.slovymovyapp.data.remote.NetworkErrorClassifier
import slovymovyapp.composeapp.generated.resources.Res
import slovymovyapp.composeapp.generated.resources.network_error_insufficient_storage
import slovymovyapp.composeapp.generated.resources.network_error_offline
import slovymovyapp.composeapp.generated.resources.network_error_server
import slovymovyapp.composeapp.generated.resources.network_error_server_with_code
import slovymovyapp.composeapp.generated.resources.network_error_timeout
import slovymovyapp.composeapp.generated.resources.network_error_unknown

/**
 * Localized copy for a classified network failure.
 *
 * [NetworkError.userMessage] stays English on purpose: it is what goes into exception messages
 * and logs. Anything shown to a person goes through here instead, so the five network messages
 * follow the UI language like the rest of the app.
 */
fun NetworkError.toUiText(): UiText = when (this) {
    is NetworkError.Offline -> UiText.Resource(Res.string.network_error_offline)
    is NetworkError.Timeout -> UiText.Resource(Res.string.network_error_timeout)
    is NetworkError.ServerError ->
        if (statusCode > 0) {
            UiText.Resource(Res.string.network_error_server_with_code, listOf(statusCode))
        } else {
            UiText.Resource(Res.string.network_error_server)
        }

    is NetworkError.InsufficientStorage -> UiText.Resource(Res.string.network_error_insufficient_storage)
    is NetworkError.Unknown -> UiText.Resource(Res.string.network_error_unknown)
}

/** Shorthand for the common `classify(e).toUiText()` pairing at ViewModel catch sites. */
fun networkErrorUiText(throwable: Throwable): UiText =
    NetworkErrorClassifier.classify(throwable).toUiText()
