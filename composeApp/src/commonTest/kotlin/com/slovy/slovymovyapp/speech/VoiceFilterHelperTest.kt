package com.slovy.slovymovyapp.speech

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.test.BaseTest
import kotlinx.coroutines.runBlocking
import kotlin.test.*

@Suppress("DEPRECATION")
open class VoiceFilterHelperTest : BaseTest() {

    private val testLanguage = Text2SpeechLanguage(
        language = Language.ENGLISH,
        isAvailable = true,
        missingData = false
    )

    private val testVoices = listOf(
        Text2SpeechVoice(
            id = "voice1",
            name = "Voice 1",
            language = Language.ENGLISH,
            localeTag = "en-US",
            quality = VoiceQuality.BEST,
            networkConnectionRequired = false,
            enabledByDefault = true
        ),
        Text2SpeechVoice(
            id = "voice2",
            name = "Voice 2",
            language = Language.ENGLISH,
            localeTag = "en-US",
            quality = VoiceQuality.GOOD,
            networkConnectionRequired = true,
            enabledByDefault = false
        ),
        Text2SpeechVoice(
            id = "voice3",
            name = "Voice 3",
            language = Language.ENGLISH,
            localeTag = "en-GB",
            quality = VoiceQuality.MEDIUM,
            networkConnectionRequired = false,
            enabledByDefault = true
        )
    )

    private fun settingsRepository(): SettingsRepository {
        return SettingsRepository(testAppDatabaseHolder().database)
    }

    @BeforeTest
    fun before() {
        runBlocking {
            val repo = settingsRepository()
            repo.deleteById(Setting.Name.ENABLED_VOICES)
            repo.deleteById(Setting.Name.VOICE_SETUP_SHOWN)
        }
    }

    @Test
    fun getEnabledVoices_returns_empty_when_no_setting() = runBlocking {
        val repo = settingsRepository()
        val helper = VoiceFilterHelper(repo)

        val result = helper.getEnabledVoices(testLanguage)

        assertTrue(result.isEmpty())
    }

    @Test
    fun setEnabledVoices_stores_voice_ids() = runBlocking {
        val repo = settingsRepository()
        val helper = VoiceFilterHelper(repo)

        val voiceIds = setOf("voice1", "voice3")
        helper.setEnabledVoices(testLanguage, voiceIds)

        val result = helper.getEnabledVoices(testLanguage)
        assertEquals(voiceIds, result)
    }

    @Test
    fun setEnabledVoices_updates_existing_setting() = runBlocking {
        val repo = settingsRepository()
        val helper = VoiceFilterHelper(repo)

        // Set initial voices
        helper.setEnabledVoices(testLanguage, setOf("voice1"))

        // Update with new voices
        val newVoiceIds = setOf("voice2", "voice3")
        helper.setEnabledVoices(testLanguage, newVoiceIds)

        val result = helper.getEnabledVoices(testLanguage)
        assertEquals(newVoiceIds, result)
    }

    @Test
    fun setEnabledVoices_maintains_other_languages() = runBlocking {
        val repo = settingsRepository()
        val helper = VoiceFilterHelper(repo)

        val otherLanguage = Text2SpeechLanguage(
            language = Language.RUSSIAN,
            isAvailable = true,
            missingData = false
        )

        // Set voices for English
        helper.setEnabledVoices(testLanguage, setOf("voice1"))

        // Set voices for Ukrainian
        helper.setEnabledVoices(otherLanguage, setOf("voice_uk1"))

        // Verify both are maintained
        val englishVoices = helper.getEnabledVoices(testLanguage)
        val ukrainianVoices = helper.getEnabledVoices(otherLanguage)

        assertEquals(setOf("voice1"), englishVoices)
        assertEquals(setOf("voice_uk1"), ukrainianVoices)
    }

    @Test
    fun isVoiceEnabled_returns_true_when_no_filter_set() = runBlocking {
        val repo = settingsRepository()
        val helper = VoiceFilterHelper(repo)

        val result = helper.isVoiceEnabled("voice1", testLanguage)

        assertTrue(result)
    }

