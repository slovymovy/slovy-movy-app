package com.slovy.slovymovyapp.data.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HtmlTagParserTest {

    @Test
    fun parseTextSegments_emptyString_returnsEmptyList() {
        val result = HtmlTagParser.parseTextSegments("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun parseTextSegments_noTags_returnsSingleUntaggedSegment() {
        val text = "Hello world"
        val result = HtmlTagParser.parseTextSegments(text)

        assertEquals(1, result.size)
        assertEquals("Hello world", result[0].text)
        assertEquals(false, result[0].isTagged)
    }

    @Test
    fun parseTextSegments_singleTag_returnsCorrectSegments() {
        val text = "Hello <w>world</w>!"
        val result = HtmlTagParser.parseTextSegments(text)

        assertEquals(3, result.size)
        assertEquals("Hello ", result[0].text)
        assertEquals(false, result[0].isTagged)
        assertEquals("world", result[1].text)
        assertEquals(true, result[1].isTagged)
        assertEquals("!", result[2].text)
        assertEquals(false, result[2].isTagged)
    }

    @Test
    fun parseTextSegments_multipleTags_returnsCorrectSegments() {
        val text = "Hello <w>world</w> and <w>universe</w>!"
        val result = HtmlTagParser.parseTextSegments(text)

        assertEquals(5, result.size)
        assertEquals("Hello ", result[0].text)
        assertEquals(false, result[0].isTagged)
        assertEquals("world", result[1].text)
        assertEquals(true, result[1].isTagged)
        assertEquals(" and ", result[2].text)
        assertEquals(false, result[2].isTagged)
        assertEquals("universe", result[3].text)
        assertEquals(true, result[3].isTagged)
        assertEquals("!", result[4].text)
        assertEquals(false, result[4].isTagged)
    }

    @Test
    fun parseTextSegments_nestedTags_treatsAsFlattened() {
        val text = "<w>outer <w>inner</w> text</w>"
        val result = HtmlTagParser.parseTextSegments(text)

        assertEquals(1, result.size)
        assertEquals("outer <w>inner</w> text", result[0].text)
        assertEquals(true, result[0].isTagged)
    }

    @Test
    fun parseTextSegments_unclosedTag_treatsAsUntaggedText() {
        val text = "Hello <w>unclosed world"
        val result = HtmlTagParser.parseTextSegments(text)

        assertEquals(2, result.size)
        assertEquals("Hello ", result[0].text)
        assertEquals(false, result[0].isTagged)
        assertEquals("<w>unclosed world", result[1].text)
        assertEquals(false, result[1].isTagged)
    }

    @Test
    fun parseTextSegments_closingTagWithoutOpening_treatsAsUntaggedText() {
        val text = "Hello </w>world"
        val result = HtmlTagParser.parseTextSegments(text)

        assertEquals(1, result.size)
        assertEquals("Hello </w>world", result[0].text)
        assertEquals(false, result[0].isTagged)
    }

    @Test
    fun parseTextSegments_emptyTag_createsEmptyTaggedSegment() {
        val text = "Hello <w></w>world"
        val result = HtmlTagParser.parseTextSegments(text)

        assertEquals(3, result.size)
        assertEquals("Hello ", result[0].text)
        assertEquals(false, result[0].isTagged)
        assertEquals("", result[1].text)
        assertEquals(true, result[1].isTagged)
        assertEquals("world", result[2].text)
        assertEquals(false, result[2].isTagged)
    }

    @Test
    fun parseTextSegments_adjacentTags_returnsCorrectSegments() {
        val text = "<w>first</w><w>second</w>"
        val result = HtmlTagParser.parseTextSegments(text)

        assertEquals(2, result.size)
        assertEquals("first", result[0].text)
        assertEquals(true, result[0].isTagged)
        assertEquals("second", result[1].text)
        assertEquals(true, result[1].isTagged)
    }

    @Test
    fun parseTextSegments_tagAtStart_returnsCorrectSegments() {
        val text = "<w>start</w> middle"
        val result = HtmlTagParser.parseTextSegments(text)

        assertEquals(2, result.size)
        assertEquals("start", result[0].text)
        assertEquals(true, result[0].isTagged)
        assertEquals(" middle", result[1].text)
        assertEquals(false, result[1].isTagged)
    }

    @Test
    fun parseTextSegments_tagAtEnd_returnsCorrectSegments() {
        val text = "middle <w>end</w>"
        val result = HtmlTagParser.parseTextSegments(text)

        assertEquals(2, result.size)
        assertEquals("middle ", result[0].text)
        assertEquals(false, result[0].isTagged)
        assertEquals("end", result[1].text)
        assertEquals(true, result[1].isTagged)
    }

    @Test
    fun parseTextSegments_singleWordTag_returnsTaggedSegment() {
        val text = "Say <hello> there"
        val result = HtmlTagParser.parseTextSegments(text)

        assertEquals(3, result.size)
        assertEquals("Say ", result[0].text)
        assertEquals(false, result[0].isTagged)
        assertEquals("hello", result[1].text)
        assertEquals(true, result[1].isTagged)
        assertEquals(" there", result[2].text)
        assertEquals(false, result[2].isTagged)
    }

    @Test
    fun parseTextSegments_multiWordTag_returnsTaggedSegment() {
        val text = "We need to <smash it> today"
        val result = HtmlTagParser.parseTextSegments(text)

        assertEquals(3, result.size)
        assertEquals("We need to ", result[0].text)
        assertEquals(false, result[0].isTagged)
        assertEquals("smash it", result[1].text)
        assertEquals(true, result[1].isTagged)
        assertEquals(" today", result[2].text)
        assertEquals(false, result[2].isTagged)
    }

    @Test
    fun extractTaggedWords_emptyString_returnsEmptyList() {
        val result = HtmlTagParser.extractTaggedWords("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun extractTaggedWords_noTags_returnsEmptyList() {
        val result = HtmlTagParser.extractTaggedWords("Hello world")
        assertTrue(result.isEmpty())
    }

    @Test
    fun extractTaggedWords_singleWord_returnsWord() {
        val result = HtmlTagParser.extractTaggedWords("Hello <w>world</w>!")
        assertEquals(listOf("world"), result)
    }

    @Test
    fun extractTaggedWords_multipleWords_returnsAllWords() {
        val result = HtmlTagParser.extractTaggedWords("Hello <w>world</w> and <w>universe</w>!")
        assertEquals(listOf("world", "universe"), result)
    }

    @Test
    fun extractTaggedWords_multipleWordsInOneTag_withoutSplit_returnsSingleEntry() {
        val result = HtmlTagParser.extractTaggedWords("<w>word1 word2</w>", splitMultipleWords = false)
        assertEquals(listOf("word1 word2"), result)
    }

    @Test
    fun extractTaggedWords_multipleWordsInOneTag_withSplit_returnsSeparateWords() {
        val result = HtmlTagParser.extractTaggedWords("<w>word1 word2</w>", splitMultipleWords = true)
        assertEquals(listOf("word1", "word2"), result)
    }

    @Test
    fun extractTaggedWords_emptyTag_ignored() {
        val result = HtmlTagParser.extractTaggedWords("Hello <w></w>world")
        assertTrue(result.isEmpty())
    }

    @Test
    fun extractTaggedWords_whitespaceOnlyTag_ignored() {
        val result = HtmlTagParser.extractTaggedWords("Hello <w>   </w>world")
        assertTrue(result.isEmpty())
    }

    @Test
    fun extractTaggedWords_trimsWhitespace() {
        val result = HtmlTagParser.extractTaggedWords("<w>  word  </w>")
        assertEquals(listOf("word"), result)
    }

    @Test
    fun extractTaggedWords_nestedTags_extractsOuterContent() {
        val result = HtmlTagParser.extractTaggedWords("<w>outer <w>inner</w> text</w>")
        assertEquals(listOf("outer <w>inner</w> text"), result)
    }

    @Test
    fun extractTaggedWords_unclosedTag_returnsEmpty() {
        val result = HtmlTagParser.extractTaggedWords("Hello <w>unclosed")
        assertTrue(result.isEmpty())
    }

    @Test
    fun extractUniqueTaggedWords_duplicates_returnsUniqueWords() {
        val result = HtmlTagParser.extractUniqueTaggedWords("<w>word</w> and <w>word</w>")
        assertEquals(setOf("word"), result)
    }

    @Test
    fun extractUniqueTaggedWords_differentWords_returnsAllWords() {
        val result = HtmlTagParser.extractUniqueTaggedWords("<w>first</w> and <w>second</w>")
        assertEquals(setOf("first", "second"), result)
    }

    @Test
    fun extractTaggedWords_complexExample_extractsCorrectly() {
        val text = "A <w>synonym</w> is a word that means the same as another <w>word</w>."
        val result = HtmlTagParser.extractTaggedWords(text)
        assertEquals(listOf("synonym", "word"), result)
    }

    @Test
    fun extractTaggedWords_singleWordTag_extractsWord() {
        val result = HtmlTagParser.extractTaggedWords("Use <hello> here")
        assertEquals(listOf("hello"), result)
    }

    @Test
    fun extractTaggedWords_multiWordTag_extractsPhrase() {
        val result = HtmlTagParser.extractTaggedWords("We need to <smash it> today")
        assertEquals(listOf("smash it"), result)
    }

    @Test
    fun extractTaggedWords_multipleSpacesInTag_withSplit_splitCorrectly() {
        val result = HtmlTagParser.extractTaggedWords("<w>word1    word2  word3</w>", splitMultipleWords = true)
        assertEquals(listOf("word1", "word2", "word3"), result)
    }

    @Test
    fun parseTextSegments_preservesIndices() {
        val text = "Hello <w>world</w>!"
        val result = HtmlTagParser.parseTextSegments(text)

        assertEquals(0, result[0].startIndex)
        assertEquals(6, result[0].endIndex)
        assertEquals(6, result[1].startIndex)
        assertEquals(18, result[1].endIndex)
        assertEquals(18, result[2].startIndex)
        assertEquals(19, result[2].endIndex)
    }
}
