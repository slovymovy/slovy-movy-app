package com.slovy.slovymovyapp.data

import com.slovy.slovymovyapp.ingestion.LANG_TO_SOURCE_FILE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    /**
     * The targets that predate the per-pair model, every learning language among them, carry no
     * source list, and that must keep meaning "every learning language": French, Chinese and the
     * rest stay offered to a learner of any language exactly as the old global flag offered them.
     */
    @Test
    fun aTargetWithoutASourceListIsOfferedToEveryLearner() {
        val unrestricted = Language.entries.filter { it.supportedForTranslation && it.translationSourceCodes == null }
        assertTrue(
            unrestricted.containsAll(Language.learningLanguages + Language.FRENCH + Language.CHINESE),
            "the pre-existing targets and every learning language carry no source list"
        )
        unrestricted.forEach { target ->
            Language.learningLanguages.filter { it != target }.forEach { source ->
                assertTrue(
                    target.isTranslationTargetFor(source),
                    "${target.code} lists no sources, so it must stay a target for ${source.code}"
                )
            }
        }
    }

    @Test
    fun aLanguageIsNeverATranslationTargetForItself() {
        Language.entries.forEach { language ->
            assertFalse(
                language.isTranslationTargetFor(language),
                "${language.code} must not be offered as a translation of itself"
            )
        }
    }

    @Test
    fun onlyLearningLanguagesCanBeTranslationSources() {
        val learningCodes = Language.learningLanguages.map { it.code }.toSet()
        Language.entries.forEach { target ->
            target.translationSourceCodes?.forEach { code ->
                assertTrue(
                    code in learningCodes,
                    "${target.code} names '$code' as a source, but only a learning language has a corpus to translate"
                )
            }
            Language.entries.filter { !it.supportedForLearning }.forEach { source ->
                assertFalse(
                    target.isTranslationTargetFor(source),
                    "${target.code} cannot be a target for ${source.code}: nothing is generated for it"
                )
            }
        }
    }

    /**
     * Japanese and European Portuguese are generated from English only. Russian, Dutch, Polish and
     * German learners must neither be offered them nor have them requested from `/word`.
     */
    @Test
    fun japaneseAndPortugueseAreTranslatedFromEnglishOnly() {
        listOf(Language.JAPANESE, Language.PORTUGUESE).forEach { target ->
            assertTrue(target.isTranslationTargetFor(Language.ENGLISH), "${target.code} is generated for English")
            Language.learningLanguages.filter { it != Language.ENGLISH }.forEach { source ->
                assertFalse(
                    target.isTranslationTargetFor(source),
                    "${target.code} is not generated for ${source.code}, so it must not be offered to its learners"
                )
            }
        }
        assertEquals(
            "European Portuguese",
            Language.PORTUGUESE.englishName,
            "the English name is what the translation prompt generates from; a neutral name would mix varieties"
        )
    }

    @Test
    fun translationTargetsForNoSourceIsTheFullUnion() {
        assertEquals(
            Language.translationTargetsFor(Language.learningLanguages),
            Language.translationTargetsFor(emptyList()),
            "before a learning language is chosen a picker lists everything some learner is translated into"
        )
        assertEquals(
            listOf(Language.ENGLISH, Language.POLISH),
            Language.translationTargetsFor(listOf(Language.RUSSIAN))
                .filter { it in setOf(Language.ENGLISH, Language.POLISH, Language.JAPANESE) },
            "a single source lists only its own targets, in enum order"
        )
    }

    @Test
    fun translationTargetsAmongNarrowsASelectionInItsOwnOrder() {
        assertEquals(
            listOf(Language.POLISH, Language.ENGLISH),
            Language.RUSSIAN.translationTargetsAmong(
                listOf(Language.POLISH, Language.RUSSIAN, Language.JAPANESE, Language.ENGLISH, Language.POLISH)
            ),
            "the source itself and English-only Japanese are dropped, duplicates collapse, order is kept"
        )
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
