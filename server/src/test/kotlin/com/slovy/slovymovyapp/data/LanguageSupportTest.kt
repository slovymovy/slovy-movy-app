package com.slovy.slovymovyapp.data

import com.slovy.slovymovyapp.ingestion.LANG_TO_SOURCE_FILE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [Language.supportedForLearning] is a claim about data that lives somewhere else: a language can
 * only be studied if there is a Wiktextract source mapping behind it. A flag and the data it
 * describes drift silently - setting the flag costs nothing and nothing reads it back - so the two
 * are pinned against each other here.
 */
class LanguageSupportTest {

    @Test
    fun learningLanguagesAreExactlyTheOnesWithASourceMapping() {
        assertEquals(
            LANG_TO_SOURCE_FILE.keys,
            Language.learningLanguages.map { it.code }.toSet(),
            "a language is studiable exactly when its extract is mapped in LANG_TO_SOURCE_FILE"
        )
    }

    @Test
    fun everyLearningLanguageIsAlsoATranslationTarget() {
        Language.learningLanguages.forEach { language ->
            assertTrue(
                language.supportedForTranslation,
                "${language.code} has a generated corpus, so it must be offerable as a target too"
            )
        }
    }

    /**
     * A code is the `translation_{src}_{tgt}.db` filename segment, the `/word` route's
     * `translations` value, a `lang_code` column value and the TTS locale tag. `zh-Hans` was tried
     * once and reverted: a script subtag emptied the TTS voice list for every language and made the
     * translation DB filename unparseable, both silently. Nothing else stops the next one.
     */
    @Test
    fun everyCodeIsABareLowercaseSubtag() {
        Language.entries.forEach { language ->
            assertTrue(
                language.code.matches(Regex("[a-z]{2,3}")),
                "'${language.code}' must be a bare lowercase language subtag - it is a filename " +
                    "segment, a column value and a locale tag, not just a display key"
            )
        }
        assertEquals(
            Language.entries.size,
            Language.entries.map { it.code }.toSet().size,
            "language codes must be unique; lookups resolve by code"
        )
    }
}
