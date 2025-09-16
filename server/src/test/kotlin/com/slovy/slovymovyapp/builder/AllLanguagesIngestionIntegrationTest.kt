@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.slovy.slovymovyapp.builder

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AllLanguagesIngestionIntegrationTest {

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        allowTrailingComma = false
    }

    private val langs = listOf("en", "ru", "nl", "pl")

    @Test
    fun ingest_all_languages_and_files() {
        val outDir = Files.createTempDirectory("ingestion_all_langs_test").toFile()
        val serverDbManager = ServerDbManager(outDir)
        val builder = JsonIngestionBuilder(serverDbManager)

        langs.forEach { lang ->
            val processedFiles = listResourceJsonFiles("processed_json_files/$lang")
            assertTrue(processedFiles.isNotEmpty(), "No processed files found for $lang")
            processedFiles.forEach { pFile ->
                val rawFile = resourceFile("db_extract/$lang/${pFile.name}")
                // run ingestion
                builder.ingest(pFile, rawFile)

                // Validate dictionary DB: lemma existence and forms from raw
                val dictDb = serverDbManager.openDictionary(lang)
                val dq = dictDb.dictionaryQueries
                val raw = json.decodeFromString(ExtractedWordData.serializer(), rawFile.readText())
                val processed = json.decodeFromString(LanguageCardResponse.serializer(), pFile.readText())
                val word = raw.word
                val lemmas = dq.selectLemmasByWord(word.lowercase()).executeAsList()
                assertTrue(lemmas.isNotEmpty(), "Lemma should exist for '$word' in $lang from ${pFile.name}")

                // Determine native entries and forms to check
                val nativeKey = LANG_TO_SOURCE_FILE[lang]
                val nativeEntries = nativeKey?.let { raw.sourceFileToEntries[it] }.orEmpty()
                val entriesCandidates =
                    if (nativeEntries.any { it.forms.isNotEmpty() }) nativeEntries else raw.sourceFileToEntries.values.flatten()
                // Filter to entries that are actually referenced by processed via sense_id (processed-as-truth)
                val processedSenseIds = processed.entries.flatMap { it.senses }.map { uuidParse(it.senseId) }.toSet()
                val entriesUsedByProcessed = entriesCandidates.filter { e ->
                    e.senses.any { s -> processedSenseIds.contains(uuidParse(s.senseId.toString())) }
                }
                // Validate presence of at least first form (if any) from entries used by processed
                val entry = entriesUsedByProcessed.firstOrNull { it.forms.isNotEmpty() }
                val anyForm = entry?.forms?.firstOrNull()?.form
                if (entry != null && anyForm != null) {
                    val forms = dq.selectFormsByNormalized(unaccent(anyForm)).executeAsList()
                    assertTrue(forms.isNotEmpty(), "Form '$anyForm' should exist for '$word' in $lang")
                }

                // Validate translation DBs for each target language in processed
                val targetLangs = collectTargetLanguages(processed)
                processed.entries.forEach { entry ->
                    entry.senses.forEach { sense ->
                        val senseId = uuidParse(sense.senseId)
                        targetLangs.forEach { trg ->
                            val trDb = serverDbManager.openTranslation(lang, trg)
                            val tq = trDb.translationQueries
                            val defExpected = sense.targetLangDefinitions[trg]
                            if (defExpected != null) {
                                val defActual = tq.selectDefinitionsBySense(senseId).executeAsList().singleOrNull()
                                assertEquals(
                                    defExpected,
                                    defActual,
                                    "Definition mismatch for $lang->$trg in ${pFile.name}"
                                )
                            }
                            val expectedTranslations = sense.translations[trg]
                            if (!expectedTranslations.isNullOrEmpty()) {
                                val rows = tq.selectSenseTranslationsBySense(senseId).executeAsList()
                                val words = rows.map { it.target_lang_word }
                                val expectedWords = expectedTranslations.map { it.targetLangWord }
                                assertEquals(
                                    expectedWords,
                                    words,
                                    "Translation words order mismatch for $lang->$trg in ${pFile.name}"
                                )
                            }
                            // Example index 0 if exists
                            val ex0 = sense.examples.firstOrNull()?.targetLangTranslations?.get(trg)
                            if (ex0 != null) {
                                val exActual = tq.selectExampleTranslations(senseId, 0).executeAsOneOrNull()
                                assertEquals(
                                    ex0,
                                    exActual,
                                    "Example[0] translation mismatch for $lang->$trg in ${pFile.name}"
                                )
                            }
                        }
                    }
                }
            }
        }

        print(outDir)
    }

    private fun listResourceJsonFiles(path: String): List<File> {
        val cl = Thread.currentThread().contextClassLoader
        val url = cl.getResource(path) ?: return emptyList()
        val root = File(url.toURI())
        return root.walkTopDown().filter { it.isFile && it.extension.equals("json", ignoreCase = true) }.toList()
    }

    private fun resourceFile(path: String): File {
        val cl = Thread.currentThread().contextClassLoader
        val url = cl.getResource(path) ?: error("Resource not found: $path")
        return File(url.toURI())
    }

    private fun collectTargetLanguages(p: LanguageCardResponse): Set<String> {
        val set = mutableSetOf<String>()
        p.entries.forEach { e ->
            e.senses.forEach { s ->
                set += s.targetLangDefinitions.keys
                set += s.translations.keys
                s.examples.forEach { ex -> set += ex.targetLangTranslations.keys }
            }
        }
        return set
    }
}
