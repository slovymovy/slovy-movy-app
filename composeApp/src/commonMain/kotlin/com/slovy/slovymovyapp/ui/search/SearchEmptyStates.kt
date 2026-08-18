package com.slovy.slovymovyapp.ui.search


import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.slovy.slovymovyapp.data.lists.WordList
import com.slovy.slovymovyapp.ui.components.SpinningProgressIndicator
import com.slovy.slovymovyapp.ui.theme.AppSpacing
import com.slovy.slovymovyapp.ui.word.ClipboardVector
import com.slovy.slovymovyapp.ui.word.FavoriteAccentColor
import androidx.compose.ui.unit.dp
import com.slovy.slovymovyapp.ui.components.EmptyState
import com.slovy.slovymovyapp.ui.icons.NoDictionaryIcon
import com.slovy.slovymovyapp.ui.icons.SearchOtter
import com.slovy.slovymovyapp.ui.icons.SlovyIcons
import com.slovy.slovymovyapp.ui.theme.LocalIsDarkTheme
import com.slovy.slovymovyapp.ui.theme.serifFontFamily
import com.slovy.slovymovyapp.ui.theme.uiItalic
import com.slovy.slovymovyapp.ui.word.colorForLemma
import kotlinx.coroutines.flow.*
import org.jetbrains.compose.resources.stringResource
import slovymovyapp.composeapp.generated.resources.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun EmptySearchState(
    wordSuggestions: List<String>,
    favoriteLemmas: List<String>,
    curatedLists: List<WordList>,
    isLoading: Boolean,
    onWordClick: (String) -> Unit,
    onListClick: (WordList) -> Unit,
    onSuggestListClick: () -> Unit,
    onNavigateToTextReader: () -> Unit = {}
) {
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            SpinningProgressIndicator()
        }
        return
    }
    val isDark = LocalIsDarkTheme.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.lg)
            .padding(top = AppSpacing.xs, bottom = AppSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        if (wordSuggestions.isNotEmpty()) {
            Text(
                text = stringResource(Res.string.search_section_explore).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp,
                    fontSize = 10.5.sp,
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = AppSpacing.sm, bottom = AppSpacing.xxs)
            )
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AppSpacing.xxs),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                wordSuggestions.forEach { lemma ->
                    WordChip(lemma = lemma, onClick = { onWordClick(lemma) })
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AppSpacing.xsPlus),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.xsPlus, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(Res.string.search_pull_to_refresh_hint),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // "Add words" entry into the Read (paste-to-highlight) flow — its own feed
        // section, sitting below Explore and above the curated lists.
        ReadSection(onClick = onNavigateToTextReader)

        if (curatedLists.isNotEmpty()) {
            Text(
                text = stringResource(Res.string.search_section_lists_for_you).uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp,
                    fontSize = 10.5.sp,
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(
                    top = if (wordSuggestions.isNotEmpty()) AppSpacing.md else AppSpacing.sm,
                    bottom = AppSpacing.xxs
                )
            )
            curatedLists.forEachIndexed { index, list ->
                ListCard(
                    list = list,
                    featured = index == 0,
                    isDark = isDark,
                    onClick = { onListClick(list) }
                )
            }
            TextButton(
                onClick = onSuggestListClick,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = AppSpacing.xs),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Flag,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(Res.string.search_suggest_list_button),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else if (favoriteLemmas.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = if (wordSuggestions.isNotEmpty()) AppSpacing.md else AppSpacing.sm,
                        bottom = AppSpacing.xxs,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.xsPlus)
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = FavoriteAccentColor,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = stringResource(Res.string.search_section_recently_saved).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            favoriteLemmas.forEach { lemma ->
                SuggestionCard(lemma = lemma, onClick = { onWordClick(lemma) })
            }
        }

        if (wordSuggestions.isEmpty() && curatedLists.isEmpty() && favoriteLemmas.isEmpty()) {
            Text(
                text = stringResource(Res.string.search_empty_start_typing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AppSpacing.xxl),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * "ADD WORDS" feed section — the entry into the Read (paste-to-highlight) flow.
 * A flat utility row matching the list cards' surface + hairline border; the copper accent
 * is confined to the eyebrow and the small clipboard tile so it reads as utility, not a CTA.
 */
@Composable
private fun ReadSection(onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(16.dp)
    // Warm but flat: a 16% primary wash over the container, no border, no shadow/glow.
    val rowFill = lerp(MaterialTheme.colorScheme.surfaceContainer, primary, 0.16f)
    Column(modifier = Modifier.padding(bottom = AppSpacing.xs)) {
        Text(
            text = stringResource(Res.string.search_add_words).uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp,
                fontSize = 10.5.sp,
            ),
            color = primary,
            modifier = Modifier.padding(top = AppSpacing.sm, bottom = AppSpacing.smPlus)
        )
        // Min height + real vertical padding so the row grows when a localized label wraps
        // to two lines (e.g. German) instead of crowding or clipping on a fixed height.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(shape)
                .background(rowFill)
                .clickable(onClickLabel = stringResource(Res.string.search_add_words_cd), role = Role.Button) { onClick() }
                .padding(start = AppSpacing.md, end = AppSpacing.mdPlus, top = AppSpacing.md, bottom = AppSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = ClipboardVector,
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(19.dp)
                )
            }
            Text(
                text = stringResource(Res.string.search_add_words_row),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    lineHeight = 19.sp, // 1.25 × font size so wrapped lines don't sit too tight
                    letterSpacing = 0.1.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                // Centers the first text line (19sp line height) on the 34dp icon tile, so the
                // icon stays anchored to line one instead of floating mid-gap on two-line copy.
                modifier = Modifier.weight(1f).padding(top = AppSpacing.xsPlus)
            )
        }
    }
}

