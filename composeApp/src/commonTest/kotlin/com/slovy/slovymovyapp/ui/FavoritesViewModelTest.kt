package com.slovy.slovymovyapp.ui

import androidx.lifecycle.ViewModelStore
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.remote.*
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.speech.FakeSpeechPlayer
import com.slovy.slovymovyapp.speech.VoiceFilterHelper
import com.slovy.slovymovyapp.db.AppDatabase
import com.slovy.slovymovyapp.i18n.UiText
import com.slovy.slovymovyapp.ui.favorites.*
import com.slovy.slovymovyapp.test.BaseTest
import kotlinx.coroutines.test.runTest
import slovymovyapp.composeapp.generated.resources.Res
import slovymovyapp.composeapp.generated.resources.duration_unit_hours
import slovymovyapp.composeapp.generated.resources.duration_unit_minutes
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.test.*

@OptIn(ExperimentalTime::class)
open class FavoritesViewModelTest : BaseTest() {

    private val viewModelStore = ViewModelStore()

    private companion object {
        const val SENSE_1 = "00000000-0000-0000-0000-000000000101"
        const val SENSE_2 = "00000000-0000-0000-0000-000000000102"
        const val SENSE_3 = "00000000-0000-0000-0000-000000000103"
        val TEST_NOW: Instant = Instant.parse("2026-05-14T12:00:00Z")
    }

    private val testClock = object : Clock {
        override fun now(): Instant = TEST_NOW
    }

    @BeforeTest
    fun setUp() = runTest {
        // Clear persisted language prefs so tests don't see each other's selections.
        val repo = SettingsRepository(testAppDatabaseHolder().database)
        repo.deleteById(Setting.Name.FAVORITES_LANGUAGE)
        repo.deleteById(Setting.Name.SEARCH_LANGUAGE)
    }

    @AfterTest
    fun tearDown() {
        viewModelStore.clear()
    }

    private fun favoritesRepository(): FavoritesRepository {
        return FavoritesRepository(testAppDatabaseHolder().database)
    }

    private fun favoritesRepository(app: AppDatabase): FavoritesRepository {
        return FavoritesRepository(app)
    }

    private fun dictionaryRepository(favoritesRepo: FavoritesRepository): DictionaryRepository {
        return DictionaryRepository(
            testDataDbManager(),
            testLocalDbManager(),
            favoritesRepo,
            SettingsRepository(testAppDatabaseHolder().database)
        )
    }

    private fun createViewModel(
        favRepo: FavoritesRepository,
        dictRepo: DictionaryRepository = dictionaryRepository(favRepo),
        settingsRepo: SettingsRepository = SettingsRepository(testAppDatabaseHolder().database),
        clock: Clock = testClock,
    ): FavoritesViewModel {
        val vm = FavoritesViewModel(
            favRepo,
            dictRepo,
            settingsRepo,
            FakeSpeechPlayer(),
            VoiceFilterHelper(settingsRepo),
            clock,
        )
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
        favRepo.add(SENSE_1, Language.ENGLISH, "hello")
        favRepo.add(SENSE_2, Language.ENGLISH, "world")

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
        favRepo.add(SENSE_1, Language.ENGLISH, "hello")
        favRepo.add(SENSE_2, Language.RUSSIAN, "привет")

        val vm = createViewModel(favRepo)
        vm.loadAndApplyState("")

        val content = contentState(vm)
        assertTrue(content.showLanguagePicker)
        assertEquals(listOf(Language.ENGLISH, Language.RUSSIAN), content.availableLanguages)
        assertEquals(Language.ENGLISH, content.selectedLanguage, "Should default to first language")
        assertTrue(
            content.senses.all { it.targetLang == Language.ENGLISH },
            "Should only show selected language's favorites"
        )
    }

