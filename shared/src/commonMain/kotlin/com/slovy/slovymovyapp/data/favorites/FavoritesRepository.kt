package com.slovy.slovymovyapp.data.favorites

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class FavoritesRepository(private val db: AppDatabase) {

    @OptIn(ExperimentalTime::class)
    suspend fun add(senseId: String, targetLang: Language, lemma: String) = withContext(Dispatchers.IO) {
        db.favoritesQueries.insertFavorite(
            sense_id = senseId,
            target_lang = targetLang.code,
            lemma = lemma,
            created_at = Clock.System.now().epochSeconds
        )
    }

    suspend fun add(senseId: String, targetLang: Language, lemma: String, createdAt: Long) =
        withContext(Dispatchers.IO) {
            db.favoritesQueries.insertFavorite(
                sense_id = senseId,
                target_lang = targetLang.code,
                lemma = lemma,
                created_at = createdAt
            )
        }

    suspend fun remove(senseId: String, targetLang: Language) = withContext(Dispatchers.IO) {
        db.favoritesQueries.deleteFavorite(
            sense_id = senseId,
            target_lang = targetLang.code
        )
    }

    suspend fun getAll(): List<Favorite> = withContext(Dispatchers.IO) {
        db.favoritesQueries.selectAll().executeAsList().map { row ->
            Favorite(
                senseId = row.sense_id,
                language = Language.fromCode(row.target_lang),
                lemma = row.lemma,
                createdAt = row.created_at
            )
        }
    }

    suspend fun getByLangAndLemma(targetLang: Language, lemma: String): List<Favorite> =
        withContext(Dispatchers.IO) {
            db.favoritesQueries.selectByLangAndLemma(target_lang = targetLang.code, lemma = lemma)
                .executeAsList()
                .map { row ->
                    Favorite(
                        senseId = row.sense_id,
                        language = Language.fromCode(row.target_lang),
                        lemma = row.lemma,
                        createdAt = row.created_at
                    )
                }
        }

    suspend fun getAllGroupedByLangAndLemma(): List<Favorite> = withContext(Dispatchers.IO) {
        db.favoritesQueries.selectAllOrderedByLangAndLemma().executeAsList().map { row ->
            Favorite(
                senseId = row.sense_id,
                language = Language.fromCode(row.target_lang),
                lemma = row.lemma,
                createdAt = row.created_at
            )
        }
    }

    suspend fun searchByLemma(query: String): List<Favorite> = withContext(Dispatchers.IO) {
        val pattern = "%$query%"
        db.favoritesQueries.selectByLemmaSearch(pattern).executeAsList().map { row ->
            Favorite(
                senseId = row.sense_id,
                language = Language.fromCode(row.target_lang),
                lemma = row.lemma,
                createdAt = row.created_at
            )
        }
    }

    suspend fun exists(senseId: String, targetLang: Language): Boolean = withContext(Dispatchers.IO) {
        db.favoritesQueries.countBySenseIdAndLang(sense_id = senseId, target_lang = targetLang.code)
            .executeAsOne() > 0
    }

    suspend fun getOne(senseId: String, targetLang: Language): Favorite? = withContext(Dispatchers.IO) {
        db.favoritesQueries.selectOne(sense_id = senseId, target_lang = targetLang.code)
            .executeAsOneOrNull()
            ?.let { row ->
                Favorite(
                    senseId = row.sense_id,
                    language = Language.fromCode(row.target_lang),
                    lemma = row.lemma,
                    createdAt = row.created_at
                )
            }
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        db.favoritesQueries.deleteAll()
    }
}