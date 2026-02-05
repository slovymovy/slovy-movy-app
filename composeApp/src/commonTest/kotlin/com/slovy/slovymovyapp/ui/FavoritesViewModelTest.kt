package com.slovy.slovymovyapp.ui

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.remote.DictionaryRepository
import com.slovy.slovymovyapp.test.BaseTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
open class FavoritesViewModelTest : BaseTest() {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun favoritesRepository(): FavoritesRepository {
        return FavoritesRepository(testAppDatabaseHolder().database)
    }

    private fun dictionaryRepository(favoritesRepo: FavoritesRepository): DictionaryRepository {
        return DictionaryRepository(testDataDbManager(), testLocalDbManager(), favoritesRepo)
    }

    private fun contentState(vm: FavoritesViewModel): FavoritesUiState.Content {
        assertIs<FavoritesUiState.Content>(vm.state, "Expected Content but was ${vm.state::class.simpleName}")
        return vm.state as FavoritesUiState.Content
    }

    @Test
    fun initialLoad_singleLanguage_hiddenPicker() = runTest(testDispatcher) {
        val favRepo = favoritesRepository()
        favRepo.deleteAll()
        favRepo.add("s1", Language.ENGLISH, "hello")
        favRepo.add("s2", Language.ENGLISH, "world")

        val vm = FavoritesViewModel(favRepo, dictionaryRepository(favRepo))
        vm.loadAndApplyState("")

        val content = contentState(vm)
        assertEquals(2, content.senses.size)
        assertEquals(Language.ENGLISH, content.selectedLanguage)
        assertFalse(content.showLanguagePicker, "Picker should be hidden with single language")
    }

    @Test
    fun initialLoad_multiLanguage_showsPicker_defaultsToFirst() = runTest(testDispatcher) {
        val favRepo = favoritesRepository()
        favRepo.deleteAll()
        favRepo.add("s1", Language.ENGLISH, "hello")
        favRepo.add("s2", Language.RUSSIAN, "привет")

        val vm = FavoritesViewModel(favRepo, dictionaryRepository(favRepo))
        vm.loadAndApplyState("")

        val content = contentState(vm)
        assertTrue(content.showLanguagePicker)
        assertEquals(listOf(Language.ENGLISH, Language.RUSSIAN), content.availableLanguages)
        assertEquals(Language.ENGLISH, content.selectedLanguage, "Should default to first language")
        assertTrue(content.senses.all { it.targetLang == Language.ENGLISH },
            "Should only show selected language's favorites")
    }

    @Test
    fun setSelectedLanguage_filtersFavoritesToThatLanguage() = runTest(testDispatcher) {
        val favRepo = favoritesRepository()
        favRepo.deleteAll()
        favRepo.add("s1", Language.ENGLISH, "hello")
        favRepo.add("s2", Language.ENGLISH, "world")
        favRepo.add("s3", Language.RUSSIAN, "привет")

        val vm = FavoritesViewModel(favRepo, dictionaryRepository(favRepo))
        vm.loadAndApplyState("")

        var content = contentState(vm)
        assertTrue(content.senses.all { it.targetLang == Language.ENGLISH })
        assertEquals(2, content.senses.size)

        // Switch to Russian — update selectedLanguage then reload
        vm.setSelectedLanguage(Language.RUSSIAN)
        vm.loadAndApplyState("")

        content = contentState(vm)
        assertEquals(Language.RUSSIAN, content.selectedLanguage)
        assertEquals(1, content.senses.size)
        assertTrue(content.senses.all { it.targetLang == Language.RUSSIAN })
    }

