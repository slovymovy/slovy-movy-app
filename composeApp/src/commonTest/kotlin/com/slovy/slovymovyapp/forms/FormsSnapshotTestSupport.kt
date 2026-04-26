package com.slovy.slovymovyapp.forms

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.dictionary.DictionaryPos
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.data.remote.DictionaryRepository
import com.slovy.slovymovyapp.data.remote.PartOfSpeech
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.test.BaseTest
import kotlin.test.assertNotNull

internal fun BaseTest.createSnapshotRepository(): Pair<DataDbManager, DictionaryRepository> {
    val mgr = testDataDbManager()
    val repo = DictionaryRepository(
        dataDbManager = mgr,
        localDbManager = testLocalDbManager(),
        favoritesRepository = FavoritesRepository(testAppDatabaseHolder().database),
        settingsRepository = SettingsRepository(testAppDatabaseHolder().database)
    )
    return mgr to repo
}

internal suspend fun resolveFormsSnapshot(
    repo: DictionaryRepository,
    language: Language,
    lemma: String,
    pos: DictionaryPos
): Map<String, List<List<String?>>> {
    val card = assertNotNull(
        repo.getLanguageCard(language, lemma),
        "Expected card for $lemma in ${language.code}"
    )
    val expectedPos = PartOfSpeech.valueOf(pos.name)
    val entry = assertNotNull(
        card.entries.firstOrNull { it.pos == expectedPos },
        "Expected $pos entry for $lemma in ${language.code}"
    )
    return entry.formsViews.associate { it.view.viewId to it.forms }
}
