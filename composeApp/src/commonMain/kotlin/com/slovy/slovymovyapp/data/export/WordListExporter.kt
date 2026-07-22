package com.slovy.slovymovyapp.data.export

import com.slovy.slovymovyapp.data.Language
import com.slovy.slovymovyapp.data.favorites.Favorite
import com.slovy.slovymovyapp.data.favorites.FavoritesRepository
import com.slovy.slovymovyapp.data.favorites.SenseCardExportRow
import com.slovy.slovymovyapp.data.remote.DictionaryRepository
import com.slovy.slovymovyapp.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Writes a word-list export file to the platform's user-visible location.
 * [save] persists the rendered text; [share] opens the platform share sheet where supported.
 */
expect class WordListFileSaver(androidContext: Any? = null) {
    val isSupported: Boolean
    val canShare: Boolean

    suspend fun save(fileName: String, mimeType: String, content: String): WordListSaveResult
    fun share(result: WordListSaveResult, mimeType: String)
}

data class WordListSaveResult(
    val fileName: String,
    val destinationLabel: String,
    val shareReference: String? = null,
)

/**
 * Exports the favorites of one language to a CSV/TSV file. Sense details are resolved from the
 * locally available dictionary data only; senses that cannot be resolved (not downloaded yet,
 * online-only) still export their lemma and dates with empty detail columns.
 */
@OptIn(ExperimentalTime::class)
class WordListExporter(
    private val favoritesRepository: FavoritesRepository,
    private val dictionaryRepository: DictionaryRepository,
    private val fileSaver: WordListFileSaver,
    private val clock: Clock,
) {
    val isSupported: Boolean get() = fileSaver.isSupported
    val canShare: Boolean get() = fileSaver.canShare

    data class Outcome(
        val saved: WordListSaveResult,
        val mimeType: String,
        val rowCount: Int,
        val rowsWithoutDetails: Int,
    )

    suspend fun export(
        language: Language,
        format: WordListExportFormat,
        includeStats: Boolean,
    ): Outcome {
        val favorites = favoritesRepository.getAllGroupedByLangAndLemma()
            .filter { it.language == language }
        val translationLanguages = dictionaryRepository.defaultTranslationTargets(language)
        val statsBySense = if (includeStats) {
            favoritesRepository.getCardExportRowsBySense(language)
        } else {
            emptyMap()
        }
        val rows = buildRows(language, favorites, translationLanguages, statsBySense)
        val content = WordListExportContent(
            format = format,
            translationLanguages = translationLanguages,
            includeStats = includeStats,
            timeZone = TimeZone.currentSystemDefault(),
        ).render(rows)
        val saved = fileSaver.save(
            fileName = exportFileName(language, format),
            mimeType = format.mimeType,
            content = content,
        )
        return Outcome(
            saved = saved,
            mimeType = format.mimeType,
            rowCount = rows.size,
            rowsWithoutDetails = rows.count { !it.hasDetails },
        )
    }

    fun share(outcome: Outcome) {
        fileSaver.share(outcome.saved, outcome.mimeType)
    }

    private suspend fun buildRows(
        language: Language,
        favorites: List<Favorite>,
        translationLanguages: List<Language>,
        statsBySense: Map<String, List<SenseCardExportRow>>,
    ): List<WordListExportRow> {
        return favorites.groupBy { it.lemma }.flatMap { (lemma, lemmaFavorites) ->
            val senseIds = lemmaFavorites.map { it.senseId }.toSet()
            val lookup = try {
                dictionaryRepository.getSenses(language, lemma, senseIds, translationLanguages)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.warn(TAG, "Sense lookup failed for '$lemma' (${language.code})", e)
                emptyMap()
            }
            lemmaFavorites.map { favorite ->
                val resolved = lookup[favorite.senseId]?.sense
                val sense = resolved?.sense
                WordListExportRow(
                    lemma = favorite.lemma,
                    pos = resolved?.pos?.name?.lowercase(),
                    definition = sense?.senseDefinition,
                    level = sense?.learnerLevel,
                    translations = sense?.translations.orEmpty().mapValues { (_, words) ->
                        words.joinToString("; ") { it.targetLangWord }
                    },
                    example = sense?.examples?.firstOrNull()?.text,
                    addedAtEpochMs = favorite.createdAt,
                    stats = statsBySense[favorite.senseId]?.let(WordListExportStats::aggregate),
                )
            }
        }
    }

    private fun exportFileName(language: Language, format: WordListExportFormat): String {
        val now = clock.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val timestamp = buildString {
            append(now.year.toString().padStart(4, '0'))
            append((now.month.ordinal + 1).toString().padStart(2, '0'))
            append(now.day.toString().padStart(2, '0'))
            append('-')
            append(now.hour.toString().padStart(2, '0'))
            append(now.minute.toString().padStart(2, '0'))
            append(now.second.toString().padStart(2, '0'))
        }
        return "slovymovy-words-${language.code}-$timestamp.${format.fileExtension}"
    }

    companion object {
        private const val TAG = "WordListExporter"
    }
}