    @Test
    fun setSelectedLanguage_filtersFavoritesToThatLanguage() = runTest {
        val favRepo = favoritesRepository()
        favRepo.deleteAll()
        favRepo.add(SENSE_1, Language.ENGLISH, "hello")
        favRepo.add(SENSE_2, Language.ENGLISH, "world")
        favRepo.add(SENSE_3, Language.RUSSIAN, "привет")

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
        favRepo.add(SENSE_1, Language.ENGLISH, "hello")
        favRepo.add(SENSE_2, Language.ENGLISH, "world")
        favRepo.add(SENSE_3, Language.RUSSIAN, "привет")

        val vm = createViewModel(favRepo)
        vm.loadAndApplyState("")

        // Switch to Russian (only 1 favorite)
        vm.setSelectedLanguage(Language.RUSSIAN)
        vm.loadAndApplyState("")
        assertEquals(Language.RUSSIAN, contentState(vm).selectedLanguage)

        // Remove the only Russian favorite and reload (simulates toggleFavorite's logic)
        favRepo.remove(SENSE_3, Language.RUSSIAN)
        vm.loadAndApplyState("")

        val content = contentState(vm)
        assertEquals(
            Language.ENGLISH, content.selectedLanguage,
            "Should switch to the remaining language"
        )
        assertFalse(content.showLanguagePicker, "Picker should hide with single language left")
        assertEquals(2, content.senses.size, "Should show English favorites")
    }

    @Test
    fun removingLastVisibleResult_withActiveQuery_staysOnSameLanguage() = runTest {
        val favRepo = favoritesRepository()
        favRepo.deleteAll()
        favRepo.add(SENSE_1, Language.ENGLISH, "hello")
        favRepo.add(SENSE_2, Language.ENGLISH, "world")
        favRepo.add(SENSE_3, Language.RUSSIAN, "привет")

        val vm = createViewModel(favRepo)
        vm.loadAndApplyState("")

        // Search for "hello" — only matches s1 in English
        vm.loadAndApplyState("hello")

        var content = contentState(vm)
        assertEquals(1, content.senses.size)
        assertEquals(SENSE_1, content.senses[0].senseId)

        // Remove "hello" — English still has "world", should stay on English
        favRepo.remove(SENSE_1, Language.ENGLISH)
        vm.loadAndApplyState("hello")

        content = contentState(vm)
        assertEquals(
            Language.ENGLISH, content.selectedLanguage,
            "Should stay on English since 'world' still exists"
        )
        assertTrue(content.showLanguagePicker, "Picker should remain visible")
    }

    @Test
    fun queryResults_scopedToSelectedLanguage() = runTest {
        val favRepo = favoritesRepository()
        favRepo.deleteAll()
        // Same lemma in two languages
        favRepo.add(SENSE_1, Language.ENGLISH, "hello")
        favRepo.add(SENSE_2, Language.RUSSIAN, "hello")

        val vm = createViewModel(favRepo)
        vm.loadAndApplyState("")

        // Search for "hello" while on English
        vm.loadAndApplyState("hello")

        val content = contentState(vm)
        assertEquals(Language.ENGLISH, content.selectedLanguage)
        assertEquals(1, content.senses.size, "Should only match within selected language")
        assertEquals(SENSE_1, content.senses[0].senseId)
    }

    @Test
    fun requestScrollToTop_incrementsScrollToTopVersionOnNextLoad() = runTest {
        val favRepo = favoritesRepository()
        favRepo.deleteAll()
        favRepo.add(SENSE_1, Language.ENGLISH, "hello")

        val vm = createViewModel(favRepo)
        vm.loadAndApplyState("")
        assertFalse(contentState(vm).scrollToTop, "scrollToTop should start as false")

        vm.requestScrollToTop()
        vm.loadAndApplyState("")

        assertTrue(
            contentState(vm).scrollToTop,
            "scrollToTop should be true after requestScrollToTop + load"
        )
    }

    @Test
    fun requestScrollToTop_raceCondition_scrollHappensEvenWhenInitialLoadRunsFirst() = runTest {
        val favRepo = favoritesRepository()
        favRepo.deleteAll()

        val vm = createViewModel(favRepo)
        // Simulate Favorites screen's initial loadFavorites() running before the favorite is added
        vm.loadAndApplyState("")
        assertFalse(contentState(vm).scrollToTop)

        // Favorite is added and the app asks Favorites to scroll after the initial load
        favRepo.add(SENSE_1, Language.ENGLISH, "hello")
        vm.requestScrollToTop() // sets the pending flag
        vm.loadAndApplyState("") // simulate the subsequent Favorites reload (debounced queryFlow)

        val content = contentState(vm)
        assertEquals(1, content.senses.size, "New favorite should be present")
        assertTrue(
            content.scrollToTop,
            "Should scroll to top even when the initial Favorites load ran before the app requested it"
        )
    }

