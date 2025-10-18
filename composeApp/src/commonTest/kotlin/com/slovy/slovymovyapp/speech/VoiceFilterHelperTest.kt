package com.slovy.slovymovyapp.speech

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.remote.DataDbManager
import com.slovy.slovymovyapp.data.settings.Setting
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.test.BaseTest
import com.slovy.slovymovyapp.test.platformDbSupport
import kotlinx.coroutines.runBlocking
import kotlin.test.*

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
            quality = VoiceQuality.BEST,
            networkConnectionRequired = false // local voice
        ),
        Text2SpeechVoice(
            id = "voice2",
            name = "Voice 2",
            language = Language.ENGLISH,
            quality = VoiceQuality.GOOD,
            networkConnectionRequired = true // network voice
        ),
        Text2SpeechVoice(
            id = "voice3",
            name = "Voice 3",
            language = Language.ENGLISH,
            quality = VoiceQuality.MEDIUM,
            networkConnectionRequired = false // local voice
        )
    )

    @BeforeTest
    fun before() {
        val db = DataDbManager(platformDbSupport()).openAppDatabase()
        val repo = SettingsRepository(db)
        repo.deleteById(Setting.Name.ENABLED_VOICES)
    }

    @Test
    fun getEnabledVoices_returns_empty_when_no_setting() = runBlocking {
        val db = DataDbManager(platformDbSupport()).openAppDatabase()
        val repo = SettingsRepository(db)
        val helper = VoiceFilterHelper(repo)

        val result = helper.getEnabledVoices(testLanguage)

        assertTrue(result.isEmpty())
    }

    @Test
    fun setEnabledVoices_stores_voice_ids() = runBlocking {
        val db = DataDbManager(platformDbSupport()).openAppDatabase()
        val repo = SettingsRepository(db)
        val helper = VoiceFilterHelper(repo)

        val voiceIds = setOf("voice1", "voice3")
        helper.setEnabledVoices(testLanguage, voiceIds)

        val result = helper.getEnabledVoices(testLanguage)
        assertEquals(voiceIds, result)
    }

    @Test
    fun setEnabledVoices_updates_existing_setting() = runBlocking {
        val db = DataDbManager(platformDbSupport()).openAppDatabase()
        val repo = SettingsRepository(db)
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
        val db = DataDbManager(platformDbSupport()).openAppDatabase()
        val repo = SettingsRepository(db)
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
        val db = DataDbManager(platformDbSupport()).openAppDatabase()
        val repo = SettingsRepository(db)
        val helper = VoiceFilterHelper(repo)

        val result = helper.isVoiceEnabled("voice1", testLanguage)

        assertTrue(result)
    }

    @Test
    fun isVoiceEnabled_returns_true_for_enabled_voice() = runBlocking {
        val db = DataDbManager(platformDbSupport()).openAppDatabase()
        val repo = SettingsRepository(db)
        val helper = VoiceFilterHelper(repo)

        helper.setEnabledVoices(testLanguage, setOf("voice1", "voice2"))

        assertTrue(helper.isVoiceEnabled("voice1", testLanguage))
        assertTrue(helper.isVoiceEnabled("voice2", testLanguage))
    }

    @Test
    fun isVoiceEnabled_returns_false_for_disabled_voice() = runBlocking {
        val db = DataDbManager(platformDbSupport()).openAppDatabase()
        val repo = SettingsRepository(db)
        val helper = VoiceFilterHelper(repo)

        helper.setEnabledVoices(testLanguage, setOf("voice1"))

        assertFalse(helper.isVoiceEnabled("voice2", testLanguage))
    }

    @Test
    fun initializeDefaultVoices_selects_local_voices_only() = runBlocking {
        val db = DataDbManager(platformDbSupport()).openAppDatabase()
        val repo = SettingsRepository(db)
        val helper = VoiceFilterHelper(repo)

        val result = helper.initializeDefaultVoices(testLanguage, testVoices)

        // Should only include voice1 and voice3 (networkConnectionRequired = false, i.e., local voices)
        assertEquals(setOf("voice1", "voice3"), result)

        // Verify it was saved
        val saved = helper.getEnabledVoices(testLanguage)
        assertEquals(setOf("voice1", "voice3"), saved)
    }

    @Test
    fun filterVoicesByEnabled_returns_all_when_no_filter() = runBlocking {
        val db = DataDbManager(platformDbSupport()).openAppDatabase()
        val repo = SettingsRepository(db)
        val helper = VoiceFilterHelper(repo)

        val result = helper.filterVoicesByEnabled(testVoices, testLanguage)

        assertEquals(testVoices, result)
    }

    @Test
    fun filterVoicesByEnabled_filters_correctly() = runBlocking {
        val db = DataDbManager(platformDbSupport()).openAppDatabase()
        val repo = SettingsRepository(db)
        val helper = VoiceFilterHelper(repo)

        helper.setEnabledVoices(testLanguage, setOf("voice1", "voice3"))

        val result = helper.filterVoicesByEnabled(testVoices, testLanguage)

        assertEquals(2, result.size)
        assertTrue(result.any { it.id == "voice1" })
        assertTrue(result.any { it.id == "voice3" })
        assertFalse(result.any { it.id == "voice2" })
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
    fun isVoiceEnabled_returns_true_when_no_repository() = runBlocking {
        val helper = VoiceFilterHelper(null)

        val result = helper.isVoiceEnabled("voice1", testLanguage)

        assertTrue(result)
    }
}