@Composable
private fun WordChip(lemma: String, onClick: () -> Unit) {
    // Single quiet sage tint for every chip — one color reads as "just words". The border
    // does the heavy lifting so the edge survives on both the cream and near-black surfaces.
    val isDark = LocalIsDarkTheme.current
    val bgColor = if (isDark) Color(0xFF2A3326) else Color(0xFFE6EBDD)
    val lineColor = if (isDark) Color(0xFF3A4636) else Color(0xFFD2DCC4)
    val textColor = if (isDark) Color(0xFFD4DEC8) else Color(0xFF2D2620)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(bgColor)
            .border(0.5.dp, lineColor, RoundedCornerShape(9.dp))
            .semantics { role = Role.Button }
            .clickable(
                onClickLabel = stringResource(Res.string.search_open_item, lemma),
                onClick = onClick
            )
            // Taller vertical padding lifts the tap target without the empty centering gap
            // that a reserved 48dp slot leaves between wrapped rows.
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.smPlus)
    ) {
        Text(
            text = lemma,
            fontFamily = MaterialTheme.serifFontFamily,
            fontSize = 15.sp,
            lineHeight = 17.25.sp,
            letterSpacing = (-0.1).sp,
            color = textColor
        )
    }
}

@Composable
private fun SuggestionCard(lemma: String, onClick: () -> Unit) {
    val containerColor = colorForLemma(lemma, MaterialTheme.colorScheme.surface, LocalIsDarkTheme.current)
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { role = Role.Button }
            .clickable(
                onClickLabel = stringResource(Res.string.search_open_item, lemma),
                onClick = onClick
            ),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Text(
            text = lemma,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontFamily = MaterialTheme.serifFontFamily
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.mdPlus)
        )
    }
}

@Composable
internal fun NoDictionaryState(
    scrollState: ScrollState,
    onNavigateToSettings: () -> Unit
) {
    ScrollableNoDictionaryContainer(
        scrollState = scrollState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                imageVector = SlovyIcons.NoDictionaryIcon,
                contentDescription = null,
                modifier = Modifier.size(204.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = stringResource(Res.string.search_no_dictionary_title),
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = MaterialTheme.serifFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 34.sp,
                ),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 340.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(Res.string.search_no_dictionary_description),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = MaterialTheme.serifFontFamily,
                    fontStyle = MaterialTheme.uiItalic,
                    lineHeight = 24.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 320.dp)
            )

            Spacer(modifier = Modifier.height(36.dp))

            Button(
                onClick = onNavigateToSettings,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                contentPadding = PaddingValues(horizontal = AppSpacing.xlPlus),
                modifier = Modifier
                    .height(56.dp)
                    .widthIn(min = 220.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(Res.string.search_go_to_settings),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}

@Composable
private fun ScrollableNoDictionaryContainer(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = maxHeight)
                .verticalScroll(scrollState),
            contentAlignment = BiasAlignment(horizontalBias = 0f, verticalBias = -0.18f),
            content = content
        )
    }
}

@Composable
internal fun NoResultsState(query: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = BiasAlignment(horizontalBias = 0f, verticalBias = -0.3f)
    ) {
        EmptyState(
            iconContent = {
                Image(
                    imageVector = SlovyIcons.SearchOtter,
                    contentDescription = null,
                    modifier = Modifier.size(140.dp)
                )
            },
            title = stringResource(Res.string.search_no_results_title, query),
            description = stringResource(Res.string.search_no_results_description)
        )
    }
}