    @Test
    fun removingLastFavoriteInLanguage_switchesToOtherLanguage() = runTest(testDispatcher) {
        val favRepo = favoritesRepository()
        favRepo.deleteAll()
        favRepo.add("s1", Language.ENGLISH, "hello")
        favRepo.add("s2", Language.ENGLISH, "world")
        favRepo.add("s3", Language.RUSSIAN, "привет")

        val vm = FavoritesViewModel(favRepo, dictionaryRepository(favRepo))
        vm.loadAndApplyState("")

        // Switch to Russian (only 1 favorite)
        vm.setSelectedLanguage(Language.RUSSIAN)
        vm.loadAndApplyState("")
        assertEquals(Language.RUSSIAN, contentState(vm).selectedLanguage)

        // Remove the only Russian favorite and reload (simulates toggleFavorite's logic)
        favRepo.remove("s3", Language.RUSSIAN)
        vm.loadAndApplyState("")

        val content = contentState(vm)
        assertEquals(Language.ENGLISH, content.selectedLanguage,
            "Should switch to the remaining language")
        assertFalse(content.showLanguagePicker, "Picker should hide with single language left")
        assertEquals(2, content.senses.size, "Should show English favorites")
    }

    @Test
    fun removingLastVisibleResult_withActiveQuery_staysOnSameLanguage() = runTest(testDispatcher) {
        val favRepo = favoritesRepository()
        favRepo.deleteAll()
        favRepo.add("s1", Language.ENGLISH, "hello")
        favRepo.add("s2", Language.ENGLISH, "world")
        favRepo.add("s3", Language.RUSSIAN, "привет")

        val vm = FavoritesViewModel(favRepo, dictionaryRepository(favRepo))
        vm.loadAndApplyState("")

        // Search for "hello" — only matches s1 in English
        vm.loadAndApplyState("hello")

        var content = contentState(vm)
        assertEquals(1, content.senses.size)
        assertEquals("s1", content.senses[0].senseId)

        // Remove "hello" — English still has "world", should stay on English
        favRepo.remove("s1", Language.ENGLISH)
        vm.loadAndApplyState("hello")

        content = contentState(vm)
        assertEquals(Language.ENGLISH, content.selectedLanguage,
            "Should stay on English since 'world' still exists")
        assertTrue(content.showLanguagePicker, "Picker should remain visible")
    }

    @Test
    fun queryResults_scopedToSelectedLanguage() = runTest(testDispatcher) {
        val favRepo = favoritesRepository()
        favRepo.deleteAll()
        // Same lemma in two languages
        favRepo.add("s1", Language.ENGLISH, "hello")
        favRepo.add("s2", Language.RUSSIAN, "hello")

        val vm = FavoritesViewModel(favRepo, dictionaryRepository(favRepo))
        vm.loadAndApplyState("")

        // Search for "hello" while on English
        vm.loadAndApplyState("hello")

        val content = contentState(vm)
        assertEquals(Language.ENGLISH, content.selectedLanguage)
        assertEquals(1, content.senses.size, "Should only match within selected language")
        assertEquals("s1", content.senses[0].senseId)
    }

    @Test
    fun dropdownExpandedState_resetsWhenPickerBecomesHidden() = runTest(testDispatcher) {
        val favRepo = favoritesRepository()
        favRepo.deleteAll()
        favRepo.add("s1", Language.ENGLISH, "hello")
        favRepo.add("s2", Language.RUSSIAN, "привет")

        val vm = FavoritesViewModel(favRepo, dictionaryRepository(favRepo))
        vm.loadAndApplyState("")

        // Switch to Russian and open the dropdown
        vm.setSelectedLanguage(Language.RUSSIAN)
        vm.loadAndApplyState("")
        vm.setLanguageDropdownExpanded(true)
        assertTrue(contentState(vm).isLanguageDropdownExpanded)

        // Remove the only Russian favorite — picker becomes hidden
        favRepo.remove("s2", Language.RUSSIAN)
        vm.loadAndApplyState("")

        val content = contentState(vm)
        assertFalse(content.showLanguagePicker)
        assertFalse(content.isLanguageDropdownExpanded,
            "Dropdown expanded state should reset when picker becomes hidden")
    }
}
