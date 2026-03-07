@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.slovy.slovymovyapp.builder

import com.slovy.slovymovyapp.data.dictionary.DictionaryPos
import com.slovy.slovymovyapp.ingestion.JsonIngestionBuilder
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Verifies that words with multiple genuinely different conjugation patterns produce
 * multiple lemma_pos rows (one per cluster), and that forms / senses are correctly
 * distributed to the right cluster.
 *
 * Algorithm: native entries with forms → one cluster root each (in processed-ingestion mode,
 * only those referenced by processed senses are active; unreferenced ones are absorbed).
 * Non-native entries are assigned via Jaccard similarity.
 */
class MultiLemmaPosIngestionTest {

    // ── helpers ──────────────────────────────────────────────────────────────

    private data class LemmaPosInfo(
        val id: Uuid,
        val pos: DictionaryPos,
        val forms: Set<String>     // all form strings (not normalized)
    )

    private fun ingestAndQuery(
        lang: String,
        word: String,
        outDir: File
    ): List<LemmaPosInfo> {
        val serverDbManager = ServerDbManager(outDir)
        val frequencyMap = mapOf(word to 3.0)
        val builder = JsonIngestionBuilder({ from, to -> serverDbManager.openTranslation(from, to) }, frequencyMap)

        val processedFile = resourceFile("processed_json_files/$lang/$word.json")
        val rawFile = resourceFile("db_extract/$lang/$word.json")

        val dictDb = serverDbManager.openDictionary(lang)
        builder.ingest(processedFile.readText(), rawFile.readText(), dictDb)

        val dictQ = dictDb.dictionaryQueries
        val lemmaIds = dictQ.selectLemmasByWord(lang, word.lowercase()).executeAsList().map { it.id }
        assertTrue(lemmaIds.isNotEmpty(), "Lemma '$word' not found in DB for lang=$lang")

        return lemmaIds.flatMap { lemmaId ->
            dictQ.selectLemmaPosByLemmaId(lemmaId).executeAsList().map { lp ->
                val forms = dictQ.selectFormsWithIdByLemmaPosId(lp.id).executeAsList()
                    .map { it.form }
                    .toSet()
                LemmaPosInfo(lp.id, lp.pos, forms)
            }
        }
    }

    private fun lemmaPosForPos(infos: List<LemmaPosInfo>, pos: DictionaryPos): List<LemmaPosInfo> =
        infos.filter { it.pos == pos }

    private fun resourceFile(path: String): File {
        val cl = Thread.currentThread().contextClassLoader
        val url = cl.getResource(path) ?: error("Resource not found: $path")
        return File(url.toURI())
    }

    // ── English words ─────────────────────────────────────────────────────────

    @Test
    fun lie_verb_produces_two_lemma_pos() {
        val outDir = Files.createTempDirectory("multi_lemma_pos_lie").toFile()
        val infos = ingestAndQuery("en", "lie", outDir)
        val verbEntries = lemmaPosForPos(infos, DictionaryPos.VERB)

        assertEquals(2, verbEntries.size, "Expected 2 verb lemma_pos for 'lie' (lay/lain vs lied)")

        // One cluster should contain "lay"/"lain", the other "lied"
        val layCluster = verbEntries.firstOrNull { it.forms.contains("lay") }
        val liedCluster = verbEntries.firstOrNull { it.forms.contains("lied") }

        assertTrue(layCluster != null, "'lay' form not found in any verb lemma_pos")
        assertTrue(liedCluster != null, "'lied' form not found in any verb lemma_pos")
        assertTrue(layCluster!!.id != liedCluster!!.id, "'lay' and 'lied' must be in DIFFERENT lemma_pos")
        assertTrue(layCluster.forms.contains("lain"), "'lain' should be in the same cluster as 'lay'")
    }

    @Test
    fun can_verb_produces_two_lemma_pos() {
        val outDir = Files.createTempDirectory("multi_lemma_pos_can").toFile()
        val infos = ingestAndQuery("en", "can", outDir)
        val verbEntries = lemmaPosForPos(infos, DictionaryPos.VERB)

        assertEquals(2, verbEntries.size, "Expected 2 verb lemma_pos for 'can' (modal vs container)")

        // Modal cluster has "could"; can-as-container cluster has "canned"
        val modalCluster = verbEntries.firstOrNull { it.forms.contains("could") }
        val cannerCluster = verbEntries.firstOrNull { it.forms.contains("canned") }

        assertTrue(modalCluster != null, "'could' form not found in any verb lemma_pos")
        assertTrue(cannerCluster != null, "'canned' form not found in any verb lemma_pos")
        assertTrue(modalCluster!!.id != cannerCluster!!.id, "'could' and 'canned' must be in DIFFERENT lemma_pos")
    }

