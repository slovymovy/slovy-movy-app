package com.slovy.slovymovyapp.server.lists

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Base64
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.readText

private val log = LoggerFactory.getLogger("LocalDirectoryListsLoader")

/**
 * Loads curated word lists from a local directory laid out exactly like the GitHub
 * `lists/{lang}/` folder: `{id}.json` plus optional `{id}.{svg|png|webp}` icons. The
 * test-mode server uses it to serve the committed `.test-lists-files/` content,
 * mirroring how `TEST_DB_DIR` backs the `/test/db` endpoints. The bundle version is a
 * content hash over the folder, so file edits invalidate client caches the same way a
 * new tree SHA does in production.
 */
class LocalDirectoryListsLoader(private val rootDir: Path) {

    /** Returns null when the language folder is missing or holds no list JSON files. */
    fun load(lang: String): LanguageListsBundle? {
        val dir = rootDir.resolve(lang)
        if (!Files.isDirectory(dir)) return null

        val files = Files.list(dir)
            .use { stream -> stream.filter { it.isRegularFile() }.toList() }
            .sortedBy { it.name }
        val listFiles = files.filter { it.name.endsWith(".json", ignoreCase = true) }
        if (listFiles.isEmpty()) return null
        val iconFiles = files.filter { ListsFolderDecoder.iconMimeType(it.name) != null }
        val unknown = files - listFiles.toSet() - iconFiles.toSet()
        if (unknown.isNotEmpty()) {
            log.warn("Ignoring {} unsupported entries in {}: {}", unknown.size, dir, unknown.map { it.name })
        }

        val iconsByName = iconFiles.associate { iconFile ->
            iconFile.name to IconPayload(
                mimeType = ListsFolderDecoder.iconMimeType(iconFile.name)!!,
                data = Base64.getEncoder().encodeToString(iconFile.readBytes()),
            )
        }

        val digest = MessageDigest.getInstance("SHA-256")
        files.forEach { digest.update(it.readBytes()) }
        val version = digest.digest().joinToString("") { "%02x".format(it) }.take(16)

        val lists = listFiles
            .map { listFile ->
                ListsFolderDecoder.decodeList(
                    folder = dir.toString(),
                    id = listFile.name.removeSuffix(".json"),
                    text = listFile.readText(),
                    iconsByName = iconsByName,
                )
            }
            .sortedWith(ListsFolderDecoder.feedOrder)
        return LanguageListsBundle(version = version, lists = lists)
    }
}
