package com.slovy.slovymovyapp.server.lists

import kotlinx.serialization.Serializable

@Serializable
internal data class RawListFile(
    val title: String,
    val subtitle: String,
    val labels: List<String> = emptyList(),
    val icon: String? = null,
    val senseIds: List<String> = emptyList(),
)

@Serializable
data class IconPayload(
    val mimeType: String,
    val data: String,
)

@Serializable
data class ListContent(
    val id: String,
    val title: String,
    val subtitle: String,
    val labels: List<String>,
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
