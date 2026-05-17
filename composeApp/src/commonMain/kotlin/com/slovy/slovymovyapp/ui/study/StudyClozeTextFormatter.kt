package com.slovy.slovymovyapp.ui.study

internal data class StudyClozeDisplayText(
    val text: String,
    val answerRanges: List<IntRange>,
)

internal fun StudyClozeTextUiState.toDisplayText(): StudyClozeDisplayText {
    val normalizedRanges = answerRanges.mapNotNull { range ->
        val start = range.first.coerceIn(0, text.length)
        val endExclusive = (range.last + 1).coerceIn(start, text.length)
        if (start == endExclusive) null else start until endExclusive
    }.sortedBy { it.first }

    val output = StringBuilder()
    val outputRanges = mutableListOf<IntRange>()
    var cursor = 0

    normalizedRanges.forEach { range ->
        val start = maxOf(range.first, cursor)
        val endExclusive = range.last + 1
        if (start >= endExclusive) return@forEach

        output.append(text.substring(cursor, start))
        val outputStart = output.length
        val answer = text.substring(start, endExclusive)
        if (filled) {
            output.append(answer)
        } else {
            repeat(answer.length.coerceAtLeast(6)) {
                output.append('\u00A0')
            }
        }
        outputRanges.add(outputStart until output.length)
        cursor = endExclusive
    }

    output.append(text.substring(cursor))
    return StudyClozeDisplayText(
        text = output.toString(),
        answerRanges = outputRanges,
    )
}
