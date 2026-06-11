package com.slovy.slovymovyapp.server.lists

import com.slovy.slovymovyapp.server.github.GitHubClient
import kotlinx.coroutines.runBlocking
import org.kohsuke.github.GHFileNotFoundException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for [LanguageListsLoader]. Requires a valid GitHub token.
 * Tests are skipped if the token is not available or if `lists/en/` is not yet
 * present on the source repository.
 */
class LanguageListsLoaderTest {

    @Test
    fun load_unknownLanguage_throwsNotFound() = runBlocking {
        if (!GitHubClient.isAvailable()) return@runBlocking
        val loader = LanguageListsLoader()
        assertFailsWith<GHFileNotFoundException> { loader.load("zz-nonexistent") }
        Unit
    }

    @Test
    fun load_returnsBundleWithShaVersion() = runBlocking {
        if (!GitHubClient.isAvailable()) return@runBlocking
        val loader = LanguageListsLoader()
        val bundle = try {
            loader.load("en")
        } catch (_: GHFileNotFoundException) {
            return@runBlocking
        }
        assertEquals(40, bundle.version.length, "Version must be a 40-char tree SHA")
        assertTrue(
            bundle.version.all { it.isDigit() || it in 'a'..'f' },
            "Version must be lowercase hex: ${bundle.version}"
        )
        bundle.lists.forEach { list ->
            assertTrue(list.id.isNotBlank(), "List id must not be blank")
            val enTitle = list.title["en"]
            assertNotNull(enTitle, "List '${list.id}' must provide an 'en' title")
            assertTrue(enTitle.isNotBlank(), "List '${list.id}' 'en' title must not be blank")
            list.iconSvg?.let { iconSvg ->
                assertTrue(iconSvg.contains("<svg"), "Icon for '${list.id}' must be SVG text")
            }
        }
    }
}
