/*
 * Adapted from FSRS-Kotlin:
 * https://github.com/open-spaced-repetition/FSRS-Kotlin
 * Original FSRS author: Jarrett Ye.
 *
 * FSRS-Kotlin is provided under the MIT License. See THIRD_PARTY_NOTICES.md
 * in this directory for attribution and license details.
 */

package external.fsrs

enum class Rating(val value: Int) {
    Again(1),
    Hard(2),
    Good(3),
    Easy(4),
}

enum class CardPhase(val value: Int) {
    Added(0),
    ReLearning(1),
    Review(2),
}

data class Grade(
    val title: String,
    val durationMillis: Long,
    val interval: Int,
    val text: String,
    val choice: Rating,
    val stability: Double,
    val difficulty: Double,
)

data class FlashCard(
    val stability: Double,
    val difficulty: Double,
    val interval: Int,
    val elapsedDays: Double,
    val reviewCount: Int,
    val phase: Int,
)
