package com.slovy.slovymovyapp.data.util

import com.slovy.slovymovyapp.data.util.HtmlTagParser.extractTaggedWords


/**
 * Utility for parsing HTML-like tags in text, specifically `<w>...</w>` tags
 * and single-word `<word>` tags
 * used to highlight words in dictionary definitions, examples, and phrases.
 *
 * This parser handles various edge cases:
 * - Nested tags: `<w>outer <w>inner</w></w>` - extracts "outer inner" as a single word
 * - Multiple words in one tag: `<w>word1 word2</w>` - can be split into individual words
 * - Malformed tags: `<w>unclosed` or `</w>no opening` - skips malformed sections
 * - Single-word tags: `<word>` - extracts "word" as a tagged segment
 * - Escaped content: Treats all content between tags as literal text
 */
object HtmlTagParser {

    /**
     * Represents a segment of text with its tag information.
     *
     * @property text The text content (without tags)
     * @property isTagged Whether this segment was inside `<w>` tags
     * @property startIndex The start index in the original string
     * @property endIndex The end index in the original string (exclusive)
     */
    data class TextSegment(
        val text: String,
        val isTagged: Boolean,
        val startIndex: Int,
        val endIndex: Int
    )

    /**
     * Parses text containing `<w>...</w>` tags or `<word>` tags into segments.
     *
     * This function splits the input text into segments, marking which parts
     * were inside tags and which were not. It properly handles:
     * - Nested tags (treats them as a single tagged segment)
     * - Malformed tags (treats unclosed tags as regular text)
     * - Multiple words inside a single tag
     *
     * Example:
     * ```
     * parseTextSegments("Hello <w>world</w> and <w>universe</w>!")
     * // Returns:
     * // [
     * //   TextSegment("Hello ", false, 0, 6),
     * //   TextSegment("world", true, 9, 14),
     * //   TextSegment(" and ", false, 18, 23),
     * //   TextSegment("universe", true, 26, 34),
     * //   TextSegment("!", false, 38, 39)
     * // ]
     * ```
     *
     * @param text The input text containing `<w>` tags or `<word>` tags
     * @return List of text segments with tag information
     */
    fun parseTextSegments(text: String): List<TextSegment> {
        if (text.isEmpty()) return emptyList()

        val segments = mutableListOf<TextSegment>()
        var i = 0

        while (i < text.length) {
            val startTag = "<w>"
            val endTag = "</w>"

            val tagStart = text.indexOf(startTag, i)
            val singleTagStart = findNextSingleWordTagStart(text, i)
            val nextTagStart = when {
                tagStart == -1 -> singleTagStart
                singleTagStart == -1 -> tagStart
                else -> minOf(tagStart, singleTagStart)
            }

            // No more tags found - append remaining text
            if (nextTagStart == -1) {
                if (i < text.length) {
                    segments.add(TextSegment(text.substring(i), false, i, text.length))
                }
                break
            }

            // Append text before the tag
            if (nextTagStart > i) {
                segments.add(TextSegment(text.substring(i, nextTagStart), false, i, nextTagStart))
            }

            if (nextTagStart == tagStart) {
                // Find matching closing tag, handling nesting
                val contentStart = tagStart + startTag.length
                var depth = 1
                var searchPos = contentStart
                var tagEnd = -1

                while (searchPos < text.length && depth > 0) {
                    val nextOpen = text.indexOf(startTag, searchPos)
                    val nextClose = text.indexOf(endTag, searchPos)

                    when {
                        nextClose == -1 -> {
                            // No closing tag found - treat the rest as untagged text
                            depth = 0
                            tagEnd = -1
                        }

                        nextOpen != -1 && nextOpen < nextClose -> {
                            // Found nested opening tag
                            depth++
                            searchPos = nextOpen + startTag.length
                        }

                        else -> {
                            // Found closing tag
                            depth--
                            if (depth == 0) {
                                tagEnd = nextClose
                            }
                            searchPos = nextClose + endTag.length
                        }
                    }
                }

                if (tagEnd != -1) {
                    // Found matching closing tag
                    val content = text.substring(contentStart, tagEnd)
                    segments.add(TextSegment(content, true, tagStart, tagEnd + endTag.length))
                    i = tagEnd + endTag.length
                } else {
                    // No matching closing tag - treat rest of text as untagged
                    segments.add(TextSegment(text.substring(tagStart), false, tagStart, text.length))
                    break
                }
            } else {
                val tagEnd = text.indexOf('>', nextTagStart + 1)
                if (tagEnd == -1) {
                    segments.add(TextSegment(text.substring(nextTagStart), false, nextTagStart, text.length))
                    break
                }

                val content = text.substring(nextTagStart + 1, tagEnd)
                if (isSingleWordTag(content)) {
                    segments.add(TextSegment(content, true, nextTagStart, tagEnd + 1))
                } else {
                    segments.add(TextSegment(text.substring(nextTagStart, tagEnd + 1), false, nextTagStart, tagEnd + 1))
                }
                i = tagEnd + 1
            }
        }

        return segments
    }

