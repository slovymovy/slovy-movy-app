package com.slovy.slovymovyapp.data.remote

import com.slovy.slovymovyapp.data.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Downloaded DB filenames lowercase the language code, and both the remote listing
 * ([DataDbManager.fetchAvailableLanguages]) and the installed-file listing
 * ([DataDbManager.listDownloadedDatabases]) recover the [Language] by parsing that segment back
 * through [Language.fromFileNameSegment].
 *
 * A code carrying a script subtag (`zh-Hans`) only survives that round trip because that lookup
 * ignores case, and a failure here is silent rather than loud: the pair is skipped, so the DB is
 * never offered for download nor reported as installed, and nothing raises or logs.
 *
 * The leniency has to stay confined to filename parsing, so the strictness of [Language.fromCodeOrNull]
 * is pinned here too - it is what stops a request for `RU` from validating and then missing the
 * existing `ru` data downstream.
 */
class LanguageFileNameRoundTripTest {

    @Test
    fun dictionaryFileName_segmentResolvesBackToItsLanguage() {
        for (lang in Language.entries) {
            val segment = DataDbManager.dictionaryFileName(lang)
                .removePrefix(DICTIONARY_PREFIX)
                .removeSuffix(DB_EXTENSION)
            assertEquals(
                lang,
                Language.fromFileNameSegment(segment),
                "dictionary filename segment '$segment' must resolve back to $lang"
            )
        }
    }

    @Test
    fun fromCodeOrNull_rejectsCasingThatDiffersFromTheCanonicalCode() {
        for (lang in Language.entries) {
            assertEquals(
                lang,
                Language.fromCodeOrNull(lang.code),
                "${lang.code} must resolve to $lang"
            )
            // Callers validate with fromCodeOrNull and then keep using their own input string, so
            // accepting a variant spelling here would let it flow on into case-sensitive lookups
            // and map keys downstream. Only codes that already differ under case can be checked.
            val swapped = lang.code.uppercase()
            if (swapped != lang.code) {
                assertNull(
                    Language.fromCodeOrNull(swapped),
                    "'$swapped' must not resolve: it is not the canonical spelling of ${lang.code}"
                )
            }
        }
    }

    @Test
    fun translationFileName_bothSegmentsResolveBackToTheirLanguages() {
        for (src in Language.entries) {
            for (tgt in Language.entries) {
                if (src == tgt) continue
                val name = DataDbManager.translationFileName(src, tgt)
                val parts = name
                    .removePrefix(TRANSLATION_PREFIX)
                    .removeSuffix(DB_EXTENSION)
                    .split("_")
                // The parsers require exactly two segments, so a code containing '_' would be
                // dropped just as silently as a case mismatch.
                assertEquals(2, parts.size, "'$name' must split into exactly two language segments")
                assertEquals(
                    src,
                    Language.fromFileNameSegment(parts[0]),
                    "source segment '${parts[0]}' of '$name' must resolve back to $src"
                )
                assertEquals(
                    tgt,
                    Language.fromFileNameSegment(parts[1]),
                    "target segment '${parts[1]}' of '$name' must resolve back to $tgt"
                )
            }
        }
    }

    private companion object {
        // Mirrors the private constants DataDbManager builds its filenames from.
        const val DICTIONARY_PREFIX = "dictionary_"
        const val TRANSLATION_PREFIX = "translation_"
        const val DB_EXTENSION = ".db"
    }
}
