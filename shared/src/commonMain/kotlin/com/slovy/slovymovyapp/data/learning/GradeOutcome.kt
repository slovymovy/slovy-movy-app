package com.slovy.slovymovyapp.data.learning

data class GradeOutcome(
    val rating: Rating,
    val newState: CardState,
    val stability: Double,
    val difficulty: Double,
    val intervalMillis: Long,
)

enum class Rating(val fsrsValue: Long) {
    AGAIN(1),
    HARD(2),
    GOOD(3),
    EASY(4);

    companion object {
        fun fromFsrsValue(value: Long): Rating =
            entries.firstOrNull { it.fsrsValue == value }
                ?: error("Unknown rating value: $value")
    }
}
