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
        DICTIONARY,
        DATA_VERSION,
        ENABLED_VOICES,
        VOICE_SETUP_SHOWN,
        SEARCH_LANGUAGE,
        FAVORITES_LANGUAGE,
        STATS_LANGUAGE,
        DEVELOPER_MODE,
        LEGACY_VOICE_MIGRATION_DONE,

        /**
         * Set when a translation language is added, cleared once lemma recovery has run. Saved
         * words already in the local DBs have no data for the new language, and only a recovery
         * pass re-fetches them; the next startup routes through the download/finalize flow, which
         * runs that pass with progress.
         */
        PENDING_LEMMA_RECOVERY
    }
}
