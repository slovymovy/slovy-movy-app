package com.slovy.slovymovyapp.data.util

data class ClozeParseResult(
    val plainText: String,
    val answerRanges: List<IntRange>,
)

fun parseClozeFromTaggedText(text: String): ClozeParseResult? {
    if (text.isBlank()) return null
    val output = StringBuilder()
    val ranges = mutableListOf<IntRange>()
    HtmlTagParser.parseTextSegments(text).forEach { segment ->
        val start = output.length
        val segmentText = HtmlTagParser.plainText(segment.text)
        output.append(segmentText)
        if (segment.isTagged && segmentText.isNotBlank()) {
            val leading = segmentText.indexOfFirst { !it.isWhitespace() }
            val trailing = segmentText.indexOfLast { !it.isWhitespace() }
            ranges.add((start + leading)..(start + trailing))
        }
    }
    val plain = output.toString()
    if (plain.isBlank()) return null
    return ClozeParseResult(plainText = plain, answerRanges = ranges)
}
