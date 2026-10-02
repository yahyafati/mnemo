package com.yahyafati.mnemo.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class WordCountTest {
    @Test
    fun wordsAreRunsBetweenSpaces() {
        assertEquals(0, WordCount.count(""))
        assertEquals(0, WordCount.count(" \n\t "))
        assertEquals(4, WordCount.count("The  amygdala\nprocesses fear."))
    }

    @Test
    fun japaneseAndChineseCountTwoCharactersToAWord() {
        assertEquals(5, WordCount.count("私は毎朝コーヒーを"))
        assertEquals(500, WordCount.count("漢".repeat(1_000)))
        assertEquals(1, WordCount.count("漢")) // an odd one rounds up
    }

    @Test
    fun mixedTextAddsBoth() {
        // "iPhone" and "Mac" are two words; 日本語 is three characters, two words.
        assertEquals(4, WordCount.count("iPhone 日本語 Mac"))
        // A Latin run touching ideographs is still a word of its own.
        assertEquals(2, WordCount.count("Mac是"))
    }

    @Test
    fun ideographsOutsideTheBasicPlaneCount() {
        assertEquals(2, WordCount.count("𠀋".repeat(4))) // U+2000B, four of them
    }

    @Test
    fun hangulIsWrittenWithSpacesAndCountsAsWords() {
        assertEquals(3, WordCount.count("안녕하세요 반갑습니다 여러분"))
    }

    @Test
    fun sourceTextUsesTheSameCount() {
        assertEquals(WordCount.count("漢字 text"), SourceText("漢字 text").wordCount)
    }
}
