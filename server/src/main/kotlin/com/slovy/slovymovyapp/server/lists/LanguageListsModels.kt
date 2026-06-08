package com.slovy.slovymovyapp.server.lists

import kotlinx.serialization.Serializable

/**
 * On-disk shape. [title], [subtitle], and [labels] are keyed by UI locale code
 * (e.g. "en", "ru") so the same list renders in the user's interface language,
 * independently of the studied language in the folder path. The client resolves
 * the active locale with an "en" fallback, so every list should provide "en".
 */
@Serializable
internal data class RawListFile(
    val title: Map<String, String>,
    val subtitle: Map<String, String>,
    val labels: Map<String, List<String>> = emptyMap(),
    val icon: String? = null,
    val senseIds: List<String> = emptyList(),
)

@Serializable
data class IconPayload(
    val mimeType: String,
    val data: String,
)

/**
 * Wire shape. [title], [subtitle], and [labels] carry every available UI locale;
 * resolution is left to the client (it picks the active UI locale with an "en"
 * fallback) so the server cache and version SHA stay locale-independent.
 */
@Serializable
data class ListContent(
    val id: String,
    val title: Map<String, String>,
    val subtitle: Map<String, String>,
    val labels: Map<String, List<String>>,
    val icon: IconPayload?,
    val senseIds: List<String>,
)

data class LanguageListsBundle(
    val version: String,
    val lists: List<ListContent>,
)

@Serializable
data class LanguageListsResponse(
    val version: String,
    val lists: List<ListContent>,
)

@Serializable
data class ListsVersionResponse(
    val version: String,
)
