package com.slovy.slovymovyapp.data.lists

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class WordListsRepository(private val db: AppDatabase) {

    suspend fun getVersion(language: Language): String? = withContext(Dispatchers.IO) {
        db.wordListsQueries.selectVersion(language.code).executeAsOneOrNull()
    }

    suspend fun getLists(language: Language): List<WordList> = withContext(Dispatchers.IO) {
        val queries = db.wordListsQueries
        queries.transactionWithResult {
            val ids = queries.selectListIdsByLang(language.code).executeAsList()
            if (ids.isEmpty()) return@transactionWithResult emptyList()
            val texts = queries.selectTextsByLang(language.code).executeAsList().groupBy { it.list_id }
            val labels = queries.selectLabelsByLang(language.code).executeAsList().groupBy { it.list_id }
            val senses = queries.selectSensesByLang(language.code).executeAsList().groupBy { it.list_id }
            ids.map { id ->
                WordList(
                    id = id,
                    title = texts[id].orEmpty().mapNotNull { row -> row.title?.let { row.locale to it } }.toMap(),
                    subtitle = texts[id].orEmpty().mapNotNull { row -> row.subtitle?.let { row.locale to it } }.toMap(),
                    labels = labels[id].orEmpty().groupBy({ it.locale }, { it.label }),
                    senseIds = senses[id].orEmpty().map { it.sense_id },
                )
            }
        }
    }

    suspend fun getList(language: Language, listId: String): WordList? = withContext(Dispatchers.IO) {
        val queries = db.wordListsQueries
        queries.transactionWithResult {
            queries.selectListId(language.code, listId).executeAsOneOrNull()
                ?: return@transactionWithResult null
            val texts = queries.selectTextsByList(language.code, listId).executeAsList()
            WordList(
                id = listId,
                title = texts.mapNotNull { row -> row.title?.let { row.locale to it } }.toMap(),
                subtitle = texts.mapNotNull { row -> row.subtitle?.let { row.locale to it } }.toMap(),
                labels = queries.selectLabelsByList(language.code, listId).executeAsList()
                    .groupBy({ it.locale }, { it.label }),
                senseIds = queries.selectSensesByList(language.code, listId).executeAsList(),
            )
        }
    }

    /**
     * Atomically replaces every stored list for [language] with [lists] and records the
     * server bundle [version].
     */
    @OptIn(ExperimentalTime::class)
    suspend fun replaceLists(language: Language, version: String, lists: List<WordList>) =
        withContext(Dispatchers.IO) {
            val queries = db.wordListsQueries
            val updatedAt = Clock.System.now().toEpochMilliseconds()
            queries.transaction {
                queries.deleteListsByLang(language.code)
                queries.deleteTextsByLang(language.code)
                queries.deleteLabelsByLang(language.code)
                queries.deleteSensesByLang(language.code)
                lists.forEachIndexed { listIndex, list ->
                    queries.insertList(language.code, list.id, listIndex.toLong())
                    val locales = list.title.keys + list.subtitle.keys
                    locales.forEach { locale ->
                        queries.insertText(
                            language.code,
                            list.id,
                            locale,
                            list.title[locale],
                            list.subtitle[locale],
                        )
                    }
                    list.labels.forEach { (locale, labels) ->
                        labels.forEachIndexed { labelIndex, label ->
                            queries.insertLabel(language.code, list.id, locale, labelIndex.toLong(), label)
                        }
                    }
                    list.senseIds.forEachIndexed { senseIndex, senseId ->
                        queries.insertSense(language.code, list.id, senseIndex.toLong(), senseId)
                    }
                }
                queries.upsertMeta(language.code, version, updatedAt)
            }
        }
}
