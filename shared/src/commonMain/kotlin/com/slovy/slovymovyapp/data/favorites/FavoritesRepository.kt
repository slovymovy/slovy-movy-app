package com.slovy.slovymovyapp.data.favorites

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.db.AppDatabase
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class FavoritesRepository(private val db: AppDatabase) {

    @OptIn(ExperimentalTime::class)
    fun add(senseId: String, targetLang: Language, lemma: String) {
        db.favoritesQueries.insertFavorite(
            sense_id = senseId,
            target_lang = targetLang.code,
            lemma = lemma,
            created_at = Clock.System.now().epochSeconds
        )
    }

    fun remove(senseId: String, targetLang: Language) {
        db.favoritesQueries.deleteFavorite(
            sense_id = senseId,
            target_lang = targetLang.code
        )
    }

    fun getAll(): List<Favorite> = db.favoritesQueries.selectAll().executeAsList().map { row ->
        Favorite(
            senseId = row.sense_id,
            targetLang = Language.fromCode(row.target_lang),
            lemma = row.lemma,
            createdAt = row.created_at
        )
    }

    fun getByLangAndLemma(targetLang: Language, lemma: String): List<Favorite> =
        db.favoritesQueries.selectByLangAndLemma(target_lang = targetLang.code, lemma = lemma)
            .executeAsList()
            .map { row ->
                Favorite(
                    senseId = row.sense_id,
                    targetLang = Language.fromCode(row.target_lang),
                    lemma = row.lemma,
                    createdAt = row.created_at
                )
            }

    fun getAllGroupedByLangAndLemma(): List<Favorite> =
        db.favoritesQueries.selectAllOrderedByLangAndLemma().executeAsList().map { row ->
            Favorite(
                senseId = row.sense_id,
                targetLang = Language.fromCode(row.target_lang),
                lemma = row.lemma,
                createdAt = row.created_at
            )
        }

    fun searchByLemma(query: String): List<Favorite> {
        val pattern = "%$query%"
        return db.favoritesQueries.selectByLemmaSearch(pattern).executeAsList().map { row ->
            Favorite(
                senseId = row.sense_id,
                targetLang = Language.fromCode(row.target_lang),
                lemma = row.lemma,
                createdAt = row.created_at
            )
        }
    }

    fun exists(senseId: String, targetLang: Language): Boolean =
        db.favoritesQueries.countBySenseIdAndLang(sense_id = senseId, target_lang = targetLang.code)
            .executeAsOne() > 0

    fun deleteAll() {
        db.favoritesQueries.deleteAll()
    }
}