    @Test
    fun requestScrollToTop_scrollsEvenWhenFilterActive() = runTest {
        val favRepo = favoritesRepository()
        favRepo.deleteAll()
        favRepo.add(SENSE_1, Language.ENGLISH, "hello")
        favRepo.add(SENSE_2, Language.ENGLISH, "world")

        val vm = createViewModel(favRepo)
        vm.loadAndApplyState("")

        // Add a new favorite and request scroll while a query is active
        favRepo.add(SENSE_3, Language.ENGLISH, "newword")
        vm.requestScrollToTop()
        vm.loadAndApplyState("hello") // query hides s3

        assertTrue(
            contentState(vm).scrollToTop,
            "Should scroll to top of the filtered results immediately, flag is not kept pending"
        )
    }

    @Test
    fun requestScrollToTop_scrollsEvenWhenSenseRemovedBeforeReload() = runTest {
        val favRepo = favoritesRepository()
        favRepo.deleteAll()
        favRepo.add(SENSE_1, Language.ENGLISH, "hello")

        val vm = createViewModel(favRepo)
        vm.loadAndApplyState("")

        // Add then immediately remove before reload
        favRepo.add(SENSE_2, Language.ENGLISH, "world")
        vm.requestScrollToTop()
        favRepo.remove(SENSE_2, Language.ENGLISH)
        vm.loadAndApplyState("")

        assertTrue(
            contentState(vm).scrollToTop,
            "Should still scroll to top even if the added sense was removed before reload"
        )
    }

    @Test
    fun withoutCachedFavoriteDetails_dropsLoadedSenseDetails() {
        val state = FavoritesUiState.Content(
            senses = listOf(
                FavoriteSenseItem(
                    senseId = SENSE_1,
                    targetLang = Language.ENGLISH,
                    lemma = "hello",
                    createdAt = 0L,
                    sense = LanguageCardResponseSense(
                        senseId = SENSE_1,
                        senseDefinition = "stale definition",
                        learnerLevel = LearnerLevel.A1,
                        frequency = SenseFrequency.HIGH,
                        semanticGroupId = "group",
                    ),
                    relatedWords = mapOf("world" to RelatedWord("world", 5f, online = false)),
                    pos = PartOfSpeech.NOUN,
                    expanded = true,
                    loading = true,
                    error = UiText.Plain("stale error"),
                )
            ),
            hasAnyFavorites = true,
        )

        val item = state.withoutCachedFavoriteDetails().senses.single()

        assertEquals(SENSE_1, item.senseId)
        assertEquals(Language.ENGLISH, item.targetLang)
        assertEquals("hello", item.lemma)
        assertNull(item.sense)
        assertTrue(item.relatedWords.isEmpty())
        assertNull(item.pos)
        assertFalse(item.expanded)
        assertFalse(item.loading)
        assertNull(item.error)
    }

    @Test
    fun loadAndApplyState_showsStudyCardForDueCard() = runTest {
        val favRepo = favoritesRepository()
        favRepo.deleteAll()
        favRepo.add(SENSE_1, Language.ENGLISH, "hello")

        val vm = createViewModel(favRepo)
        vm.updateReviewDueCounts(mapOf(Language.ENGLISH to 1))
        vm.loadAndApplyState("")

        val study = assertNotNull(contentState(vm).study)
        assertEquals(Language.ENGLISH, study.language)
        assertEquals(1, study.dueCount)
    }

    @Test
    fun scrollToTop_notRetriggered_afterVersionConsumed() = runTest {
        val favRepo = favoritesRepository()
        favRepo.deleteAll()
        favRepo.add(SENSE_1, Language.ENGLISH, "hello")

        val vm = createViewModel(favRepo)
        vm.loadAndApplyState("")

        // Add a favorite and trigger scroll
        favRepo.add(SENSE_2, Language.ENGLISH, "world")
        vm.requestScrollToTop()
        vm.loadAndApplyState("")
        assertTrue(contentState(vm).scrollToTop, "scrollToTop should be true after add")

        // Composable consumes the scroll event (as LaunchedEffect does after scrollToItem)
        vm.consumeScrollToTop()
        assertFalse(contentState(vm).scrollToTop, "scrollToTop should reset to false after consume")

        // Simulate re-entering Favorites (loadFavorites fires on screen entry, no new add)
        vm.loadAndApplyState("")
        assertFalse(
            contentState(vm).scrollToTop,
            "Re-entering Favorites without a new add must not re-trigger scroll"
        )
    }

