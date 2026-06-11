package com.slovy.slovymovyapp.server.lists

import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Folder-layout rules shared by [LanguageListsLoader] (GitHub `lists/{lang}/`) and
 * [LocalDirectoryListsLoader] (test-mode local directory): list JSON decoding, icon
 * auto-pairing by file name, base-locale validation, and the feed sort order.
 */
internal object ListsFolderDecoder {

    private val log = LoggerFactory.getLogger("ListsFolderDecoder")

    private val json = Json { ignoreUnknownKeys = true }

    /** UI locale every list should provide; clients fall back to it for unknown locales. */
    private const val BASE_LOCALE = "en"

    private val ICON_EXTENSIONS = mapOf(
        "svg" to "image/svg+xml",
        "png" to "image/png",
        "webp" to "image/webp",
    )

    /** Ascending curator [ListContent.order] with absent last, id as the tiebreaker. */
    val feedOrder: Comparator<ListContent> = compareBy({ it.order ?: Int.MAX_VALUE }, { it.id })

    /** Icon mime type for a file name, or null when the extension is not a supported icon format. */
    fun iconMimeType(fileName: String): String? =
        ICON_EXTENSIONS[fileName.substringAfterLast('.', "").lowercase()]

    /**
     * Decodes one list file. The icon is auto-paired by name (`{id}.{svg|png|webp}`), with the
     * explicit `icon` reference from the JSON as a fallback. [folder] only labels log messages.
     */
    fun decodeList(
        folder: String,
        id: String,
        text: String,
        iconsByName: Map<String, IconPayload>,
    ): ListContent {
        val raw = json.decodeFromString(RawListFile.serializer(), text)
        val iconPayload = ICON_EXTENSIONS.keys.firstNotNullOfOrNull { ext -> iconsByName["$id.$ext"] }
            ?: raw.icon?.let { iconsByName[it] }
        if (raw.icon != null && iconPayload == null) {
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
            icon = iconPayload,
            senseIds = raw.senseIds,
            order = raw.order,
        )
    }
}
