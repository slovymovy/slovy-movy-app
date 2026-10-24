package com.slovy.slovymovyapp

import com.slovy.slovymovyapp.data.Language
import kotlinx.serialization.Serializable

/**
 * Typed navigation destinations for the app's single [androidx.navigation.compose.NavHost].
 *
 * Route arguments are plain strings rather than [Language] values: the Kotlin/Native side of
 * Compose Navigation cannot round-trip an enum through a route, so each destination stores the
 * language code and exposes the parsed value as a property.
 */
@Serializable
internal sealed interface AppDestination {
    @Serializable
    data object Welcome : AppDestination

    /**
     * Downloads the databases the app is missing. With [addedTranslationCode] set, the run comes
     * from Settings and adds that one translation target to every installed dictionary, returning
     * to Settings when it ends; otherwise it is the initial setup for the chosen learning language.
     */
    @Serializable
    data class DownloadSetup(val addedTranslationCode: String?) : AppDestination

    @Serializable
    data object SetupLanguages : AppDestination

    @Serializable
    data object Search : AppDestination

    @Serializable
    data object Favorites : AppDestination

    @Serializable
    data object Stats : AppDestination

    @Serializable
    data class StudySession(
        val langCode: String,
    ) : AppDestination

    @Serializable
    data class WordDetail(
        @Deprecated("temporal hack, looks like IOS can't handle enums here")
        val dictionaryLanguageCode: String,
        val lemma: String,
        val targetSenseId: String? = null,
        val translationLanguageCodes: List<String>? = null,
    ) : AppDestination {
        @Suppress("DEPRECATION")
        val dictionaryLanguage: Language
            get() = Language.fromCode(dictionaryLanguageCode)

        val translationLanguages: List<Language>?
            get() = translationLanguageCodes?.mapNotNull { Language.fromCodeOrNull(it) }
    }

    @Serializable
    data object Settings : AppDestination

    @Serializable
    data object Developer : AppDestination

    @Serializable
    data class TextReader(val languageCode: String) : AppDestination

    @Serializable
    data class Error(val message: String) : AppDestination

    @Serializable
    data object DataVersionMismatch : AppDestination

    @Serializable
    data class ListDetail(val languageCode: String, val listId: String) : AppDestination
}
