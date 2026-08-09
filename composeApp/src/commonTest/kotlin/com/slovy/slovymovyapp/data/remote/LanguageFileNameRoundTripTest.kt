package com.slovy.slovymovyapp.data.remote

import com.slovy.slovymovyapp.data.Language
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Downloaded DB filenames lowercase the language code, and both the remote listing
 * ([DataDbManager.fetchAvailableLanguages]) and the installed-file listing
 * ([DataDbManager.listDownloadedDatabases]) recover the [Language] by parsing that segment back
 * through [Language.fromCodeOrNull].
 *
 * A code carrying a script subtag (`zh-Hans`) only survives that round trip because the lookup
 * ignores case, and a failure here is silent rather than loud: the pair is skipped, so the DB is
 * never offered for download nor reported as installed, and nothing raises or logs. These tests
 * pin the round trip for every language so a future code cannot quietly break it.
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
                Language.fromCodeOrNull(segment),
                "dictionary filename segment '$segment' must resolve back to $lang"
            )
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
                    Language.fromCodeOrNull(parts[0]),
                    "source segment '${parts[0]}' of '$name' must resolve back to $src"
                )
                assertEquals(
                    tgt,
                    Language.fromCodeOrNull(parts[1]),
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
