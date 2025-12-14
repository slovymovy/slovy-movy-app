package com.slovy.slovymovyapp.ingestion

import com.slovy.slovymovyapp.dictionary.DictionaryDatabase
import com.slovy.slovymovyapp.translation.TranslationDatabase

/**
 * Interface for managing database access during ingestion.
 *
 * Implementations provide platform-specific database creation and management.
 * The server module provides a JVM-only implementation using JDBC drivers,
 * while mobile apps can provide their own implementations if needed.
 */
interface IngestionDbManager {
    /**
     * Opens or creates a dictionary database for the given language code.
     *
     * @param langCode The language code (e.g., "en", "ru", "nl", "pl")
     * @return The dictionary database instance
     */
    fun openDictionary(langCode: String): DictionaryDatabase

    /**
     * Opens or creates a translation database for the given language pair.
     *
     * @param sourceLang The source language code
     * @param targetLang The target language code
     * @return The translation database instance
     */
    fun openTranslation(sourceLang: String, targetLang: String): TranslationDatabase
}