    @Test
    fun isVoiceEnabled_returns_true_for_enabled_voice() = runBlocking {
        val repo = settingsRepository()
        val helper = VoiceFilterHelper(repo)

        helper.setEnabledVoices(testLanguage, setOf("voice1", "voice2"))

        assertTrue(helper.isVoiceEnabled("voice1", testLanguage))
        assertTrue(helper.isVoiceEnabled("voice2", testLanguage))
    }

    @Test
    fun isVoiceEnabled_returns_false_for_disabled_voice() = runBlocking {
        val repo = settingsRepository()
        val helper = VoiceFilterHelper(repo)

        helper.setEnabledVoices(testLanguage, setOf("voice1"))

        assertFalse(helper.isVoiceEnabled("voice2", testLanguage))
    }

    @Test
    fun isVoiceEnabled_returns_false_when_voice_list_is_empty() = runBlocking {
        val repo = settingsRepository()
        val helper = VoiceFilterHelper(repo)

        helper.setEnabledVoices(testLanguage, emptySet())

        assertFalse(helper.isVoiceEnabled("voice1", testLanguage))
    }

    @Test
    fun initializeDefaultVoices_selects_default_enabled_voices_only() = runBlocking {
        val repo = settingsRepository()
        val helper = VoiceFilterHelper(repo)

        val result = helper.initializeDefaultVoices(testLanguage, testVoices)

        assertEquals(setOf("voice1", "voice3"), result)

        val saved = helper.getEnabledVoices(testLanguage)
        assertEquals(setOf("voice1", "voice3"), saved)
    }

    @Test
    fun initializeDefaultVoices_excludes_default_disabled_local_voices() = runBlocking {
        val repo = settingsRepository()
        val helper = VoiceFilterHelper(repo)
        val voices = testVoices + Text2SpeechVoice(
            id = "novelty",
            name = "Bubbles",
            language = Language.ENGLISH,
            localeTag = "en-US",
            quality = VoiceQuality.BEST,
            networkConnectionRequired = false,
            enabledByDefault = false
        )

        val result = helper.initializeDefaultVoices(testLanguage, voices)

        assertEquals(setOf("voice1", "voice3"), result)
    }

    @Test
    fun initializeDefaultVoices_selects_all_default_enabled_voices() = runBlocking {
        val repo = settingsRepository()
        val helper = VoiceFilterHelper(repo)
        val voices = (1..6).map { index ->
            Text2SpeechVoice(
                id = "voice$index",
                name = "Voice $index",
                language = Language.ENGLISH,
                localeTag = "en-US",
                quality = if (index <= 4) VoiceQuality.BEST else VoiceQuality.GOOD,
                networkConnectionRequired = false,
                enabledByDefault = true
            )
        }

        val result = helper.initializeDefaultVoices(testLanguage, voices)

        assertEquals(setOf("voice1", "voice2", "voice3", "voice4", "voice5", "voice6"), result)
    }

    @Test
    fun initializeDefaultVoices_preserves_configured_legacy_default_selection() = runBlocking {
        val repo = settingsRepository()
        val helper = VoiceFilterHelper(repo)
        val voices = (1..10).map { index ->
            Text2SpeechVoice(
                id = "voice$index",
                name = "Voice $index",
                language = Language.ENGLISH,
                localeTag = "en-US",
                quality = if (index <= 4) VoiceQuality.BEST else VoiceQuality.GOOD,
                networkConnectionRequired = false,
                enabledByDefault = index <= 4
            )
        }
        val savedSelection = voices.map { it.id }.toSet()
        helper.setEnabledVoices(testLanguage, savedSelection)

        val result = helper.initializeDefaultVoices(testLanguage, voices)

        assertEquals(savedSelection, result)
    }

    @Test
    fun migrateLegacyDefaultVoiceSelection_repairs_selection_created_by_old_default_selector() = runBlocking {
        val repo = settingsRepository()
        val helper = VoiceFilterHelper(repo)
        val voices = (1..10).map { index ->
            Text2SpeechVoice(
                id = "voice$index",
                name = "Voice $index",
                language = Language.ENGLISH,
                localeTag = "en-US",
                quality = if (index <= 4) VoiceQuality.BEST else VoiceQuality.GOOD,
                networkConnectionRequired = false,
                enabledByDefault = index <= 4
            )
        }
        helper.setEnabledVoices(testLanguage, voices.map { it.id }.toSet())

        val migrated = helper.migrateLegacyDefaultVoiceSelection(testLanguage, voices)

        assertTrue(migrated)
        val result = helper.getEnabledVoices(testLanguage)
        assertEquals(setOf("voice1", "voice2", "voice3", "voice4"), result)
    }

