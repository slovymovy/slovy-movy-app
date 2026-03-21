package com.slovy.slovymovyapp.ui

import androidx.lifecycle.ViewModelStore
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.remote.DictionaryRepository
import com.slovy.slovymovyapp.test.BaseTest
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

open class FavoritesViewModelTest : BaseTest() {

    private val viewModelStore = ViewModelStore()

    @AfterTest
    fun tearDown() {
        viewModelStore.clear()
    }

    private fun favoritesRepository(): FavoritesRepository {
        return FavoritesRepository(testAppDatabaseHolder().database)
    }

    private fun dictionaryRepository(favoritesRepo: FavoritesRepository): DictionaryRepository {
        return DictionaryRepository(testDataDbManager(), testLocalDbManager(), favoritesRepo)
    }

    private fun createViewModel(
        favRepo: FavoritesRepository,
        dictRepo: DictionaryRepository = dictionaryRepository(favRepo)
    ): FavoritesViewModel {
        val vm = FavoritesViewModel(favRepo, dictRepo)
        viewModelStore.put("test", vm)
        return vm
    }

    private fun contentState(vm: FavoritesViewModel): FavoritesUiState.Content {
        assertIs<FavoritesUiState.Content>(vm.state, "Expected Content but was ${vm.state::class.simpleName}")
        return vm.state as FavoritesUiState.Content
    }

    @Test
    fun initialLoad_singleLanguage_hiddenPicker() = runTest {
        val favRepo = favoritesRepository()
        favRepo.deleteAll()
        favRepo.add("s1", Language.ENGLISH, "hello")
        favRepo.add("s2", Language.ENGLISH, "world")

        val vm = createViewModel(favRepo)
        vm.loadAndApplyState("")

        val content = contentState(vm)
        assertEquals(2, content.senses.size)
        assertEquals(Language.ENGLISH, content.selectedLanguage)
        assertFalse(content.showLanguagePicker, "Picker should be hidden with single language")
    }

    @Test
    fun initialLoad_multiLanguage_showsPicker_defaultsToFirst() = runTest {
        val favRepo = favoritesRepository()
        favRepo.deleteAll()
        favRepo.add("s1", Language.ENGLISH, "hello")
        favRepo.add("s2", Language.RUSSIAN, "привет")

        val vm = createViewModel(favRepo)
        vm.loadAndApplyState("")

        val content = contentState(vm)
        assertTrue(content.showLanguagePicker)
        assertEquals(listOf(Language.ENGLISH, Language.RUSSIAN), content.availableLanguages)
        assertEquals(Language.ENGLISH, content.selectedLanguage, "Should default to first language")
        assertTrue(content.senses.all { it.targetLang == Language.ENGLISH },
            "Should only show selected language's favorites")
    }

    @Test
    fun setSelectedLanguage_filtersFavoritesToThatLanguage() = runTest {
        val favRepo = favoritesRepository()
        favRepo.deleteAll()
        favRepo.add("s1", Language.ENGLISH, "hello")
        favRepo.add("s2", Language.ENGLISH, "world")
        favRepo.add("s3", Language.RUSSIAN, "привет")

        val vm = createViewModel(favRepo)
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
    fun removingLastFavoriteInLanguage_switchesToOtherLanguage() = runTest {
        val favRepo = favoritesRepository()
        favRepo.deleteAll()
        favRepo.add("s1", Language.ENGLISH, "hello")
        favRepo.add("s2", Language.ENGLISH, "world")
        favRepo.add("s3", Language.RUSSIAN, "привет")

        val vm = createViewModel(favRepo)
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
    fun removingLastVisibleResult_withActiveQuery_staysOnSameLanguage() = runTest {
        val favRepo = favoritesRepository()
        favRepo.deleteAll()
        favRepo.add("s1", Language.ENGLISH, "hello")
        favRepo.add("s2", Language.ENGLISH, "world")
        favRepo.add("s3", Language.RUSSIAN, "привет")

        val vm = createViewModel(favRepo)
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
    fun queryResults_scopedToSelectedLanguage() = runTest {
        val favRepo = favoritesRepository()
        favRepo.deleteAll()
        // Same lemma in two languages
        favRepo.add("s1", Language.ENGLISH, "hello")
        favRepo.add("s2", Language.RUSSIAN, "hello")

        val vm = createViewModel(favRepo)
        vm.loadAndApplyState("")

        // Search for "hello" while on English
        vm.loadAndApplyState("hello")

        val content = contentState(vm)
        assertEquals(Language.ENGLISH, content.selectedLanguage)
        assertEquals(1, content.senses.size, "Should only match within selected language")
        assertEquals("s1", content.senses[0].senseId)
    }

    @Test
    fun requestScrollToTop_incrementsScrollToTopVersionOnNextLoad() = runTest {
        val favRepo = favoritesRepository()
        favRepo.deleteAll()
        favRepo.add("s1", Language.ENGLISH, "hello")

        val vm = createViewModel(favRepo)
        vm.loadAndApplyState("")
        assertEquals(0, contentState(vm).scrollToTopVersion, "Version should start at 0")

        vm.requestScrollToTop()
        vm.loadAndApplyState("")

        assertTrue(contentState(vm).scrollToTopVersion > 0,
            "scrollToTopVersion should be incremented after requestScrollToTop + load")
    }

    @Test
    fun requestScrollToTop_raceCondition_scrollHappensEvenWhenInitialLoadRunsFirst() = runTest {
        val favRepo = favoritesRepository()
        favRepo.deleteAll()

        val vm = createViewModel(favRepo)
        // Simulate Favorites screen's initial loadFavorites() running before the favorite is added
        vm.loadAndApplyState("")
        assertEquals(0, contentState(vm).scrollToTopVersion)

        // Favorite is added and onFavoriteAdded fires after the initial load
        favRepo.add("s1", Language.ENGLISH, "hello")
        vm.requestScrollToTop() // sets the pending flag
        vm.loadAndApplyState("") // simulate the subsequent Favorites reload (debounced queryFlow)

        val content = contentState(vm)
        assertEquals(1, content.senses.size, "New favorite should be present")
        assertTrue(content.scrollToTopVersion > 0,
            "Should scroll to top even when the initial Favorites load ran before onFavoriteAdded")
    }

    @Test
    fun dropdownExpandedState_resetsWhenPickerBecomesHidden() = runTest {
        val favRepo = favoritesRepository()
        favRepo.deleteAll()
        favRepo.add("s1", Language.ENGLISH, "hello")
        favRepo.add("s2", Language.RUSSIAN, "привет")

        val vm = createViewModel(favRepo)
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
