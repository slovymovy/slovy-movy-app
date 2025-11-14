package com.slovy.slovymovyapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.favorites.Favorite
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.remote.*
import com.slovy.slovymovyapp.ui.word.SenseCard
import com.slovy.slovymovyapp.ui.word.SenseUiState
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.ui.tooling.preview.PreviewParameter

data class FavoriteGroupUiState(
    val targetLang: Language,
    val lemma: String,
    val senses: List<FavoriteSenseUiState>?,
    val expanded: Boolean = false
)

data class FavoriteSenseUiState(
    val favorite: Favorite,
    val sense: LanguageCardResponseSense,
    val state: SenseUiState
)

data class FavoritesUiState(
    val groups: List<FavoriteGroupUiState>,
    val query: String = "",
    val showNoResults: Boolean = false,
    val hasAnyFavorites: Boolean = false,
)

class FavoritesViewModel(
    private val favoritesRepository: FavoritesRepository,
    private val dictionaryRepository: DictionaryRepository
) : ViewModel() {

    var state by mutableStateOf(
        FavoritesUiState(
            groups = emptyList()
        )
    )
        private set

    val scrollState = LazyListState()

    init {
        loadFavorites()
    }

    fun updateQuery(newQuery: String) {
        state = state.copy(query = newQuery)
        loadFavorites()
    }

    fun loadFavorites() {
        val allFavorites = favoritesRepository.getAllGroupedByLangAndLemma()
        val hasAnyFavorites = allFavorites.isNotEmpty()

        val trimmedQuery = state.query.trim()
        val favorites = if (trimmedQuery.isEmpty()) {
            allFavorites
        } else {
            favoritesRepository.searchByLemma(trimmedQuery)
        }

        // Group by (targetLang, lemma)
        val grouped = favorites.groupBy { it.targetLang to it.lemma }

        val groups = grouped.map { (langLemma, _) ->
            val (targetLang, lemma) = langLemma

            // Find existing group to preserve state
            val existingGroup = state.groups.find { it.targetLang == targetLang && it.lemma == lemma }

            // Load senses immediately for collapsed view preview
            val senses = if (existingGroup?.senses != null) {
                existingGroup.senses
            } else {
                loadGroupSensesData(targetLang, lemma)
            }

            FavoriteGroupUiState(
                targetLang = targetLang,
                lemma = lemma,
                senses = senses,
                expanded = existingGroup?.expanded ?: false
            )
        }

        state = state.copy(
            groups = groups,
            showNoResults = groups.isEmpty() && trimmedQuery.isNotEmpty(),
            hasAnyFavorites = hasAnyFavorites
        )
    }

    private fun loadGroupSensesData(targetLang: Language, lemma: String): List<FavoriteSenseUiState> {
        val favorites = favoritesRepository.getByLangAndLemma(targetLang, lemma)
        val allFavSenses = favorites.map { it.senseId }

        val card = dictionaryRepository.getLanguageCard(targetLang, lemma)

        return favorites.mapNotNull { favorite ->
            // Find the entry and sense
            val entryAndSense = card?.entries?.firstNotNullOfOrNull { entry ->
                entry.senses.find { it.senseId == favorite.senseId }?.let { sense ->
                    entry to sense
                }
            }

            if (entryAndSense != null) {
                val (entry, sense) = entryAndSense
                // Find existing sense state to preserve
                val existingGroup = state.groups.find { it.targetLang == targetLang && it.lemma == lemma }
                val existingSenseState =
                    existingGroup?.senses?.find { it.sense.senseId == sense.senseId }
                        ?.state?.copy(favorite = allFavSenses.contains(sense.senseId))

                FavoriteSenseUiState(
                    favorite = favorite,
                    sense = sense,
                    state = existingSenseState ?: SenseUiState(
                        senseId = sense.senseId,
                        expanded = false,
                        examplesExpanded = false,
                        languageExpanded = emptyMap(),
                        favorite = true,
                        showFavoriteToggle = false,
                        pos = entry.pos
                    )
                )
            } else {
                null
            }
        }
    }

    private fun updateSenseState(senseId: String, updateFn: (SenseUiState) -> SenseUiState) {
        state = state.copy(
            groups = state.groups.map { group ->
                group.copy(
                    senses = group.senses?.map { favSense ->
                        if (favSense.sense.senseId == senseId) {
                            favSense.copy(state = updateFn(favSense.state))
                        } else {
                            favSense
                        }
                    }
                )
            }
        )
    }

    fun toggleSense(senseId: String) {
        updateSenseState(senseId) { it.copy(expanded = !it.expanded, showFavoriteToggle = !it.showFavoriteToggle) }
    }

    fun toggleFavorite(senseId: String, targetLang: Language, lemma: String) {
        val isFavorite = if (favoritesRepository.exists(senseId, targetLang)) {
            favoritesRepository.remove(senseId, targetLang)
            false
        } else {
            favoritesRepository.add(senseId, targetLang, lemma)
            true
        }
        updateSenseState(senseId) { it.copy(favorite = isFavorite) }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel,
    onNavigateToSearch: () -> Unit = {},
    wordDetailLabel: String? = null,
    onNavigateToLastWordDetail: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val focusManager = LocalFocusManager.current

    // Clear focus when scrolling starts
    LaunchedEffect(viewModel.scrollState.isScrollInProgress) {
        if (viewModel.scrollState.isScrollInProgress) {
            focusManager.clearFocus()
        }
    }

    FavoritesScreenContent(
        state = viewModel.state,
        scrollState = viewModel.scrollState,
        onNavigateToSearch = onNavigateToSearch,
        onQueryChange = { viewModel.updateQuery(it) },
        onSenseToggle = { senseId -> viewModel.toggleSense(senseId) },
        onFavoriteToggle = { senseId, targetLang, lemma -> viewModel.toggleFavorite(senseId, targetLang, lemma) },
        wordDetailLabel = wordDetailLabel,
        onNavigateToLastWordDetail = onNavigateToLastWordDetail,
        onNavigateToSettings = onNavigateToSettings
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreenContent(
    state: FavoritesUiState,
    scrollState: LazyListState = LazyListState(),
    onNavigateToSearch: () -> Unit = {},
    onQueryChange: (String) -> Unit = {},
    onSenseToggle: (String) -> Unit = {},
    onFavoriteToggle: (String, Language, String) -> Unit = { _, _, _ -> },
    wordDetailLabel: String? = null,
    onNavigateToLastWordDetail: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "My words",
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            AppNavigationBar(
                currentScreen = AppScreen.FAVORITES,
                onNavigateToSearch = onNavigateToSearch,
                onNavigateToFavorites = {},
                onNavigateToWordDetail = onNavigateToLastWordDetail,
                wordDetailLabel = wordDetailLabel,
                onNavigateToSettings = onNavigateToSettings
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search field - only show if there are any favorites
            if (state.hasAnyFavorites) {
                com.slovy.slovymovyapp.ui.components.AppSearchBar(
                    query = state.query,
                    onQueryChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 16.dp),
                    placeholder = "Search my word..."
                )

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Content
            when {
                state.groups.isEmpty() && state.query.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(96.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.FavoriteBorder,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No Favorites Yet",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Start adding meanings to your favorites by tapping the heart icon",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }

                state.showNoResults -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No favorites match your search",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Meaning count
                        val meaningCount = state.groups.sumOf { it.senses?.size ?: 0 }
                        Text(
                            text = if (meaningCount == 1) "1 meaning" else "$meaningCount meanings",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                        )

                        LazyColumn(
                            state = scrollState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(state.groups, key = { "${it.targetLang.code}_${it.lemma}" }) { group ->
                                FavoriteGroupCard(
                                    group = group,
                                    onSenseToggle = onSenseToggle,
                                    onFavoriteToggle = onFavoriteToggle
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteGroupCard(
    group: FavoriteGroupUiState,
    onSenseToggle: (String) -> Unit,
    onFavoriteToggle: (String, Language, String) -> Unit
) {

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        group.senses?.forEach { favSense ->
            val sense = favSense.sense
            SenseCard(
                lemma = group.lemma,
                sense = sense,
                state = favSense.state,
                onToggle = { onSenseToggle(sense.senseId) },
                onFavoriteToggle = { onFavoriteToggle(sense.senseId, group.targetLang, group.lemma) })
        }
    }
}

// Preview helpers
private fun createMockSense(
    id: String,
    definition: String,
    level: LearnerLevel = LearnerLevel.B1,
    frequency: SenseFrequency = SenseFrequency.MIDDLE,
    examples: List<LanguageCardExample> = emptyList(),
    synonyms: List<String> = emptyList(),
    antonyms: List<String> = emptyList(),
): LanguageCardResponseSense {
    return LanguageCardResponseSense(
        senseId = id,
        senseDefinition = definition,
        learnerLevel = level,
        frequency = frequency,
        semanticGroupId = "group1",
        nameType = null,
        examples = examples,
        synonyms = synonyms,
        antonyms = antonyms,
        commonPhrases = emptyList(),
        traits = emptyList(),
        targetLangDefinitions = mapOf(Language.ENGLISH to definition),
    )
}

private fun createMockFavorite(
    senseId: String,
    targetLang: Language,
    lemma: String,
    createdAt: Long = 1700000000L
): Favorite {
    return Favorite(
        senseId = senseId,
        targetLang = targetLang,
        lemma = lemma,
        createdAt = createdAt
    )
}

@Preview
@Composable
fun PreviewFavoritesScreenEmpty(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        FavoritesScreenContent(
            state = FavoritesUiState(
                groups = emptyList(),
                hasAnyFavorites = false
            )
        )
    }
}

@Preview
@Composable
fun PreviewFavoritesScreenSingleGroupCollapsed(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val sense1 = createMockSense(
            id = "run-1",
            definition = "to move swiftly on foot",
            level = LearnerLevel.A1,
            frequency = SenseFrequency.HIGH,
            examples = listOf(
                LanguageCardExample("She runs every morning", mapOf(Language.POLISH to "Ona biegnie każdego ranka"))
            ),
            synonyms = listOf("walk", "stride"),
            antonyms = listOf("hitchhike", "stop")
        )

        val state = FavoritesUiState(
            groups = listOf(
                FavoriteGroupUiState(
                    targetLang = Language.ENGLISH,
                    lemma = "run",
                    senses = listOf(
                        FavoriteSenseUiState(
                            favorite = createMockFavorite("run-1", Language.ENGLISH, "run"),
                            sense = sense1,
                            state = SenseUiState(
                                senseId = "run-1",
                                expanded = false,
                                examplesExpanded = false,
                                languageExpanded = emptyMap(),
                                favorite = true,
                                pos = PartOfSpeech.VERB,
                                showFavoriteToggle = false
                            )
                        )
                    ),
                    expanded = false
                )
            ),
            hasAnyFavorites = true
        )

        FavoritesScreenContent(state = state)
    }
}

@Preview
@Composable
fun PreviewFavoritesScreenMultipleGroupsCollapsed(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val runSense1 = createMockSense(
            id = "run-1",
            definition = "to move swiftly on foot",
            level = LearnerLevel.A1,
            frequency = SenseFrequency.HIGH
        )

        val runSense2 = createMockSense(
            id = "run-2",
            definition = "to operate or control",
            level = LearnerLevel.B1,
            frequency = SenseFrequency.MIDDLE
        )

        val bookSense1 = createMockSense(
            id = "book-1",
            definition = "a written or printed work",
            level = LearnerLevel.A1,
            frequency = SenseFrequency.HIGH
        )

        val state = FavoritesUiState(
            groups = listOf(
                FavoriteGroupUiState(
                    targetLang = Language.ENGLISH,
                    lemma = "run",
                    senses = listOf(
                        FavoriteSenseUiState(
                            favorite = createMockFavorite("run-1", Language.ENGLISH, "run", 1000000),
                            sense = runSense1,
                            state = SenseUiState(
                                senseId = "run-1",
                                expanded = false,
                                examplesExpanded = false,
                                languageExpanded = emptyMap(),
                                favorite = true,
                                pos = PartOfSpeech.VERB,
                                showFavoriteToggle = false
                            )
                        ),
                        FavoriteSenseUiState(
                            favorite = createMockFavorite("run-2", Language.ENGLISH, "run", 900000),
                            sense = runSense2,
                            state = SenseUiState(
                                senseId = "run-2",
                                expanded = false,
                                examplesExpanded = false,
                                languageExpanded = emptyMap(),
                                favorite = true,
                                pos = PartOfSpeech.VERB,
                                showFavoriteToggle = false
                            )
                        )
                    ),
                    expanded = false
                ),
                FavoriteGroupUiState(
                    targetLang = Language.ENGLISH,
                    lemma = "book",
                    senses = listOf(
                        FavoriteSenseUiState(
                            favorite = createMockFavorite("book-1", Language.ENGLISH, "book", 800000),
                            sense = bookSense1,
                            state = SenseUiState(
                                senseId = "book-1",
                                expanded = false,
                                examplesExpanded = false,
                                languageExpanded = emptyMap(),
                                favorite = false,
                                pos = PartOfSpeech.NOUN,
                                showFavoriteToggle = false
                            )
                        )
                    ),
                    expanded = false,
                )
            ),
            hasAnyFavorites = true
        )

        FavoritesScreenContent(state = state)
    }
}

@Preview
@Composable
fun PreviewFavoritesScreenGroupExpanded(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val sense1 = createMockSense(
            id = "happy-1",
            definition = "feeling or showing pleasure or contentment",
            level = LearnerLevel.A2,
            frequency = SenseFrequency.HIGH,
            examples = listOf(
                LanguageCardExample("I'm so happy today!", mapOf(Language.POLISH to "Jestem dzisiaj taki szczęśliwy!")),
                LanguageCardExample("She looks happy", mapOf(Language.POLISH to "Ona wygląda na szczęśliwą"))
            )
        )

        val state = FavoritesUiState(
            groups = listOf(
                FavoriteGroupUiState(
                    targetLang = Language.ENGLISH,
                    lemma = "happy",
                    senses = listOf(
                        FavoriteSenseUiState(
                            favorite = createMockFavorite("happy-1", Language.ENGLISH, "happy"),
                            sense = sense1,
                            state = SenseUiState(
                                senseId = "happy-1",
                                expanded = true,
                                examplesExpanded = true,
                                languageExpanded = mapOf(Language.POLISH to true),
                                favorite = true,
                                pos = PartOfSpeech.ADJECTIVE,
                                showFavoriteToggle = true
                            )
                        )
                    ),
                    expanded = true
                )
            ),
            hasAnyFavorites = true
        )

        FavoritesScreenContent(state = state)
    }
}

@Preview
@Composable
fun PreviewFavoritesScreenMixedStates(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val sense1 = createMockSense(
            id = "love-1",
            definition = "an intense feeling of deep affection",
            level = LearnerLevel.A2,
            frequency = SenseFrequency.HIGH
        )

        val sense2 = createMockSense(
            id = "love-2",
            definition = "to feel deep affection for someone",
            level = LearnerLevel.A2,
            frequency = SenseFrequency.HIGH,
            examples = listOf(LanguageCardExample("I love you", mapOf(Language.POLISH to "Kocham cię")))
        )

        val runSense = createMockSense(
            id = "run-1",
            definition = "to move swiftly on foot",
            level = LearnerLevel.A1,
            frequency = SenseFrequency.HIGH
        )

        val state = FavoritesUiState(
            groups = listOf(
                FavoriteGroupUiState(
                    targetLang = Language.ENGLISH,
                    lemma = "love",
                    senses = listOf(
                        FavoriteSenseUiState(
                            favorite = createMockFavorite("love-1", Language.ENGLISH, "love", 2000000),
                            sense = sense1,
                            state = SenseUiState(
                                senseId = "love-1",
                                expanded = true,
                                examplesExpanded = false,
                                languageExpanded = emptyMap(),
                                favorite = true,
                                pos = PartOfSpeech.NOUN,
                                showFavoriteToggle = true
                            )
                        ),
                        FavoriteSenseUiState(
                            favorite = createMockFavorite("love-2", Language.ENGLISH, "love", 1000000),
                            sense = sense2,
                            state = SenseUiState(
                                senseId = "love-2",
                                expanded = false,
                                examplesExpanded = false,
                                languageExpanded = emptyMap(),
                                favorite = false,
                                pos = PartOfSpeech.VERB,
                                showFavoriteToggle = false
                            )
                        )
                    ),
                    expanded = true
                ),
                FavoriteGroupUiState(
                    targetLang = Language.ENGLISH,
                    lemma = "run",
                    senses = listOf(
                        FavoriteSenseUiState(
                            favorite = createMockFavorite("run-1", Language.ENGLISH, "run", 1500000),
                            sense = runSense,
                            state = SenseUiState(
                                senseId = "run-1",
                                expanded = false,
                                examplesExpanded = false,
                                languageExpanded = emptyMap(),
                                favorite = true,
                                pos = PartOfSpeech.VERB,
                                showFavoriteToggle = false
                            )
                        )
                    ),
                    expanded = false
                )
            ),
            hasAnyFavorites = true
        )

        FavoritesScreenContent(state = state)
    }
}

@Preview
@Composable
fun PreviewFavoritesScreenWithSearch(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val runSense = createMockSense(
            id = "run-1",
            definition = "to move swiftly on foot",
            level = LearnerLevel.A1,
            frequency = SenseFrequency.HIGH
        )

        val state = FavoritesUiState(
            groups = listOf(
                FavoriteGroupUiState(
                    targetLang = Language.ENGLISH,
                    lemma = "run",
                    senses = listOf(
                        FavoriteSenseUiState(
                            favorite = createMockFavorite("run-1", Language.ENGLISH, "run"),
                            sense = runSense,
                            state = SenseUiState(
                                senseId = "run-1",
                                expanded = false,
                                examplesExpanded = false,
                                languageExpanded = emptyMap(),
                                favorite = true,
                                pos = PartOfSpeech.VERB,
                                showFavoriteToggle = false
                            )
                        )
                    ),
                    expanded = false
                )
            ),
            query = "run",
            hasAnyFavorites = true
        )

        FavoritesScreenContent(state = state)
    }
}

@Preview
@Composable
fun PreviewFavoritesScreenNoResults(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val state = FavoritesUiState(
            groups = emptyList(),
            query = "xyz",
            showNoResults = true,
            hasAnyFavorites = true
        )

        FavoritesScreenContent(state = state)
    }
}

@Preview
@Composable
fun PreviewFavoritesScreenSearchWithMultipleResults(
    @PreviewParameter(ThemePreviewProvider::class) isDark: Boolean
) {
    ThemedPreview(darkTheme = isDark) {
        val bookSense1 = createMockSense(
            id = "book-1",
            definition = "a written or printed work",
            level = LearnerLevel.A1,
            frequency = SenseFrequency.HIGH
        )

        val bookmarkSense1 = createMockSense(
            id = "bookmark-1",
            definition = "a strip of material used to mark one's place in a book",
            level = LearnerLevel.B1,
            frequency = SenseFrequency.MIDDLE
        )

        val state = FavoritesUiState(
            groups = listOf(
                FavoriteGroupUiState(
                    targetLang = Language.ENGLISH,
                    lemma = "book",
                    senses = listOf(
                        FavoriteSenseUiState(
                            favorite = createMockFavorite("book-1", Language.ENGLISH, "book"),
                            sense = bookSense1,
                            state = SenseUiState(
                                senseId = "book-1",
                                expanded = false,
                                examplesExpanded = false,
                                languageExpanded = emptyMap(),
                                favorite = true,
                                pos = PartOfSpeech.NOUN,
                                showFavoriteToggle = false
                            )
                        )
                    ),
                    expanded = false
                ),
                FavoriteGroupUiState(
                    targetLang = Language.RUSSIAN,
                    lemma = "книга",
                    senses = listOf(
                        FavoriteSenseUiState(
                            favorite = createMockFavorite("bookmark-1", Language.ENGLISH, "bookmark"),
                            sense = bookmarkSense1,
                            state = SenseUiState(
                                senseId = "bookmark-1",
                                expanded = false,
                                examplesExpanded = false,
                                languageExpanded = emptyMap(),
                                favorite = true,
                                pos = PartOfSpeech.NOUN
                            )
                        )
                    ),
                    expanded = false
                )
            ),
            query = "book",
            hasAnyFavorites = true
        )

        FavoritesScreenContent(state = state)
    }
}
