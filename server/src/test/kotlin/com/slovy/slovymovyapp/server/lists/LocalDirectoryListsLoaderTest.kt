package com.slovy.slovymovyapp.server.lists

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class LocalDirectoryListsLoaderTest {

    private val listJson =
        """{"title":{"en":"Animals"},"subtitle":{"en":"Some words"},"senses":[{"senseId":"s-1","lemma":"hond"}]}"""
    private val legacyListJson = """{"title":{"en":"Animals"},"subtitle":{"en":"Some words"},"senseIds":["s-1"]}"""
    private val iconSvg = """<svg viewBox="0 0 24 24"><path d="M4 4h16v16H4z"/></svg>"""

    private fun withListsDir(block: (root: Path, lang: Path) -> Unit) {
        val root = Files.createTempDirectory("lists-loader-test")
        try {
            val lang = Files.createDirectory(root.resolve("nl"))
            block(root, lang)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun load_pairsSvgIconByFileName() {
        withListsDir { root, lang ->
            lang.resolve("dieren.json").writeText(listJson)
            lang.resolve("dieren.svg").writeText(iconSvg)

            val bundle = LocalDirectoryListsLoader(root).load("nl")
                ?: error("bundle must load for an existing language folder")
            assertEquals(1, bundle.lists.size, "one list file must yield one list")
            assertEquals(iconSvg, bundle.lists.single().iconSvg, "the {id}.svg file must be auto-paired as SVG text")
            assertEquals(
                listOf(ListSenseEntry(senseId = "s-1", lemma = "hond")),
                bundle.lists.single().senses,
                "senses must carry the authored lemma",
            )
        }
    }

    @Test
    fun load_acceptsLegacySenseIdsShape_withEmptyLemma() {
        withListsDir { root, lang ->
            lang.resolve("dieren.json").writeText(legacyListJson)

            val bundle = LocalDirectoryListsLoader(root).load("nl")
                ?: error("bundle must load for an existing language folder")
            assertEquals(
                listOf(ListSenseEntry(senseId = "s-1", lemma = "")),
                bundle.lists.single().senses,
                "legacy senseIds must normalize to senses with an empty lemma",
            )
        }
    }

    @Test
    fun load_ignoresNonSvgImages() {
        withListsDir { root, lang ->
            lang.resolve("dieren.json").writeText(listJson)
            lang.resolve("dieren.png").writeBytes(byteArrayOf(0x42, 0x42, 0x42))

            val bundle = LocalDirectoryListsLoader(root).load("nl")
                ?: error("bundle must load for an existing language folder")
            assertNull(bundle.lists.single().iconSvg, "icons are SVG-only; a png must not be paired")
        }
    }

    @Test
    fun version_changesWhenOnlyTheIconChanges() {
        withListsDir { root, lang ->
            lang.resolve("dieren.json").writeText(listJson)
            lang.resolve("dieren.svg").writeText(iconSvg)
            val loader = LocalDirectoryListsLoader(root)
            val before = loader.load("nl") ?: error("bundle must load before the icon edit")

            lang.resolve("dieren.svg").writeText("""<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/></svg>""")
            val after = loader.load("nl") ?: error("bundle must load after the icon edit")

            assertNotEquals(
                before.version,
                after.version,
                "an icon-only edit must change the bundle version so clients re-sync",
            )
        }
    }

    @Test
    fun load_returnsNull_whenLanguageFolderMissing() {
        withListsDir { root, _ ->
            assertNull(LocalDirectoryListsLoader(root).load("tr"), "missing language folder must yield no bundle")
        }
    }
}