    @Test
    fun fly_verb_produces_two_lemma_pos() {
        val outDir = Files.createTempDirectory("multi_lemma_pos_fly").toFile()
        val infos = ingestAndQuery("en", "fly", outDir)
        val verbEntries = lemmaPosForPos(infos, DictionaryPos.VERB)

        assertEquals(2, verbEntries.size, "Expected 2 verb lemma_pos for 'fly' (flew/flown vs flied)")

        val flewCluster = verbEntries.firstOrNull { it.forms.contains("flew") }
        val fliedCluster = verbEntries.firstOrNull { it.forms.contains("flied") }

        assertTrue(flewCluster != null, "'flew' form not found in any verb lemma_pos")
        assertTrue(fliedCluster != null, "'flied' form not found in any verb lemma_pos")
        assertTrue(flewCluster!!.id != fliedCluster!!.id, "'flew' and 'flied' must be in DIFFERENT lemma_pos")
    }

    @Test
    fun hang_produces_one_verb_lemma_pos() {
        val outDir = Files.createTempDirectory("multi_lemma_pos_hang").toFile()
        val infos = ingestAndQuery("en", "hang", outDir)
        val verbEntries = lemmaPosForPos(infos, DictionaryPos.VERB)

        assertEquals(1, verbEntries.size, "Expected 1 verb lemma_pos for 'hang' (single native entry)")
    }

    @Test
    fun hang_noun_produces_two_lemma_pos() {
        val outDir = Files.createTempDirectory("multi_lemma_pos_hang_n").toFile()
        val infos = ingestAndQuery("en", "hang", outDir)
        val nounEntries = lemmaPosForPos(infos, DictionaryPos.NOUN)

        // All native noun entries with distinct form sets become roots in the two-step path.
        assertEquals(
            2, nounEntries.size,
            "Expected 2 noun lemma_pos for 'hang' (all native noun entries become roots)"
        )
    }

    @Test
    fun shine_verb_produces_two_lemma_pos() {
        val outDir = Files.createTempDirectory("multi_lemma_pos_shine").toFile()
        val infos = ingestAndQuery("en", "shine", outDir)
        val verbEntries = lemmaPosForPos(infos, DictionaryPos.VERB)

        assertEquals(2, verbEntries.size, "Expected 2 verb lemma_pos for 'shine' (shone vs shined paradigm)")

        // "shone" appears in exactly one cluster (the shone paradigm); the other has only "shined"
        val shoneClusters = verbEntries.filter { it.forms.contains("shone") }
        val noShoneClusters = verbEntries.filter { !it.forms.contains("shone") }

        assertEquals(1, shoneClusters.size, "'shone' should appear in exactly one lemma_pos")
        assertEquals(1, noShoneClusters.size, "Exactly one lemma_pos should lack 'shone'")
        assertTrue(
            noShoneClusters.first().forms.contains("shined"),
            "The shined-only cluster should contain 'shined'"
        )
    }

    @Test
    fun weave_verb_produces_two_lemma_pos() {
        val outDir = Files.createTempDirectory("multi_lemma_pos_weave").toFile()
        val infos = ingestAndQuery("en", "weave", outDir)
        val verbEntries = lemmaPosForPos(infos, DictionaryPos.VERB)

        assertEquals(2, verbEntries.size, "Expected 2 verb lemma_pos for 'weave' (wove/woven vs weaved paradigm)")

        // "wove"/"woven" appear only in the primary (wove) paradigm cluster
        val woveClusters = verbEntries.filter { it.forms.contains("wove") }
        val noWoveClusters = verbEntries.filter { !it.forms.contains("wove") }

        assertEquals(1, woveClusters.size, "'wove' should appear in exactly one lemma_pos")
        assertEquals(1, noWoveClusters.size, "Exactly one lemma_pos should lack 'wove'")
        assertTrue(
            woveClusters.first().forms.contains("woven"),
            "The wove cluster should also contain 'woven'"
        )
        assertTrue(
            noWoveClusters.first().forms.contains("weaved"),
            "The weaved-only cluster should contain 'weaved'"
        )
    }

