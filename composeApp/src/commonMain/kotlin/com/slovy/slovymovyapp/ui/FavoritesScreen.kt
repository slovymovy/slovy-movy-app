package com.slovy.slovymovyapp.ui

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
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
    val hasAnyFavorites: Boolean = false
)

class FavoritesViewModel(
    private val favoritesRepository: FavoritesRepository,
    private val dictionaryRepository: DictionaryRepository
) : ViewModel() {

    var state by mutableStateOf(FavoritesUiState(groups = emptyList()))
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

            FavoriteGroupUiState(
                targetLang = targetLang,
                lemma = lemma,
                senses = existingGroup?.senses, // Preserve existing senses or empty
                expanded = existingGroup?.expanded ?: false
            )
        }

        state = state.copy(
            groups = groups,
            showNoResults = groups.isEmpty() && trimmedQuery.isNotEmpty(),
            hasAnyFavorites = hasAnyFavorites
        )
    }

    private fun loadGroupSenses(targetLang: Language, lemma: String) {
        val favorites = favoritesRepository.getByLangAndLemma(targetLang, lemma)
        val allFavSenses = favorites.map { it.senseId }

        val card = dictionaryRepository.getLanguageCard(targetLang, lemma)

        val senses = favorites.mapNotNull { favorite ->
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
                        showNavigationArrow = true,
                        showFavoriteToggle = false,
                        pos = entry.pos
                    )
                )
            } else {
                null
            }
        }

        // Update the specific group with loaded senses
        updateGroupState(targetLang, lemma) { it.copy(senses = senses) }
    }

    private fun updateGroupState(
        targetLang: Language,
        lemma: String,
        updateFn: (FavoriteGroupUiState) -> FavoriteGroupUiState
    ) {
        state = state.copy(
            groups = state.groups.map { group ->
                if (group.targetLang == targetLang && group.lemma == lemma) {
                    updateFn(group)
                } else {
                    group
                }
            }
        )
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

    fun toggleSenseExamples(senseId: String) {
        updateSenseState(senseId) { it.copy(examplesExpanded = !it.examplesExpanded) }
    }

    fun toggleLanguage(senseId: String, language: Language) {
        updateSenseState(senseId) { currentState ->
            val current = currentState.languageExpanded[language] ?: currentState.expanded
            currentState.copy(
                languageExpanded = currentState.languageExpanded + (language to !current)
            )
        }
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

    fun toggleGroup(targetLang: Language, lemma: String) {
        val group = state.groups.find { it.targetLang == targetLang && it.lemma == lemma }
        if (group != null) {
            val newExpanded = !group.expanded

            // Update expanded state first
            updateGroupState(targetLang, lemma) { it.copy(expanded = newExpanded) }

            // Load senses only if expanding and not already loaded
            if (newExpanded && group.senses == null) {
                loadGroupSenses(targetLang, lemma)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToWordDetail: (Language, String, String) -> Unit = { _, _, _ -> },
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
        onSenseExamplesToggle = { senseId -> viewModel.toggleSenseExamples(senseId) },
        onLanguageToggle = { senseId, lang -> viewModel.toggleLanguage(senseId, lang) },
        onFavoriteToggle = { senseId, targetLang, lemma -> viewModel.toggleFavorite(senseId, targetLang, lemma) },
        onGroupToggle = { targetLang, lemma -> viewModel.toggleGroup(targetLang, lemma) },
        wordDetailLabel = wordDetailLabel,
        onNavigateToLastWordDetail = onNavigateToLastWordDetail,
        onNavigateToWordDetail = onNavigateToWordDetail,
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
    onSenseExamplesToggle: (String) -> Unit = {},
    onLanguageToggle: (String, Language) -> Unit = { _, _ -> },
    onFavoriteToggle: (String, Language, String) -> Unit = { _, _, _ -> },
    onGroupToggle: (Language, String) -> Unit = { _, _ -> },
    wordDetailLabel: String? = null,
    onNavigateToLastWordDetail: () -> Unit = {},
    onNavigateToWordDetail: (Language, String, String) -> Unit = { _, _, _ -> },
    onNavigateToSettings: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Favorites",
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
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 16.dp),
                    label = { Text("Search in favorites") },
                    placeholder = { Text("Type to search...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            TextButton(onClick = { onQueryChange("") }) {
                                Text("✕")
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Content
            when {
                state.groups.isEmpty() && state.query.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🤍🤍🤍\nNo favorites yet.\nAdd favorites by tapping the heart icon on any word sense.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                state.showNoResults -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        Spacer(modifier = Modifier.height(48.dp))
                        Icon(
                            imageVector = Icons.Outlined.FavoriteBorder,
                            contentDescription = "No results",
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No favorites found",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "We couldn't find any favorites matching \"${state.query}\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Try a different search term",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp)
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        state = scrollState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        contentPadding = PaddingValues(vertical = 24.dp)
                    ) {
                        items(state.groups, key = { "${it.targetLang.code}_${it.lemma}" }) { group ->
                            FavoriteGroupCard(
                                group = group,
                                onSenseToggle = onSenseToggle,
                                onSenseExamplesToggle = onSenseExamplesToggle,
                                onLanguageToggle = onLanguageToggle,
                                onFavoriteToggle = onFavoriteToggle,
                                onGroupToggle = { onGroupToggle(group.targetLang, group.lemma) },
                                onNavigateToWordDetail = onNavigateToWordDetail
                            )
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
    onSenseExamplesToggle: (String) -> Unit,
    onLanguageToggle: (String, Language) -> Unit,
    onFavoriteToggle: (String, Language, String) -> Unit,
    onGroupToggle: () -> Unit = {},
    onNavigateToWordDetail: (Language, String, String) -> Unit = { _, _, _ -> }
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with language and lemma
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onGroupToggle() },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = group.lemma,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Icon(
                        imageVector = if (group.expanded) com.slovy.slovymovyapp.ui.word.ExpandLessVector else com.slovy.slovymovyapp.ui.word.ExpandMoreVector,
                        contentDescription = if (group.expanded) "Collapse group" else "Expand group",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                SuggestionChip(
                    onClick = { },
                    label = {
                        Text(
                            text = group.targetLang.code.uppercase(),
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        // Senses
        AnimatedVisibility(
            visible = group.expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                val allSenses = group.senses?.map { it.sense }?.toList()
                group.senses?.forEach { favSense ->
                    val sense = favSense.sense
                    SenseCard(
                        sense = sense,
                        state = favSense.state,
                        onToggle = { onSenseToggle(sense.senseId) },
                        onExamplesToggle = { onSenseExamplesToggle(sense.senseId) },
                        onLanguageToggle = { lang -> onLanguageToggle(sense.senseId, lang) },
                        allSenses = allSenses ?: emptyList(),
                        onFavoriteToggle = { onFavoriteToggle(sense.senseId, group.targetLang, group.lemma) },
                        onNavigateToDetail = { onNavigateToWordDetail(group.targetLang, group.lemma, sense.senseId) },
                    )
                }
            }
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
    translations: Map<Language, List<LanguageCardTranslation>> = emptyMap()
): LanguageCardResponseSense {
    return LanguageCardResponseSense(
        senseId = id,
        senseDefinition = definition,
        learnerLevel = level,
        frequency = frequency,
        semanticGroupId = "group1",
        nameType = null,
        examples = examples,
        synonyms = emptyList(),
        antonyms = emptyList(),
        commonPhrases = emptyList(),
        traits = emptyList(),
        targetLangDefinitions = mapOf(Language.ENGLISH to definition),
        translations = translations
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
            translations = mapOf(
                Language.POLISH to listOf(
                    LanguageCardTranslation("biegać", null)
                )
            )
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
                                showNavigationArrow = true,
                                pos = PartOfSpeech.VERB
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
            frequency = SenseFrequency.HIGH,
            translations = mapOf(Language.POLISH to listOf(LanguageCardTranslation("biegać")))
        )

        val runSense2 = createMockSense(
            id = "run-2",
            definition = "to operate or control",
            level = LearnerLevel.B1,
            frequency = SenseFrequency.MIDDLE,
            translations = mapOf(Language.POLISH to listOf(LanguageCardTranslation("prowadzić")))
        )

        val bookSense1 = createMockSense(
            id = "book-1",
            definition = "a written or printed work",
            level = LearnerLevel.A1,
            frequency = SenseFrequency.HIGH,
            translations = mapOf(Language.POLISH to listOf(LanguageCardTranslation("książka")))
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
                                "run-1",
                                expanded = false,
                                examplesExpanded = false,
                                languageExpanded = emptyMap(),
                                favorite = true,
                                showNavigationArrow = true,
                                pos = PartOfSpeech.VERB
                            )
                        ),
                        FavoriteSenseUiState(
                            favorite = createMockFavorite("run-2", Language.ENGLISH, "run", 900000),
                            sense = runSense2,
                            state = SenseUiState(
                                "run-2",
                                expanded = false,
                                examplesExpanded = false,
                                languageExpanded = emptyMap(),
                                favorite = true,
                                showNavigationArrow = true,
                                pos = PartOfSpeech.VERB
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
                                "book-1",
                                expanded = false,
                                examplesExpanded = false,
                                languageExpanded = emptyMap(),
                                favorite = false,
                                showNavigationArrow = true,
                                pos = PartOfSpeech.NOUN
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
            ),
            translations = mapOf(
                Language.POLISH to listOf(
                    LanguageCardTranslation("szczęśliwy", "in a good mood"),
                    LanguageCardTranslation("zadowolony", "satisfied")
                )
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
                                showNavigationArrow = true,
                                pos = PartOfSpeech.ADJECTIVE
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
            frequency = SenseFrequency.HIGH,
            translations = mapOf(Language.POLISH to listOf(LanguageCardTranslation("miłość")))
        )

        val sense2 = createMockSense(
            id = "love-2",
            definition = "to feel deep affection for someone",
            level = LearnerLevel.A2,
            frequency = SenseFrequency.HIGH,
            examples = listOf(LanguageCardExample("I love you", mapOf(Language.POLISH to "Kocham cię"))),
            translations = mapOf(Language.POLISH to listOf(LanguageCardTranslation("kochać")))
        )

        val runSense = createMockSense(
            id = "run-1",
            definition = "to move swiftly on foot",
            level = LearnerLevel.A1,
            frequency = SenseFrequency.HIGH,
            translations = mapOf(Language.POLISH to listOf(LanguageCardTranslation("biegać")))
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
                                "love-1", true,
                                examplesExpanded = false,
                                languageExpanded = emptyMap(),
                                favorite = true,
                                showNavigationArrow = true,
                                pos = PartOfSpeech.NOUN
                            )
                        ),
                        FavoriteSenseUiState(
                            favorite = createMockFavorite("love-2", Language.ENGLISH, "love", 1000000),
                            sense = sense2,
                            state = SenseUiState(
                                "love-2", false,
                                examplesExpanded = false,
                                languageExpanded = emptyMap(),
                                favorite = false,
                                showNavigationArrow = true,
                                pos = PartOfSpeech.VERB
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
                                "run-1", false,
                                examplesExpanded = false,
                                languageExpanded = emptyMap(),
                                favorite = true,
                                showNavigationArrow = true,
                                pos = PartOfSpeech.VERB
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
            frequency = SenseFrequency.HIGH,
            translations = mapOf(Language.POLISH to listOf(LanguageCardTranslation("biegać")))
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
                                "run-1", false,
                                examplesExpanded = false,
                                languageExpanded = emptyMap(),
                                favorite = true,
                                showNavigationArrow = true,
                                pos = PartOfSpeech.VERB
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
            frequency = SenseFrequency.HIGH,
            translations = mapOf(Language.POLISH to listOf(LanguageCardTranslation("książka")))
        )

        val bookmarkSense1 = createMockSense(
            id = "bookmark-1",
            definition = "a strip of material used to mark one's place in a book",
            level = LearnerLevel.B1,
            frequency = SenseFrequency.MIDDLE,
            translations = mapOf(Language.POLISH to listOf(LanguageCardTranslation("zakładka")))
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
                                "book-1", false,
                                examplesExpanded = false,
                                languageExpanded = emptyMap(),
                                favorite = true,
                                showNavigationArrow = true,
                                pos = PartOfSpeech.NOUN
                            )
                        )
                    ),
                    expanded = false
                ),
                FavoriteGroupUiState(
                    targetLang = Language.ENGLISH,
                    lemma = "bookmark",
                    senses = listOf(
                        FavoriteSenseUiState(
                            favorite = createMockFavorite("bookmark-1", Language.ENGLISH, "bookmark"),
                            sense = bookmarkSense1,
                            state = SenseUiState(
                                "bookmark-1", false,
                                examplesExpanded = false,
                                languageExpanded = emptyMap(),
                                favorite = true,
                                showNavigationArrow = true,
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