    @Test
    fun dropdownExpandedState_resetsWhenPickerBecomesHidden() = runTest {
        val favRepo = favoritesRepository()
        favRepo.deleteAll()
        favRepo.add(SENSE_1, Language.ENGLISH, "hello")
        favRepo.add(SENSE_2, Language.RUSSIAN, "привет")

        val vm = createViewModel(favRepo)
        vm.loadAndApplyState("")

        // Switch to Russian and open the dropdown
        vm.setSelectedLanguage(Language.RUSSIAN)
        vm.loadAndApplyState("")
        vm.setLanguageDropdownExpanded(true)
        assertTrue(contentState(vm).isLanguageDropdownExpanded)

        // Remove the only Russian favorite — picker becomes hidden
        favRepo.remove(SENSE_2, Language.RUSSIAN)
        vm.loadAndApplyState("")

        val content = contentState(vm)
        assertFalse(content.showLanguagePicker)
        assertFalse(
            content.isLanguageDropdownExpanded,
            "Dropdown expanded state should reset when picker becomes hidden"
        )
    }

    @Test
    fun studyState_showsDueCountForSelectedLanguage() = runTest {
        val favRepo = favoritesRepository()
        favRepo.deleteAll()
        favRepo.add(SENSE_1, Language.ENGLISH, "hello")

        val vm = createViewModel(favRepo)
        vm.updateReviewDueCounts(mapOf(Language.ENGLISH to 1))
        vm.loadAndApplyState("")

        val study = assertNotNull(contentState(vm).study)
        assertEquals(Language.ENGLISH, study.language)
        assertEquals(1, study.dueCount)
        assertEquals(1, study.estimatedMinutes)
    }

    @Test
    fun studyDoneState_hiddenWhenNoCardsAreInLearning() = runTest {
        val favRepo = favoritesRepository()
        favRepo.deleteAll()
        favRepo.add(SENSE_1, Language.ENGLISH, "hello")

        val vm = createViewModel(favRepo)
        vm.updateReviewState(
            mapOf(
                Language.ENGLISH to FavoriteLanguageReviewUiState(
                    dueCount = 0,
                    activeCardCount = 0,
                    delayedDueLemmaCount = 0,
                    nextReviewAtEpochMs = (TEST_NOW + 1.hours).toEpochMilliseconds(),
                )
            )
        )
        vm.loadAndApplyState("")

        assertNull(contentState(vm).studyDone)
    }

    @Test
    fun studyDoneState_showsActualHourMinuteTimeForFutureReview() = runTest {
        val favRepo = favoritesRepository()
        favRepo.deleteAll()
        favRepo.add(SENSE_1, Language.ENGLISH, "hello")

        val vm = createViewModel(favRepo)
        vm.updateReviewState(
            mapOf(
                Language.ENGLISH to FavoriteLanguageReviewUiState(
                    dueCount = 0,
                    activeCardCount = 1,
                    delayedDueLemmaCount = 0,
                    nextReviewAtEpochMs = (TEST_NOW + 1.hours + 15.minutes).toEpochMilliseconds(),
                )
            )
        )
        vm.loadAndApplyState("")

        val studyDone = assertNotNull(contentState(vm).studyDone)
        assertEquals(Language.ENGLISH, studyDone.language)
        assertEquals(
            UiText.Joined(
                items = listOf(
                    UiText.Plain("1"),
                    UiText.Resource(Res.string.duration_unit_hours),
                    UiText.Plain("15"),
                    UiText.Resource(Res.string.duration_unit_minutes),
                ),
                separator = " ",
            ),
            studyDone.nextReviewLabel,
        )
        assertNull(studyDone.action)
        assertFalse(studyDone.canContinueNow)
    }