    // ── Dutch words ───────────────────────────────────────────────────────────

    @Test
    fun doorlopen_verb_produces_two_lemma_pos() {
        val outDir = Files.createTempDirectory("multi_lemma_pos_doorlopen").toFile()
        val infos = ingestAndQuery("nl", "doorlopen", outDir)
        val verbEntries = lemmaPosForPos(infos, DictionaryPos.VERB)

        assertEquals(
            2, verbEntries.size,
            "Expected 2 verb lemma_pos for 'doorlopen' (two nl-extract entries with different stress)"
        )
    }

    @Test
    fun overleggen_verb_produces_two_lemma_pos() {
        val outDir = Files.createTempDirectory("multi_lemma_pos_overleggen").toFile()
        val infos = ingestAndQuery("nl", "overleggen", outDir)
        val verbEntries = lemmaPosForPos(infos, DictionaryPos.VERB)

        // Both native nl-extract entries become roots in the two-step path (different form sets).
        assertEquals(
            2, verbEntries.size,
            "Expected 2 verb lemma_pos for 'overleggen' (both native entries become roots)"
        )
    }

    @Test
    fun ondergaan_verb_produces_three_lemma_pos() {
        val outDir = Files.createTempDirectory("multi_lemma_pos_ondergaan").toFile()
        val infos = ingestAndQuery("nl", "ondergaan", outDir)
        val verbEntries = lemmaPosForPos(infos, DictionaryPos.VERB)

        // All 3 native entries with distinct form sets become roots in the two-step path;
        // the empty cluster (no senses) is filtered at read time by DictionaryRepository.
        assertEquals(
            3, verbEntries.size,
            "Expected 3 verb lemma_pos for 'ondergaan' (3 native entries all become roots)"
        )
    }

    @Test
    fun omschrijven_verb_produces_one_lemma_pos() {
        val outDir = Files.createTempDirectory("multi_lemma_pos_omschrijven").toFile()
        val infos = ingestAndQuery("nl", "omschrijven", outDir)
        val verbEntries = lemmaPosForPos(infos, DictionaryPos.VERB)

        assertEquals(
            1, verbEntries.size,
            "Expected 1 verb lemma_pos for 'omschrijven' (1 native nl-extract entry)"
        )
    }

    @Test
    fun overstappen_verb_produces_one_lemma_pos() {
        val outDir = Files.createTempDirectory("multi_lemma_pos_overstappen").toFile()
        val infos = ingestAndQuery("nl", "overstappen", outDir)
        val verbEntries = lemmaPosForPos(infos, DictionaryPos.VERB)

        assertEquals(
            1, verbEntries.size,
            "Expected 1 verb lemma_pos for 'overstappen' (1 native nl-extract entry)"
        )
    }

    @Test
    fun overzien_verb_produces_two_lemma_pos() {
        val outDir = Files.createTempDirectory("multi_lemma_pos_overzien").toFile()
        val infos = ingestAndQuery("nl", "overzien", outDir)
        val verbEntries = lemmaPosForPos(infos, DictionaryPos.VERB)

        // 3 native entries; 2 are referenced by processed senses → 2 clusters
        assertEquals(
            2, verbEntries.size,
            "Expected 2 verb lemma_pos for 'overzien' (2 of 3 native entries referenced by senses)"
        )
    }

    @Test
    fun voorkomen_verb_produces_two_lemma_pos() {
        val outDir = Files.createTempDirectory("multi_lemma_pos_voorkomen").toFile()
        val infos = ingestAndQuery("nl", "voorkomen", outDir)
        val verbEntries = lemmaPosForPos(infos, DictionaryPos.VERB)

        assertEquals(
            2, verbEntries.size,
            "Expected 2 verb lemma_pos for 'voorkomen' (2 native entries: 'occur' vs 'prevent')"
        )

        // The two clusters share 107 of 108–109 forms; the only unique forms are the
        // accented infinitives: vóórkomen (separable, "to occur") vs voorkómen (inseparable, "to prevent").
        val occurCluster  = verbEntries.firstOrNull { it.forms.contains("vóórkomen") }
        val preventCluster = verbEntries.firstOrNull { it.forms.contains("voorkómen") }

        assertTrue(occurCluster  != null, "'vóórkomen' (occur) not found in any verb lemma_pos")
        assertTrue(preventCluster != null, "'voorkómen' (prevent) not found in any verb lemma_pos")
        assertTrue(
            occurCluster.id != preventCluster.id,
            "'vóórkomen' and 'voorkómen' must be in DIFFERENT lemma_pos"
        )

    }

