package com.slovy.slovymovyapp.ui.reader

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.remote.DictionaryRepository
import com.slovy.slovymovyapp.data.settings.SettingsRepository
import com.slovy.slovymovyapp.test.BaseTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TextReaderViewModelTest : BaseTest() {

    @BeforeTest
    fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): TextReaderViewModel {
        val appDb = testAppDatabaseHolder().database
        val favorites = FavoritesRepository(appDb)
        val repository = DictionaryRepository(testDataDbManager(), testLocalDbManager(), favorites, SettingsRepository(appDb))
        return TextReaderViewModel(repository, favorites, Language.DUTCH, initialText = null)
    }

    @Test
    fun returning_to_the_paste_card_with_text_asks_for_a_paste() {
        val vm = viewModel()
        assertFalse(vm.updateClipboardHasText(false), "an empty clipboard must not trigger a paste")
        assertFalse(vm.state.clipboardHasText, "an empty clipboard must be shown as empty")

        assertTrue(vm.updateClipboardHasText(true), "text on the clipboard must be pasted onto the paste card")
        assertTrue(vm.state.clipboardHasText, "a filled clipboard must be shown as filled")
    }

    @Test
    fun a_blank_paste_marks_the_clipboard_empty() {
        val vm = viewModel()
        vm.pasteText("   ")
        assertFalse(vm.state.clipboardHasText, "a paste that read nothing must disable the paste card")
    }
}