    @Test
    fun studyDoneState_reviewMoreOnlyWhenDueCardsAreDelayed() = runTest {
        val favRepo = favoritesRepository()
        favRepo.deleteAll()
        favRepo.add(SENSE_1, Language.ENGLISH, "hello")

        val vm = createViewModel(favRepo)
        vm.updateReviewState(
            mapOf(
                Language.ENGLISH to FavoriteLanguageReviewUiState(
                    dueCount = 0,
                    activeCardCount = 1,
                    delayedDueLemmaCount = 2,
                    nextReviewAtEpochMs = (TEST_NOW + 12.minutes).toEpochMilliseconds(),
                )
            )
        )
        vm.loadAndApplyState("")

        val delayedStudyDone = assertNotNull(contentState(vm).studyDone)
        assertEquals(
            UiText.Joined(
                items = listOf(
                    UiText.Plain("12"),
                    UiText.Resource(Res.string.duration_unit_minutes),
                ),
                separator = " ",
            ),
            delayedStudyDone.nextReviewLabel,
        )
        assertEquals(FavoritesStudyDoneAction.REVIEW_MORE, delayedStudyDone.action)
        assertTrue(delayedStudyDone.canContinueNow)

        vm.updateReviewState(
            mapOf(
                Language.ENGLISH to FavoriteLanguageReviewUiState(
                    dueCount = 0,
                    activeCardCount = 1,
                    delayedDueLemmaCount = 0,
                    nextReviewAtEpochMs = (TEST_NOW + 12.minutes).toEpochMilliseconds(),
                )
            )
        )

        val notDelayedStudyDone = assertNotNull(contentState(vm).studyDone)
        assertNull(notDelayedStudyDone.action)
        assertFalse(notDelayedStudyDone.canContinueNow)
    }

    @Test
    fun studyDoneState_studyNewWhenQueuedFavoritesRemain() = runTest {
        val favRepo = favoritesRepository()
        favRepo.deleteAll()
        favRepo.add(SENSE_1, Language.ENGLISH, "hello")

        val vm = createViewModel(favRepo)
        vm.updateReviewState(
            mapOf(
                Language.ENGLISH to FavoriteLanguageReviewUiState(
                    dueCount = 0,
                    activeCardCount = 1,
                    delayedDueLemmaCount = 0,
                    pendingFavoriteLemmaCount = 2,
                    nextReviewAtEpochMs = (TEST_NOW + 12.minutes).toEpochMilliseconds(),
                )
            )
        )
        vm.loadAndApplyState("")

        val studyDone = assertNotNull(contentState(vm).studyDone)
        assertEquals(FavoritesStudyDoneAction.STUDY_NEW, studyDone.action)
        assertTrue(studyDone.canContinueNow)
    }

    @Test
    fun studyDoneState_hidesStudyNewWhenQueuedFavoriteIntakeIsPaused() = runTest {
        val favRepo = favoritesRepository()
        favRepo.deleteAll()
        favRepo.add(SENSE_1, Language.ENGLISH, "hello")

        val vm = createViewModel(favRepo)
        vm.updateReviewState(
            mapOf(
                Language.ENGLISH to FavoriteLanguageReviewUiState(
                    dueCount = 0,
                    activeCardCount = 1,
                    delayedDueLemmaCount = 0,
                    pendingFavoriteLemmaCount = 2,
                    canStudyPendingFavoritesNow = false,
                    nextReviewAtEpochMs = (TEST_NOW + 12.minutes).toEpochMilliseconds(),
                )
            )
        )
        vm.loadAndApplyState("")

        val studyDone = assertNotNull(contentState(vm).studyDone)
        assertNull(studyDone.action)
        assertFalse(studyDone.canContinueNow)
    }

    @Test
    fun studyDoneState_hiddenWhenDueCardsAreAvailable() = runTest {
        val favRepo = favoritesRepository()
        favRepo.deleteAll()
        favRepo.add(SENSE_1, Language.ENGLISH, "hello")

        val vm = createViewModel(favRepo)
        vm.updateReviewState(
            mapOf(
                Language.ENGLISH to FavoriteLanguageReviewUiState(
                    dueCount = 1,
                    activeCardCount = 1,
                    delayedDueLemmaCount = 0,
                    nextReviewAtEpochMs = (TEST_NOW + 1.hours).toEpochMilliseconds(),
                )
            )
        )
        vm.loadAndApplyState("")

        val content = contentState(vm)
        assertNotNull(content.study)
        assertNull(content.studyDone)
    }

    @Test
    fun reviewDueCount_aggregatesDueCardsAcrossFavoriteLanguages() = runTest {
        val favRepo = favoritesRepository()
        favRepo.deleteAll()
        favRepo.add(SENSE_1, Language.ENGLISH, "hello")
        favRepo.add(SENSE_2, Language.RUSSIAN, "привет")

        val vm = createViewModel(favRepo)
        vm.updateReviewDueCounts(
            mapOf(
                Language.ENGLISH to 1,
                Language.RUSSIAN to 1,
            )
        )
        vm.loadAndApplyState("")

        val content = contentState(vm)
        assertEquals(2, content.reviewDueCount)
        assertEquals(1, assertNotNull(content.study).dueCount)
    }

}