    // ── Russian words ─────────────────────────────────────────────────────────

    @Test
    fun stroyt_verb_produces_two_lemma_pos() {
        val word = "строить"
        val outDir = Files.createTempDirectory("multi_lemma_pos_stroyt").toFile()
        val infos = ingestAndQuery("ru", word, outDir)
        val verbEntries = lemmaPosForPos(infos, DictionaryPos.VERB)

        // Both native ru-extract entries become roots in the two-step path;
        // senses are routed correctly via sense hints.
        assertEquals(
            2, verbEntries.size,
            "Expected 2 verb lemma_pos for '$word' (both native entries become roots)"
        )
    }

    @Test
    fun voorkomen_sense_routing_preserved_in_two_step_ingestion() {
        val outDir = Files.createTempDirectory("multi_lemma_pos_voorkomen_routing").toFile()
        val serverDbManager = ServerDbManager(outDir)
        val builder = JsonIngestionBuilder(
            { from, to -> serverDbManager.openTranslation(from, to) },
            mapOf("voorkomen" to 3.0)
        )

        val rawJson = resourceFile("db_extract/nl/voorkomen.json").readText()
        val processedJson = resourceFile("processed_json_files/nl/voorkomen.json").readText()

        val dictDb = serverDbManager.openDictionary("nl")

        // Step 1: raw-only ingestion (two-step via ingestBatch)
        builder.ingestBatch(listOf(JsonIngestionBuilder.IngestionInput(rawJson = rawJson)), dictDb)
        // Step 2: processed-over-raw ingestion
        builder.ingestProcessedOverRaw(processedJson, "voorkomen", "nl", dictDb)

        val dictQ = dictDb.dictionaryQueries
        val lemmaId = dictQ.selectLemmasByWord("nl", "voorkomen").executeAsList().first().id
        val verbPosEntries = dictQ.selectLemmaPosByLemmaId(lemmaId).executeAsList()
            .filter { it.pos == DictionaryPos.VERB }

        assertEquals(2, verbPosEntries.size, "Expected 2 verb lemma_pos for 'voorkomen'")

        val occurClusterId = verbPosEntries.firstOrNull { lp ->
            dictQ.selectFormsWithIdByLemmaPosId(lp.id).executeAsList().any { it.form == "vóórkomen" }
        }?.id
        val preventClusterId = verbPosEntries.firstOrNull { lp ->
            dictQ.selectFormsWithIdByLemmaPosId(lp.id).executeAsList().any { it.form == "voorkómen" }
        }?.id

        assertTrue(occurClusterId != null, "'vóórkomen' (occur) cluster not found")
        assertTrue(preventClusterId != null, "'voorkómen' (prevent) cluster not found")
        assertTrue(occurClusterId != preventClusterId, "Two clusters must have different IDs")

        // Both clusters should have senses — confirming hint-based routing works
        val occurSenses = dictQ.selectSensesByLemmaPosId(occurClusterId!!).executeAsList()
        val preventSenses = dictQ.selectSensesByLemmaPosId(preventClusterId!!).executeAsList()

        assertTrue(occurSenses.isNotEmpty(), "'vóórkomen' cluster should have senses after processed-over-raw")
        assertTrue(preventSenses.isNotEmpty(), "'voorkómen' cluster should have senses after processed-over-raw")
    }

    // ── Sense routing tests ───────────────────────────────────────────────────

