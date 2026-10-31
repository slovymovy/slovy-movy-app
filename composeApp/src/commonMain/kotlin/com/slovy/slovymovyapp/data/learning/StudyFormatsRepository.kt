package com.slovy.slovymovyapp.data.learning

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one place the learner's study formats are read from and written to.
 *
 * Reads resolve the stored choice against the language's translation targets: without any, the
 * source-only kinds are the only ones that can be played, so the effective selection plays those
 * alone with every format on, whatever was stored. That keeps a language studiable after its last translation
 * language is removed, when the page to switch the format back on is hidden, and keeps families
 * nothing can play out of the due counts. The stored choice itself is left alone and applies
 * again once a translation language returns.
 *
 * Writes are serialised: the setting is one JSON object for every language, so two quick toggles
 * must not interleave their read-modify-write and persist the older of the two. Reads take the
 * same lock, so a screen opened right after a toggle elsewhere sees the toggle, not the value
 * from before a write still in flight.
 */
class StudyFormatsRepository(
    private val settingsRepository: SettingsRepository,
    private val translationTargets: suspend (Language) -> List<Language>,
) {
    private val mutex = Mutex()

    private companion object {
        val WITHOUT_TARGETS = StudyFormatSelection(StudyFormat.entries.toSet(), playsTranslationKinds = false)
    }

    suspend fun get(language: Language): StudyFormatSelection =
        get(language, translationTargets(language))

    /** As [get], for a caller that has already resolved the language's translation targets. */
    suspend fun get(language: Language, translationTargets: List<Language>): StudyFormatSelection {
        if (!hasChoice(language, translationTargets)) return WITHOUT_TARGETS
        val stored = mutex.withLock { settingsRepository.getStudyFormats(language) } ?: return StudyFormatSelection.ALL
        return StudyFormatSelection(stored, playsTranslationKinds = true)
    }

    /**
     * Whether there is anything to choose for [language]: only with translation targets, since
     * without them the source-only format is the one format there is. The session menu, the
     * Settings row and the page itself all ask this, so none of them opens a page that lies.
     */
    suspend fun hasChoice(language: Language): Boolean =
        hasChoice(language, translationTargets(language))

    fun hasChoice(language: Language, translationTargets: List<Language>): Boolean =
        translationTargets.any { it != language }

    /** The language's translation targets, for a caller that will ask several questions of them. */
    suspend fun translationTargetsOf(language: Language): List<Language> = translationTargets(language)

    /** The selection for a language code; every format for a code the app does not know. */
    suspend fun get(langCode: String): StudyFormatSelection =
        Language.fromCodeOrNull(langCode)?.let { get(it) } ?: StudyFormatSelection.ALL

    suspend fun set(language: Language, selection: StudyFormatSelection) {
        mutex.withLock {
            settingsRepository.setStudyFormats(language, selection.enabled)
        }
    }
}
