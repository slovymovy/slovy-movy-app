@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.slovy.slovymovyapp.builder

import com.slovy.slovymovyapp.data.dictionary.DictionaryPos
import com.slovy.slovymovyapp.ingestion.*
import com.slovy.slovymovyapp.util.stripAccents
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
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

        langs.forEach { lang ->
            // Build frequency map from test resources
            val frequencyMap = buildTestFrequencyMap(lang)
            val builder = JsonIngestionBuilder({ from, to -> serverDbManager.openTranslation(from, to) }, frequencyMap)
            val processedFiles = listResourceJsonFiles("processed_json_files/$lang")
            assertTrue(processedFiles.isNotEmpty(), "No processed files found for $lang")
            val dictDb = serverDbManager.openDictionary(lang)
            val dictQ = dictDb.dictionaryQueries
            processedFiles.forEach { pFile ->
                val rawFile = resourceFile("db_extract/$lang/${pFile.name}")
                // run ingestion
                builder.ingest(
                    pFile.readText(),
                    rawFile.readText(),
                    dictDb,
                )

                // Validate dictionary DB: lemma existence and forms from raw
                val raw = json.decodeFromString(ExtractedWordData.serializer(), rawFile.readText())
                val processed = json.decodeFromString(LanguageCardResponse.serializer(), pFile.readText())
                val word = raw.word
                val lemmas = dictQ.selectLemmasByWord(lang, word.lowercase()).executeAsList()
                assertTrue(lemmas.isNotEmpty(), "Lemma should exist for '$word' in $lang from ${pFile.name}")

                // Determine native entries and forms to check
                val nativeKey = LANG_TO_SOURCE_FILE[lang]
                val nativeEntries = nativeKey?.let { raw.sourceFileToEntries[it] }.orEmpty()
                val entriesCandidates =
                    if (nativeEntries.any { it.forms.isNotEmpty() }) nativeEntries else raw.sourceFileToEntries.values.flatten()
                // Only keep entries whose POS exists in processed entries (forms map by POS)
                val processedPosSet = processed.entries.mapNotNull { mapPos(it.pos) }.toSet()
                val entriesUsedByProcessedPos = entriesCandidates.filter { e ->
                    val pos = mapPos(e.pos)
                    pos != null && processedPosSet.contains(pos)
                }

                // Validate presence of all UNIQUE forms per POS from entries used by processed
                data class FormKey(val form: String, val formNormalized: String, val tags: Set<String>)

                val expectedFormsByPos = entriesUsedByProcessedPos
                    .groupBy { it.pos.lowercase() }
                    .mapValues { (_, entries) ->
                        entries.flatMap { it.forms }.map { f ->
                            FormKey(f.form, stripAccents(f.form), f.tags.toSet())
                        }.toSet()
                    }

                if (expectedFormsByPos.isNotEmpty()) {
                    val lemmaIds = lemmas.map { it.id }
                    val lemmaPosRows = lemmaIds.flatMap { lemmaId ->
                        dictQ.selectLemmaPosByLemmaId(lemmaId).executeAsList()
                    }

                    lemmaPosRows.forEach { lemmaPos ->
                        val posKey = lemmaPos.pos.name.lowercase()
                        val expectedForPos = expectedFormsByPos[posKey].orEmpty()
                        if (expectedForPos.isNotEmpty()) {
                            val formsInDb = dictQ.selectFormsWithIdByLemmaPosId(lemmaPos.id).executeAsList()
                            val actualFormKeys = formsInDb.map { form ->
                                val tags = dictQ.selectFormTagsByFormId(form.form_id).executeAsList().map { it.tag }.toSet()
                                FormKey(form.form, stripAccents(form.form), tags)
                            }.toSet()

                            assertEquals(
                                expectedForPos.size,
                                actualFormKeys.size,
                                "Expected exactly ${expectedForPos.size} unique forms for '$word' POS $posKey in $lang, " +
                                        "but found ${actualFormKeys.size}. Expected forms: ${
                                            expectedForPos.joinToString(", ") { it.form }
                                        }. Found forms: ${actualFormKeys.joinToString(", ") { it.form }}"
                            )
                        }
                    }

                    val expectedTotal = expectedFormsByPos.values.sumOf { it.size }
                    val lemmaPosIds = lemmaPosRows.map { it.id }
                    val allFormsForLemma = lemmaPosIds.flatMap { lemmaPosId ->
                        dictQ.selectFormsByLemmaPosId(lemmaPosId).executeAsList()
                    }
                    assertEquals(
                        expectedTotal,
                        allFormsForLemma.size,
                        "Expected exactly $expectedTotal unique forms for '$word' in $lang, but found ${allFormsForLemma.size}."
                    )
                }

                // Validate word_family if present in processed JSON
                val expectedWordFamily = processed.wordFamily
                if (expectedWordFamily != null && expectedWordFamily.isNotEmpty()) {
                    val lemmaIds = lemmas.map { it.id }
                    val actualWordFamily = lemmaIds.flatMap { lemmaId ->
                        dictQ.selectWordFamilyByLemmaId(lemmaId).executeAsList()
                    }
                    assertEquals(
                        expectedWordFamily.sorted(),
                        actualWordFamily.sorted(),
                        "Word family mismatch for '$word' in $lang from ${pFile.name}. " +
                                "Expected: ${expectedWordFamily.sorted()}, Found: ${actualWordFamily.sorted()}"
                    )
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
                                val defActual = tq.selectDefinitionsBySense(senseId, lang, trg).executeAsList().singleOrNull()
                                assertEquals(
                                    defExpected,
                                    defActual,
                                    "Definition mismatch for $lang->$trg in ${pFile.name}"
                                )
                            }
                            val expectedTranslations = sense.translations[trg]
                            if (!expectedTranslations.isNullOrEmpty()) {
                                val rows = tq.selectSenseTranslationsBySenseWithNormalized(senseId, lang, trg).executeAsList()
                                val words = rows.map { it.target_lang_word }
                                val expectedWords = expectedTranslations.map { it.targetLangWord }
                                assertEquals(
                                    expectedWords,
                                    words,
                                    "Translation words order mismatch for $lang->$trg in ${pFile.name}"
                                )
                                // also check normalized values
                                rows.forEachIndexed { i, row ->
                                    assertEquals(
                                        stripAccents(expectedWords[i]),
                                        row.target_lang_word_normalized,
                                        "Normalized translation mismatch for '${expectedWords[i]}' in $lang->$trg from ${pFile.name}"
                                    )
                                }
                            }
                            // Example index 0 if exists
                            val ex0 = sense.examples.firstOrNull()?.targetLangTranslations?.get(trg)
                            if (ex0 != null) {
                                val exActual = tq.selectExampleTranslations(senseId, lang, trg, 0).executeAsOneOrNull()
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

    private fun buildTestFrequencyMap(lang: String): Map<String, Double> {
        val processedFiles = listResourceJsonFiles("processed_json_files/$lang")
        val map = mutableMapOf<String, Double>()
        processedFiles.forEach { pFile ->
            val rawFile = resourceFile("db_extract/$lang/${pFile.name}")
            val raw = json.decodeFromString(ExtractedWordData.serializer(), rawFile.readText())
            // Assign a default test frequency for all words
            map[raw.word] = 3.0
        }
        return map
    }

    private fun mapPos(pos: String): DictionaryPos? {
        try {
            return DictionaryPos.valueOf(pos.uppercase())
        } catch (_: IllegalArgumentException) {
            return when (pos.uppercase()) {
                "ADJ" -> DictionaryPos.ADJECTIVE
                "ADV" -> DictionaryPos.ADVERB
                "PREP" -> DictionaryPos.PREPOSITION
                "CONJ" -> DictionaryPos.CONJUNCTION
                "PRON" -> DictionaryPos.PRONOUN
                "INTJ" -> DictionaryPos.INTERJECTION
                "NUM" -> DictionaryPos.NUMERAL
                else -> null
            }
        }
    }

    @Test
    fun bulk_insert_recreates_dictionary_indexes() {
        val outDir = Files.createTempDirectory("index_test").toFile()
        val serverDbManager = ServerDbManager(outDir)
        val lang = "en"

        // Create dictionary DB with schema (indexes are created by schema)
        serverDbManager.openDictionary(lang).also { /* just to create the db */ }

        val dbFile = serverDbManager.dictionaryDbFile(lang)
        val expectedTables = listOf("lemma", "lemma_pos", "form")

        // Get initial indexes
        val initialIndexes = getIndexesFromDb(dbFile, expectedTables)
        assertTrue(initialIndexes.isNotEmpty(), "Dictionary schema should have indexes on $expectedTables")

        // Open for bulk insert - this should drop indexes
        serverDbManager.openDictionaryForBulkInsert(lang)
        val indexesAfterPrepare = getIndexesFromDb(dbFile, expectedTables)
        assertTrue(
            indexesAfterPrepare.isEmpty(),
            "Indexes should be dropped after openDictionaryForBulkInsert, but found: $indexesAfterPrepare"
        )

        // Finish bulk insert - this should recreate indexes
        serverDbManager.finishDictionaryBulkInsert(lang)
        val indexesAfterFinish = getIndexesFromDb(dbFile, expectedTables)
        assertEquals(
            initialIndexes.sorted(),
            indexesAfterFinish.sorted(),
            "Indexes should be recreated after finishDictionaryBulkInsert"
        )
    }

    @Test
    fun bulk_insert_recreates_translation_indexes() {
        val outDir = Files.createTempDirectory("index_test").toFile()
        val serverDbManager = ServerDbManager(outDir)
        val sourceLang = "en"
        val targetLang = "ru"

        // Create translation DB with schema
        serverDbManager.openTranslation(sourceLang, targetLang).also { /* just to create the db */ }

        val dbFile = serverDbManager.translationDbFile(sourceLang, targetLang)
        val expectedTables = listOf("sense_translation")

        // Get initial indexes
        val initialIndexes = getIndexesFromDb(dbFile, expectedTables)
        assertTrue(initialIndexes.isNotEmpty(), "Translation schema should have indexes on $expectedTables")

        // Open for bulk insert - this should drop indexes
        serverDbManager.openTranslationForBulkInsert(sourceLang, targetLang)
        val indexesAfterPrepare = getIndexesFromDb(dbFile, expectedTables)
        assertTrue(
            indexesAfterPrepare.isEmpty(),
            "Indexes should be dropped after openTranslationForBulkInsert, but found: $indexesAfterPrepare"
        )

        // Finish bulk insert - this should recreate indexes
        serverDbManager.finishTranslationBulkInsert(sourceLang, targetLang)
        val indexesAfterFinish = getIndexesFromDb(dbFile, expectedTables)
        assertEquals(
            initialIndexes.sorted(),
            indexesAfterFinish.sorted(),
            "Indexes should be recreated after finishTranslationBulkInsert"
        )
    }

    private fun getIndexesFromDb(dbFile: File, tableNames: List<String>): List<String> {
        val indexes = mutableListOf<String>()
        DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
            val placeholders = tableNames.joinToString(",") { "?" }
            val sql = "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name IN ($placeholders) AND sql IS NOT NULL"
            conn.prepareStatement(sql).use { stmt ->
                tableNames.forEachIndexed { index, tableName ->
                    stmt.setString(index + 1, tableName)
                }
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        indexes.add(rs.getString("name"))
                    }
                }
            }
        }
        return indexes
    }
}