    @Test
    fun migrateLegacyDefaultVoiceSelection_preserves_selection_with_manual_extra_voice() = runBlocking {
        val repo = settingsRepository()
        val helper = VoiceFilterHelper(repo)
        val legacyVoices = (1..10).map { index ->
            Text2SpeechVoice(
                id = "voice$index",
                name = "Voice $index",
                language = Language.ENGLISH,
                localeTag = "en-US",
                quality = if (index <= 4) VoiceQuality.BEST else VoiceQuality.GOOD,
                networkConnectionRequired = false,
                enabledByDefault = index <= 4
            )
        }
        val manualExtraVoice = Text2SpeechVoice(
            id = "manualExtra",
            name = "Manual Extra",
            language = Language.ENGLISH,
            localeTag = "en-US",
            quality = VoiceQuality.BEST,
            networkConnectionRequired = true,
            enabledByDefault = false
        )
        val savedSelection = legacyVoices.map { it.id }.toSet() + manualExtraVoice.id
        helper.setEnabledVoices(testLanguage, savedSelection)

        val migrated = helper.migrateLegacyDefaultVoiceSelection(testLanguage, legacyVoices + manualExtraVoice)
        val result = helper.getEnabledVoices(testLanguage)

        assertFalse(migrated)
        assertEquals(savedSelection, result)
    }

    @Test
    fun initializeDefaultVoices_preserves_manual_selection() = runBlocking {
        val repo = settingsRepository()
        val helper = VoiceFilterHelper(repo)
        helper.setEnabledVoices(testLanguage, setOf("voice3"))

        val result = helper.initializeDefaultVoices(testLanguage, testVoices)

        assertEquals(setOf("voice3"), result)
    }

    @Test
    fun initializeDefaultVoices_preserves_manual_selection_with_disabled_voice() = runBlocking {
        val repo = settingsRepository()
        val helper = VoiceFilterHelper(repo)
        val voices = testVoices + Text2SpeechVoice(
            id = "novelty",
            name = "Bubbles",
            language = Language.ENGLISH,
            localeTag = "en-US",
            quality = VoiceQuality.BEST,
            networkConnectionRequired = false,
            enabledByDefault = false
        )
        helper.setEnabledVoices(testLanguage, setOf("novelty"))

        val result = helper.initializeDefaultVoices(testLanguage, voices)

        assertEquals(setOf("novelty"), result)
    }

    @Test
    fun initializeDefaultVoices_preserves_empty_manual_selection() = runBlocking {
        val repo = settingsRepository()
        val helper = VoiceFilterHelper(repo)
        helper.setEnabledVoices(testLanguage, emptySet())

        val result = helper.initializeDefaultVoices(testLanguage, testVoices)

        assertTrue(result.isEmpty())
    }

    @Test
    fun filterVoicesByEnabled_returns_all_when_no_filter() = runBlocking {
        val repo = settingsRepository()
        val helper = VoiceFilterHelper(repo)

        val result = helper.filterVoicesByEnabled(testVoices, testLanguage)

        assertEquals(testVoices, result)
    }

    @Test
    fun filterVoicesByEnabled_filters_correctly() = runBlocking {
        val repo = settingsRepository()
        val helper = VoiceFilterHelper(repo)

        helper.setEnabledVoices(testLanguage, setOf("voice1", "voice3"))

        val result = helper.filterVoicesByEnabled(testVoices, testLanguage)

        assertEquals(2, result.size)
        assertTrue(result.any { it.id == "voice1" })
        assertTrue(result.any { it.id == "voice3" })
        assertFalse(result.any { it.id == "voice2" })
    }

    @Test
    fun filterVoicesByEnabled_returns_empty_when_voice_list_is_empty() = runBlocking {
        val repo = settingsRepository()
        val helper = VoiceFilterHelper(repo)

        helper.setEnabledVoices(testLanguage, emptySet())

        val result = helper.filterVoicesByEnabled(testVoices, testLanguage)

        assertTrue(result.isEmpty())
    }

