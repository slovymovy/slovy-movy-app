package com.slovy.slovymovyapp.server.lists

import com.slovy.slovymovyapp.server.github.GitHubClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.kohsuke.github.GHContent
import org.slf4j.LoggerFactory
import java.util.Base64

private val log = LoggerFactory.getLogger("LanguageListsLoader")

private val json = Json { ignoreUnknownKeys = true }

/** UI locale every list should provide; clients fall back to it for unknown locales. */
private const val BASE_LOCALE = "en"

private val ICON_EXTENSIONS = mapOf(
    "svg" to "image/svg+xml",
    "png" to "image/png",
    "webp" to "image/webp",
)

class LanguageListsLoader {

    /**
     * Fetches just the tree SHA of `lists/{lang}/` without downloading any list
     * JSON or icon bytes. Used by the version endpoint so a cold cache or a
     * malformed list file doesn't make a version check expensive or fail.
     */
    suspend fun loadVersion(lang: String): String = withContext(Dispatchers.IO) {
        GitHubClient.loadLanguageListsTreeSha(lang)
    }

    suspend fun load(lang: String): LanguageListsBundle = withContext(Dispatchers.IO) {
        val treeSha = GitHubClient.loadLanguageListsTreeSha(lang)
        val entries = GitHubClient.listLanguageListsFolder(lang)

        val files = entries.filter { it.type == "file" }
        val listFiles = files.filter { it.name.endsWith(".json", ignoreCase = true) }
        val iconFiles = files.filter { ICON_EXTENSIONS.containsKey(it.name.substringAfterLast('.', "").lowercase()) }
        val unknown = files - listFiles.toSet() - iconFiles.toSet()
        if (unknown.isNotEmpty()) {
            log.warn("Ignoring {} unsupported entries in lists/{}/: {}", unknown.size, lang, unknown.map { it.name })
        }

        val iconsByName: Map<String, IconPayload> = coroutineScope {
            iconFiles.map { iconFile ->
                async {
                    val ext = iconFile.name.substringAfterLast('.').lowercase()
                    val mimeType = ICON_EXTENSIONS.getValue(ext)
                    val bytes = GitHubClient.readBytes(iconFile)
                    iconFile.name to IconPayload(
                        mimeType = mimeType,
                        data = Base64.getEncoder().encodeToString(bytes),
                    )
                }
            }.awaitAll().toMap()
        }

        val lists: List<ListContent> = coroutineScope {
            listFiles.map { listFile ->
                async { decodeList(lang, listFile, iconsByName) }
            }.awaitAll().sortedWith(compareBy({ it.order ?: Int.MAX_VALUE }, { it.id }))
        }

        LanguageListsBundle(version = treeSha, lists = lists)
    }

    private fun decodeList(
        lang: String,
        listFile: GHContent,
        iconsByName: Map<String, IconPayload>,
    ): ListContent {
        val text = GitHubClient.readText(listFile)
        val raw = json.decodeFromString(RawListFile.serializer(), text)
        val id = listFile.name.removeSuffix(".json")
        val iconPayload = ICON_EXTENSIONS.keys.firstNotNullOfOrNull { ext -> iconsByName["$id.$ext"] }
            ?: raw.icon?.let { iconsByName[it] }
        if (raw.icon != null && iconPayload == null) {
            log.warn("List '{}' in lists/{}/ references icon '{}' but no matching file was found", id, lang, raw.icon)
        }
        if (!raw.title.containsKey(BASE_LOCALE) || !raw.subtitle.containsKey(BASE_LOCALE)) {
            log.warn(
                "List '{}' in lists/{}/ is missing the '{}' base locale for title/subtitle; clients fall back to it",
                id, lang, BASE_LOCALE,
            )
        }
        return ListContent(
            id = id,
            title = raw.title,
            subtitle = raw.subtitle,
            labels = raw.labels,
            icon = iconPayload,
            senseIds = raw.senseIds,
            order = raw.order,
        )
    }
}
