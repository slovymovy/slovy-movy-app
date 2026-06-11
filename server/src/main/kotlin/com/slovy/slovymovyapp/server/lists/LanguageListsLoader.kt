package com.slovy.slovymovyapp.server.lists

import com.slovy.slovymovyapp.server.github.GitHubClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.Base64

private val log = LoggerFactory.getLogger("LanguageListsLoader")

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
        val folder = "lists/$lang"
        val treeSha = GitHubClient.loadLanguageListsTreeSha(lang)
        val entries = GitHubClient.listLanguageListsFolder(lang)

        val files = entries.filter { it.type == "file" }
        val listFiles = files.filter { it.name.endsWith(".json", ignoreCase = true) }
        val iconFiles = files.filter { ListsFolderDecoder.iconMimeType(it.name) != null }
        val unknown = files - listFiles.toSet() - iconFiles.toSet()
        if (unknown.isNotEmpty()) {
            log.warn("Ignoring {} unsupported entries in {}: {}", unknown.size, folder, unknown.map { it.name })
        }

        val iconsByName: Map<String, IconPayload> = coroutineScope {
            iconFiles.map { iconFile ->
                async {
                    val mimeType = ListsFolderDecoder.iconMimeType(iconFile.name)!!
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
                async {
                    ListsFolderDecoder.decodeList(
                        folder = folder,
                        id = listFile.name.removeSuffix(".json"),
                        text = GitHubClient.readText(listFile),
                        iconsByName = iconsByName,
                    )
                }
            }.awaitAll().sortedWith(ListsFolderDecoder.feedOrder)
        }

        LanguageListsBundle(version = treeSha, lists = lists)
    }
}