    @Test
    fun getEnabledVoices_returns_empty_when_no_repository() = runBlocking {
        val helper = VoiceFilterHelper(null)

        val result = helper.getEnabledVoices(testLanguage)

        assertTrue(result.isEmpty())
    }

    @Test
    fun setEnabledVoices_does_nothing_when_no_repository() = runBlocking {
        val helper = VoiceFilterHelper(null)

        helper.setEnabledVoices(testLanguage, setOf("voice1"))

        val result = helper.getEnabledVoices(testLanguage)
        assertTrue(result.isEmpty())
    }

    @Test
    fun reconcileVoicesForEngineChange_reapplies_defaults_when_stored_ids_are_from_another_engine() =
        runBlocking {
            val helper = VoiceFilterHelper(settingsRepository())
            helper.setEnabledVoices(testLanguage, setOf("rhvoice-anna", "rhvoice-elena"))

            val result = helper.reconcileVoicesForEngineChange(testLanguage, testVoices)

            assertEquals(
                setOf("voice1", "voice3"),
                result,
                "Selection from an unbound engine should be replaced by the current engine's defaults"
            )
            assertEquals(setOf("voice1", "voice3"), helper.getEnabledVoices(testLanguage))
        }

    @Test
    fun reconcileVoicesForEngineChange_keeps_selection_that_overlaps_current_engine() = runBlocking {
        val helper = VoiceFilterHelper(settingsRepository())
        helper.setEnabledVoices(testLanguage, setOf("voice1"))

        val result = helper.reconcileVoicesForEngineChange(testLanguage, testVoices)

        assertEquals(
            setOf("voice1"),
            result,
            "A deliberate single-voice choice on the bound engine must not be widened to the defaults"
        )
    }

    @Test
    fun reconcileVoicesForEngineChange_keeps_selection_when_engine_reports_no_voices() = runBlocking {
        val helper = VoiceFilterHelper(settingsRepository())
        helper.setEnabledVoices(testLanguage, setOf("voice1"))

        val result = helper.reconcileVoicesForEngineChange(testLanguage, emptyList())

        assertEquals(
            setOf("voice1"),
            result,
            "An engine that reports no voices yet must not clear the stored selection"
        )
    }

    @Test
    fun reconcileVoicesForEngineChange_writes_nothing_when_no_selection_stored() = runBlocking {
        val helper = VoiceFilterHelper(settingsRepository())

        val result = helper.reconcileVoicesForEngineChange(testLanguage, testVoices)

        assertTrue(result.isEmpty(), "Reconciliation should defer to initializeDefaultVoices")
        assertFalse(
            helper.hasEnabledVoices(testLanguage),
            "Reconciliation must not create a selection where none existed"
        )
    }

    @Test
    fun isVoiceSetupShown_returns_false_when_no_setting() = runBlocking {
        val helper = VoiceFilterHelper(settingsRepository())

        assertFalse(helper.isVoiceSetupShown(Language.ENGLISH))
    }

    @Test
    fun markVoiceSetupShown_marks_language_as_shown() = runBlocking {
        val helper = VoiceFilterHelper(settingsRepository())

        helper.markVoiceSetupShown(Language.ENGLISH)

        assertTrue(helper.isVoiceSetupShown(Language.ENGLISH))
    }

    @Test
    fun markVoiceSetupShown_does_not_affect_other_languages() = runBlocking {
        val helper = VoiceFilterHelper(settingsRepository())

        helper.markVoiceSetupShown(Language.ENGLISH)

        assertFalse(helper.isVoiceSetupShown(Language.DUTCH))
    }

    @Test
    fun isVoiceSetupShown_returns_true_when_no_repository() = runBlocking {
        val helper = VoiceFilterHelper(null)

        assertTrue(helper.isVoiceSetupShown(Language.ENGLISH))
    }

    @Test
    fun markVoiceSetupShown_does_nothing_when_no_repository() = runBlocking {
        val helper = VoiceFilterHelper(null)

        helper.markVoiceSetupShown(Language.ENGLISH)

        assertTrue(helper.isVoiceSetupShown(Language.ENGLISH))
    }

    @Test
    fun isVoiceEnabled_returns_true_when_no_repository() = runBlocking {
        val helper = VoiceFilterHelper(null)

        val result = helper.isVoiceEnabled("voice1", testLanguage)

        assertTrue(result)
    }
}
