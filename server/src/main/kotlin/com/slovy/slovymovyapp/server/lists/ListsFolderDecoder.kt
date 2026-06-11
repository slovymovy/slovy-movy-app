package com.slovy.slovymovyapp.server.lists

import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Folder-layout rules shared by [LanguageListsLoader] (GitHub `lists/{lang}/`) and
 * [LocalDirectoryListsLoader] (test-mode local directory): list JSON decoding, icon
 * auto-pairing by file name, base-locale validation, and the feed sort order.
 * Icons are SVG-only; other image types in the folder are ignored.
 */
internal object ListsFolderDecoder {

    private val log = LoggerFactory.getLogger("ListsFolderDecoder")

    private val json = Json { ignoreUnknownKeys = true }

    /** UI locale every list should provide; clients fall back to it for unknown locales. */
    private const val BASE_LOCALE = "en"

    /** Ascending curator [ListContent.order] with absent last, id as the tiebreaker. */
    val feedOrder: Comparator<ListContent> = compareBy({ it.order ?: Int.MAX_VALUE }, { it.id })

    /** True for files served as list icons. Icons are SVG-only. */
    fun isIconFile(fileName: String): Boolean = fileName.endsWith(".svg", ignoreCase = true)

    /**
     * Decodes one list file. The icon is auto-paired by name (`{id}.svg`), with the explicit
     * `icon` file reference from the JSON as a fallback. [iconsByName] maps icon file names
     * to their SVG text. [folder] only labels log messages.
     */
    fun decodeList(
        folder: String,
        id: String,
        text: String,
        iconsByName: Map<String, String>,
    ): ListContent {
        val raw = json.decodeFromString(RawListFile.serializer(), text)
        val iconSvg = iconsByName["$id.svg"] ?: raw.icon?.let { iconsByName[it] }
        if (raw.icon != null && iconSvg == null) {
            log.warn("List '{}' in {} references icon '{}' but no matching file was found", id, folder, raw.icon)
        }
        if (!raw.title.containsKey(BASE_LOCALE) || !raw.subtitle.containsKey(BASE_LOCALE)) {
            log.warn(
                "List '{}' in {} is missing the '{}' base locale for title/subtitle; clients fall back to it",
                id, folder, BASE_LOCALE,
            )
        }
        return ListContent(
            id = id,
            title = raw.title,
            subtitle = raw.subtitle,
            labels = raw.labels,
            iconSvg = iconSvg,
            senseIds = raw.senseIds,
            order = raw.order,
        )
    }
}
