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
        WELCOME_COMPLETED,
        LANGUAGE,

        /**
         * The last picked learning language: set during onboarding, then whenever a language
         * with an installed dictionary is picked in Search, Favorites, or Stats, and moved to
         * another language when its dictionary is deleted. Shared text opens in it. It decides
         * the startup download check and the `learning_lang` analytics property, and is the
         * fallback where a screen has no saved language. Stored by name, so renaming the
         * constant needs an app DB migration.
         */
        DICTIONARY,
        DATA_VERSION,
        ENABLED_VOICES,
        VOICE_SETUP_SHOWN,
        SEARCH_LANGUAGE,
        FAVORITES_LANGUAGE,
        STATS_LANGUAGE,
        DEVELOPER_MODE,
        LEGACY_VOICE_MIGRATION_DONE,
        STUDY_AUTOPLAY,
        SPELLING_BACKFILL_DONE,
    }
}
