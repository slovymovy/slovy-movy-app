package com.slovy.slovymovyapp.data.favorites

import com.slovy.slovymovyapp.db.AppDatabase
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class FavoritesRepository(private val db: AppDatabase) {

    @OptIn(ExperimentalTime::class)
    fun add(senseId: String, targetLang: String, lemma: String) {
        db.favoritesQueries.insertFavorite(
            sense_id = senseId,
            target_lang = targetLang,
            lemma = lemma,
            created_at = Clock.System.now().epochSeconds
        )
    }

    fun remove(senseId: String, targetLang: String) {
        db.favoritesQueries.deleteFavorite(
            sense_id = senseId,
            target_lang = targetLang
        )
    }

    fun getAll(): List<Favorite> = db.favoritesQueries.selectAll().executeAsList().map { row ->
        Favorite(
            senseId = row.sense_id,
            targetLang = row.target_lang,
            lemma = row.lemma,
            createdAt = row.created_at
        )
    }

    fun getByLangAndLemma(targetLang: String, lemma: String): List<Favorite> =
        db.favoritesQueries.selectByLangAndLemma(target_lang = targetLang, lemma = lemma)
            .executeAsList()
            .map { row ->
                Favorite(
                    senseId = row.sense_id,
                    targetLang = row.target_lang,
                    lemma = row.lemma,
                    createdAt = row.created_at
                )
            }

    fun getAllGroupedByLangAndLemma(): List<Favorite> =
        db.favoritesQueries.selectAllOrderedByLangAndLemma().executeAsList().map { row ->
            Favorite(
                senseId = row.sense_id,
                targetLang = row.target_lang,
                lemma = row.lemma,
                createdAt = row.created_at
            )
        }

    fun exists(senseId: String, targetLang: String): Boolean =
        db.favoritesQueries.countBySenseIdAndLang(sense_id = senseId, target_lang = targetLang)
            .executeAsOne() > 0

    fun deleteAll() {
        db.favoritesQueries.deleteAll()
    }
}