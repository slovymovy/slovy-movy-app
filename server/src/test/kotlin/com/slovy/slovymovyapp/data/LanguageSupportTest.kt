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
     * The corpus of every learning language is translated into every other one, so each pair of
     * learning languages must pass the pair check in both directions - that is what makes the
     * union in Settings equal to the plain target list for the languages that can be studied.
     */
    @Test
    fun everyLearningLanguageIsATranslationTargetForEveryOtherOne() {
        Language.learningLanguages.forEach { target ->
            Language.learningLanguages.filter { it != target }.forEach { source ->
                assertTrue(
                    target.isTranslationTargetFor(source),
                    "${target.code} has a generated corpus, so it must be offerable to a learner of ${source.code}"
                )
            }
        }
    }

    /**
     * The eleven targets that predate the per-pair model carry no source list, and that must keep
     * meaning "every learning language": French, Chinese and the rest stay offered to a learner of
     * any language exactly as the old global flag offered them.
     */
    @Test
    fun aTargetWithoutASourceListIsOfferedToEveryLearner() {
        val unrestricted = Language.entries.filter { it.supportedForTranslation && it.translationSourceCodes == null }
        assertTrue(Language.FRENCH in unrestricted && Language.CHINESE in unrestricted, "the pre-existing targets carry no source list")
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
            assertTrue(target in Language.translationTargetsFor(Language.ENGLISH), "${target.code} is in English's picker")
            assertFalse(target in Language.translationTargetsFor(Language.RUSSIAN), "${target.code} is not in Russian's picker")
            assertTrue(target in Language.translationTargets, "${target.code} is in the union shown with several learning languages")
        }
        assertEquals(
            "European Portuguese",
            Language.PORTUGUESE.englishName,
            "the English name is what the translation prompt generates from; a neutral name would mix varieties"
        )
    }

    @Test
    fun translationTargetsAreTheUnionOverLearningLanguages() {
        val union = Language.learningLanguages.flatMap { Language.translationTargetsFor(it) }.toSet()
        assertEquals(
            union,
            Language.translationTargets.toSet(),
            "translationTargets must be exactly what some learning language is translated into"
        )
        assertEquals(
            Language.translationTargets,
            Language.translationTargetsFor(Language.learningLanguages),
            "the collection overload must be the same union, in enum order"
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
