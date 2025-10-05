package com.slovy.slovymovyapp.ui

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.slovy.slovymovyapp.data.favorites.Favorite
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.remote.*
import com.slovy.slovymovyapp.ui.word.SenseCard
import com.slovy.slovymovyapp.ui.word.SenseUiState
import org.jetbrains.compose.ui.tooling.preview.Preview

data class FavoriteGroupUiState(
    val targetLang: String,
    val lemma: String,
    val senses: List<FavoriteSenseUiState>,
    val expanded: Boolean = false
)

data class FavoriteSenseUiState(
    val favorite: Favorite,
    val sense: LanguageCardResponseSense,
    val state: SenseUiState
)

data class FavoritesUiState(
    val groups: List<FavoriteGroupUiState>,
    val isEmpty: Boolean
)

class FavoritesViewModel(
    private val favoritesRepository: FavoritesRepository,
    private val dictionaryRepository: DictionaryRepository
) : ViewModel() {

    var state by mutableStateOf(FavoritesUiState(groups = emptyList(), isEmpty = true))
        private set

    val scrollState = LazyListState()

    init {
        loadFavorites()
    }

    fun loadFavorites() {
        val favorites = favoritesRepository.getAllGroupedByLangAndLemma()
        val allFavSenses = favoritesRepository.getAll().map { it.senseId }.toSet()

        // Group by (targetLang, lemma)
        val grouped = favorites.groupBy { it.targetLang to it.lemma }

        val groups = grouped.map { (langLemma, favs) ->
            val (targetLang, lemma) = langLemma
            val card = dictionaryRepository.getLanguageCard(targetLang, lemma)

            // Find existing group to preserve state
            val existingGroup = state.groups.find { it.targetLang == targetLang && it.lemma == lemma }

            val senses = favs.mapNotNull { favorite ->
                // Find the entry and sense
                val entryAndSense = card?.entries?.firstNotNullOfOrNull { entry ->
                    entry.senses.find { it.senseId == favorite.senseId }?.let { sense ->
                        entry to sense
                    }
                }

                if (entryAndSense != null) {
                    val (entry, sense) = entryAndSense
                    // Find existing sense state to preserve
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
                            pos = entry.pos
                        )
                    )
                } else {
                    null
                }
            }

            FavoriteGroupUiState(
                targetLang = targetLang,
                lemma = lemma,
                senses = senses,
                expanded = existingGroup?.expanded ?: false
            )
        }.filter { it.senses.isNotEmpty() }

        state = FavoritesUiState(
            groups = groups,
            isEmpty = groups.isEmpty()
        )
    }

    private fun updateSenseState(senseId: String, updateFn: (SenseUiState) -> SenseUiState) {
        state = state.copy(
            groups = state.groups.map { group ->
                group.copy(
                    senses = group.senses.map { favSense ->
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
        updateSenseState(senseId) { it.copy(expanded = !it.expanded) }
    }

    fun toggleSenseExamples(senseId: String) {
        updateSenseState(senseId) { it.copy(examplesExpanded = !it.examplesExpanded) }
    }

    fun toggleLanguage(senseId: String, languageCode: String) {
        updateSenseState(senseId) { currentState ->
            val current = currentState.languageExpanded[languageCode] ?: currentState.expanded
            currentState.copy(
                languageExpanded = currentState.languageExpanded + (languageCode to !current)
            )
        }
    }

    fun toggleFavorite(senseId: String, targetLang: String, lemma: String) {
        val isFavorite = if (favoritesRepository.exists(senseId, targetLang)) {
            favoritesRepository.remove(senseId, targetLang)
            false
        } else {
            favoritesRepository.add(senseId, targetLang, lemma)
            true
        }
        updateSenseState(senseId) { it.copy(favorite = isFavorite) }
    }

    fun toggleGroup(targetLang: String, lemma: String) {
        val updated = state.groups.map { group ->
            if (group.targetLang == targetLang && group.lemma == lemma) {
                group.copy(expanded = !group.expanded)
            } else {
                group
            }
        }
        state = state.copy(groups = updated)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    viewModel: FavoritesViewModel,
    onNavigateToSearch: () -> Unit = {},
    onNavigateToWordDetail: (String, String, String) -> Unit = { _, _, _ -> },
    isWordDetailAvailable: Boolean = false,
    onNavigateToLastWordDetail: () -> Unit = {}
) {
    FavoritesScreenContent(
        state = viewModel.state,
        scrollState = viewModel.scrollState,
        onNavigateToSearch = onNavigateToSearch,
        onSenseToggle = { senseId -> viewModel.toggleSense(senseId) },
        onSenseExamplesToggle = { senseId -> viewModel.toggleSenseExamples(senseId) },
        onLanguageToggle = { senseId, lang -> viewModel.toggleLanguage(senseId, lang) },
        onFavoriteToggle = { senseId, targetLang, lemma -> viewModel.toggleFavorite(senseId, targetLang, lemma) },
        onGroupToggle = { targetLang, lemma -> viewModel.toggleGroup(targetLang, lemma) },
        isWordDetailAvailable = isWordDetailAvailable,
        onNavigateToLastWordDetail = onNavigateToLastWordDetail,
        onNavigateToWordDetail = onNavigateToWordDetail
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreenContent(
    state: FavoritesUiState,
    scrollState: LazyListState = LazyListState(),
    onNavigateToSearch: () -> Unit = {},
    onSenseToggle: (String) -> Unit = {},
    onSenseExamplesToggle: (String) -> Unit = {},
    onLanguageToggle: (String, String) -> Unit = { _, _ -> },
    onFavoriteToggle: (String, String, String) -> Unit = { _, _, _ -> },
    onGroupToggle: (String, String) -> Unit = { _, _ -> },
    isWordDetailAvailable: Boolean = false,
    onNavigateToLastWordDetail: () -> Unit = {},
    onNavigateToWordDetail: (String, String, String) -> Unit = { _, _, _ -> }
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
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            AppNavigationBar(
                currentScreen = AppScreen.FAVORITES,
                isWordDetailAvailable = isWordDetailAvailable,
                onNavigateToSearch = onNavigateToSearch,
                onNavigateToFavorites = {},
                onNavigateToWordDetail = onNavigateToLastWordDetail
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (state.isEmpty) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No favorites yet.\nAdd favorites by tapping the heart icon on any word sense.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                state = scrollState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(vertical = 24.dp)
            ) {
                items(state.groups, key = { "${it.targetLang}_${it.lemma}" }) { group ->
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

@Composable
private fun FavoriteGroupCard(
    group: FavoriteGroupUiState,
    onSenseToggle: (String) -> Unit,
    onSenseExamplesToggle: (String) -> Unit,
    onLanguageToggle: (String, String) -> Unit,
    onFavoriteToggle: (String, String, String) -> Unit,
    onGroupToggle: () -> Unit = {},
    onNavigateToWordDetail: (String, String, String) -> Unit = { _, _, _ -> }
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
                    modifier = Modifier.weight(1f),
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
                    Text(
                        text = "• ${codeToLanguage.getOrElse(group.targetLang) { group.targetLang }}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
                Icon(
                    imageVector = if (group.expanded) com.slovy.slovymovyapp.ui.word.ExpandLessVector else com.slovy.slovymovyapp.ui.word.ExpandMoreVector,
                    contentDescription = if (group.expanded) "Collapse group" else "Expand group",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
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

                val allSenses = group.senses.map { it.sense }.toList()
                group.senses.forEach { favSense ->
                    val sense = favSense.sense
                    SenseCard(
                        sense = sense,
                        state = favSense.state,
                        onToggle = { onSenseToggle(sense.senseId) },
                        onExamplesToggle = { onSenseExamplesToggle(sense.senseId) },
                        onLanguageToggle = { lang -> onLanguageToggle(sense.senseId, lang) },
                        allSenses = allSenses,
                        onFavoriteToggle = { onFavoriteToggle(sense.senseId, group.targetLang, group.lemma) },
                        onNavigateToDetail = { onNavigateToWordDetail(group.targetLang, group.lemma, sense.senseId) }
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
    translations: Map<String, List<LanguageCardTranslation>> = emptyMap()
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
        targetLangDefinitions = mapOf("en" to definition),
        translations = translations
    )
}

private fun createMockFavorite(
    senseId: String,
    targetLang: String,
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
fun PreviewFavoritesScreenEmpty() {
    FavoritesScreenContent(
        state = FavoritesUiState(groups = emptyList(), isEmpty = true)
    )
}

@Preview
@Composable
fun PreviewFavoritesScreenSingleGroupCollapsed() {
    val sense1 = createMockSense(
        id = "run-1",
        definition = "to move swiftly on foot",
        level = LearnerLevel.A1,
        frequency = SenseFrequency.HIGH,
        examples = listOf(
            LanguageCardExample("She runs every morning", mapOf("pl" to "Ona biegnie każdego ranka"))
        ),
        translations = mapOf(
            "pl" to listOf(
                LanguageCardTranslation("biegać", null)
            )
        )
    )

    val state = FavoritesUiState(
        groups = listOf(
            FavoriteGroupUiState(
                targetLang = "en",
                lemma = "run",
                senses = listOf(
                    FavoriteSenseUiState(
                        favorite = createMockFavorite("run-1", "en", "run"),
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
        isEmpty = false
    )

    FavoritesScreenContent(state = state)
}

@Preview
@Composable
fun PreviewFavoritesScreenMultipleGroupsCollapsed() {
    val runSense1 = createMockSense(
        id = "run-1",
        definition = "to move swiftly on foot",
        level = LearnerLevel.A1,
        frequency = SenseFrequency.HIGH,
        translations = mapOf("pl" to listOf(LanguageCardTranslation("biegać")))
    )

    val runSense2 = createMockSense(
        id = "run-2",
        definition = "to operate or control",
        level = LearnerLevel.B1,
        frequency = SenseFrequency.MIDDLE,
        translations = mapOf("pl" to listOf(LanguageCardTranslation("prowadzić")))
    )

    val bookSense1 = createMockSense(
        id = "book-1",
        definition = "a written or printed work",
        level = LearnerLevel.A1,
        frequency = SenseFrequency.HIGH,
        translations = mapOf("pl" to listOf(LanguageCardTranslation("książka")))
    )

    val state = FavoritesUiState(
        groups = listOf(
            FavoriteGroupUiState(
                targetLang = "en",
                lemma = "run",
                senses = listOf(
                    FavoriteSenseUiState(
                        favorite = createMockFavorite("run-1", "en", "run", 1000000),
                        sense = runSense1,
                        state = SenseUiState("run-1", false, false, emptyMap(), true, true, PartOfSpeech.VERB)
                    ),
                    FavoriteSenseUiState(
                        favorite = createMockFavorite("run-2", "en", "run", 900000),
                        sense = runSense2,
                        state = SenseUiState("run-2", false, false, emptyMap(), true, true, PartOfSpeech.VERB)
                    )
                ),
                expanded = false
            ),
            FavoriteGroupUiState(
                targetLang = "en",
                lemma = "book",
                senses = listOf(
                    FavoriteSenseUiState(
                        favorite = createMockFavorite("book-1", "en", "book", 800000),
                        sense = bookSense1,
                        state = SenseUiState("book-1", false, false, emptyMap(), false, true, PartOfSpeech.NOUN)
                    )
                ),
                expanded = false,
            )
        ),
        isEmpty = false
    )

    FavoritesScreenContent(state = state)
}

@Preview
@Composable
fun PreviewFavoritesScreenGroupExpanded() {
    val sense1 = createMockSense(
        id = "happy-1",
        definition = "feeling or showing pleasure or contentment",
        level = LearnerLevel.A2,
        frequency = SenseFrequency.HIGH,
        examples = listOf(
            LanguageCardExample("I'm so happy today!", mapOf("pl" to "Jestem dzisiaj taki szczęśliwy!")),
            LanguageCardExample("She looks happy", mapOf("pl" to "Ona wygląda na szczęśliwą"))
        ),
        translations = mapOf(
            "pl" to listOf(
                LanguageCardTranslation("szczęśliwy", "in a good mood"),
                LanguageCardTranslation("zadowolony", "satisfied")
            )
        )
    )

    val state = FavoritesUiState(
        groups = listOf(
            FavoriteGroupUiState(
                targetLang = "en",
                lemma = "happy",
                senses = listOf(
                    FavoriteSenseUiState(
                        favorite = createMockFavorite("happy-1", "en", "happy"),
                        sense = sense1,
                        state = SenseUiState(
                            senseId = "happy-1",
                            expanded = true,
                            examplesExpanded = true,
                            languageExpanded = mapOf("pl" to true),
                            favorite = true,
                            showNavigationArrow = true,
                            pos = PartOfSpeech.ADJECTIVE
                        )
                    )
                ),
                expanded = true
            )
        ),
        isEmpty = false
    )

    FavoritesScreenContent(state = state)
}

@Preview
@Composable
fun PreviewFavoritesScreenMixedStates() {
    val sense1 = createMockSense(
        id = "love-1",
        definition = "an intense feeling of deep affection",
        level = LearnerLevel.A2,
        frequency = SenseFrequency.HIGH,
        translations = mapOf("pl" to listOf(LanguageCardTranslation("miłość")))
    )

    val sense2 = createMockSense(
        id = "love-2",
        definition = "to feel deep affection for someone",
        level = LearnerLevel.A2,
        frequency = SenseFrequency.HIGH,
        examples = listOf(LanguageCardExample("I love you", mapOf("pl" to "Kocham cię"))),
        translations = mapOf("pl" to listOf(LanguageCardTranslation("kochać")))
    )

    val runSense = createMockSense(
        id = "run-1",
        definition = "to move swiftly on foot",
        level = LearnerLevel.A1,
        frequency = SenseFrequency.HIGH,
        translations = mapOf("pl" to listOf(LanguageCardTranslation("biegać")))
    )

    val state = FavoritesUiState(
        groups = listOf(
            FavoriteGroupUiState(
                targetLang = "en",
                lemma = "love",
                senses = listOf(
                    FavoriteSenseUiState(
                        favorite = createMockFavorite("love-1", "en", "love", 2000000),
                        sense = sense1,
                        state = SenseUiState("love-1", true, false, emptyMap(), true, true, PartOfSpeech.NOUN)
                    ),
                    FavoriteSenseUiState(
                        favorite = createMockFavorite("love-2", "en", "love", 1000000),
                        sense = sense2,
                        state = SenseUiState("love-2", false, false, emptyMap(), false, true, PartOfSpeech.VERB)
                    )
                ),
                expanded = true
            ),
            FavoriteGroupUiState(
                targetLang = "en",
                lemma = "run",
                senses = listOf(
                    FavoriteSenseUiState(
                        favorite = createMockFavorite("run-1", "en", "run", 1500000),
                        sense = runSense,
                        state = SenseUiState("run-1", false, false, emptyMap(), true, true, PartOfSpeech.VERB)
                    )
                ),
                expanded = false
            )
        ),
        isEmpty = false
    )

    FavoritesScreenContent(state = state)
}
