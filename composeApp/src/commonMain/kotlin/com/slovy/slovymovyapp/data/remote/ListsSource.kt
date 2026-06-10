package com.slovy.slovymovyapp.data.remote

import com.slovy.slovymovyapp.data.Language

/**
 * Source of curated word-list bundles for [com.slovy.slovymovyapp.data.lists.ListsService].
 * Production uses [ListsClient] (HTTP). Debug builds can swap in
 * [ResourceListsSource], which serves bundles generated from the project-root `lists/`
 * folder and bundled into compose resources.
 */
interface ListsSource {
    suspend fun getListsVersion(language: Language): String?
    suspend fun getLists(language: Language): RemoteListsResponse?
}