    /**
     * Extracts all words from `<w>...</w>` tags in the text.
     *
     * This function returns a list of unique words that were marked with `<w>` tags.
     * - Nested tags are flattened: `<w>outer <w>inner</w></w>` extracts "outer inner"
     * - Multiple words in one tag can be split: `<w>word1 word2</w>` extracts ["word1 word2"] or split based on splitMultipleWords
     * - Empty tags are ignored: `<w></w>` or `<w>   </w>`
     *
     * @param text The input text containing `<w>` tags
     * @param splitMultipleWords If true, splits content with spaces into separate words
     * @return List of extracted words (trimmed, non-empty, deduplicated if specified)
     */
    fun extractTaggedWords(
        text: String,
        splitMultipleWords: Boolean = false
    ): List<String> {
        val segments = parseTextSegments(text)
        val words = mutableListOf<String>()

        segments
            .filter { it.isTagged }
            .forEach { segment ->
                val content = segment.text.trim()
                if (content.isNotEmpty()) {
                    if (splitMultipleWords && content.contains(' ')) {
                        // Split multiple words and add each separately
                        content.split(Regex("\\s+"))
                            .filter { it.isNotEmpty() }
                            .forEach { words.add(it) }
                    } else {
                        words.add(content)
                    }
                }
            }

        return words
    }

    /**
     * Extracts unique tagged words from the text.
     *
     * Same as [extractTaggedWords] but returns a Set to remove duplicates.
     *
     * @param text The input text containing `<w>` tags
     * @param splitMultipleWords If true, splits content with spaces into separate words
     * @return Set of unique extracted words
     */
    fun extractUniqueTaggedWords(
        text: String,
        splitMultipleWords: Boolean = false
    ): Set<String> {
        return extractTaggedWords(text, splitMultipleWords).toSet()
    }

    private fun findNextSingleWordTagStart(text: String, startIndex: Int): Int {
        var searchIndex = startIndex
        while (searchIndex < text.length) {
            val candidateStart = text.indexOf('<', searchIndex)
            if (candidateStart == -1) return -1

            if (text.startsWith("<w>", candidateStart) || text.startsWith("</w>", candidateStart)) {
                searchIndex = candidateStart + 1
                continue
            }

            val candidateEnd = text.indexOf('>', candidateStart + 1)
            if (candidateEnd == -1) return candidateStart

            val content = text.substring(candidateStart + 1, candidateEnd)
            if (isSingleWordTag(content)) {
                return candidateStart
            }

            searchIndex = candidateStart + 1
        }

        return -1
    }

    private fun isSingleWordTag(content: String): Boolean {
        if (content.isEmpty()) return false
        if (content.startsWith("/")) return false
        if (content == "w") return false
        return content.none { it.isWhitespace() }
    }
}