    @Test
    fun lie_verb_senses_routed_to_correct_lemma_pos() {
        val outDir = Files.createTempDirectory("multi_lemma_pos_lie_senses").toFile()
        val serverDbManager = ServerDbManager(outDir)
        val builder = JsonIngestionBuilder({ from, to -> serverDbManager.openTranslation(from, to) }, mapOf("lie" to 3.0))

        val dictDb = serverDbManager.openDictionary("en")
        builder.ingest(
            resourceFile("processed_json_files/en/lie.json").readText(),
            resourceFile("db_extract/en/lie.json").readText(),
            dictDb
        )

        val dictQ = dictDb.dictionaryQueries
        val lemmaId = dictQ.selectLemmasByWord("en", "lie").executeAsList().first().id
        val verbPosEntries = dictQ.selectLemmaPosByLemmaId(lemmaId).executeAsList()
            .filter { it.pos == DictionaryPos.VERB }

        assertEquals(2, verbPosEntries.size, "Expected 2 verb lemma_pos for 'lie'")

        // Identify the lay/lain cluster and the lied cluster by their forms
        val layLemmaPosId = verbPosEntries.firstOrNull { lp ->
            dictQ.selectFormsWithIdByLemmaPosId(lp.id).executeAsList().any { it.form == "lay" }
        }?.id
        val liedLemmaPosId = verbPosEntries.firstOrNull { lp ->
            dictQ.selectFormsWithIdByLemmaPosId(lp.id).executeAsList().any { it.form == "lied" }
        }?.id

        assertTrue(layLemmaPosId != null, "No lemma_pos found with form 'lay'")
        assertTrue(liedLemmaPosId != null, "No lemma_pos found with form 'lied'")

        // The "lie down" sense (0bc4b833) belongs to the lay/lain cluster
        val senseLayDown = uuidParse("0bc4b833-695d-3f43-ad1b-3dfebd4ce1d1")
        val senseLayDownRow = dictQ.selectSensesByLemmaPosId(layLemmaPosId!!).executeAsList()
            .firstOrNull { it.sense_id == senseLayDown }
        assertTrue(
            senseLayDownRow != null,
            "Sense 'lie down' (0bc4b833) must be routed to the lay/lain cluster"
        )

        // The "deceive" sense (8f0630d5) belongs to the lied cluster
        val senseDeceive = uuidParse("8f0630d5-11db-3350-bc4e-b3391287e68c")
        val senseDeceiveRow = dictQ.selectSensesByLemmaPosId(liedLemmaPosId!!).executeAsList()
            .firstOrNull { it.sense_id == senseDeceive }
        assertTrue(
            senseDeceiveRow != null,
            "Sense 'deceive' (8f0630d5) must be routed to the lied cluster"
        )
    }

    @Test
    fun shine_verb_senses_routed_to_correct_lemma_pos() {
        val outDir = Files.createTempDirectory("multi_lemma_pos_shine_senses").toFile()
        val serverDbManager = ServerDbManager(outDir)
        val builder = JsonIngestionBuilder({ from, to -> serverDbManager.openTranslation(from, to) }, mapOf("shine" to 3.0))

        val dictDb = serverDbManager.openDictionary("en")
        builder.ingest(
            resourceFile("processed_json_files/en/shine.json").readText(),
            resourceFile("db_extract/en/shine.json").readText(),
            dictDb
        )

        val dictQ = dictDb.dictionaryQueries
        val lemmaId = dictQ.selectLemmasByWord("en", "shine").executeAsList().first().id
        val verbPosEntries = dictQ.selectLemmaPosByLemmaId(lemmaId).executeAsList()
            .filter { it.pos == DictionaryPos.VERB }

        assertEquals(2, verbPosEntries.size, "Expected 2 verb lemma_pos for 'shine'")

        // "shone" appears in exactly one cluster; the other has "shined" exclusively
        val shoneClusters = verbPosEntries.filter { lp ->
            dictQ.selectFormsWithIdByLemmaPosId(lp.id).executeAsList().any { it.form == "shone" }
        }
        val nonShoneClusters = verbPosEntries.filter { lp ->
            dictQ.selectFormsWithIdByLemmaPosId(lp.id).executeAsList().none { it.form == "shone" }
        }

        assertEquals(1, shoneClusters.size, "'shone' should appear in exactly one lemma_pos")
        assertEquals(1, nonShoneClusters.size, "Exactly one lemma_pos should lack 'shone'")
    }

    private fun uuidParse(s: String) = com.slovy.slovymovyapp.ingestion.uuidParse(s)
}
