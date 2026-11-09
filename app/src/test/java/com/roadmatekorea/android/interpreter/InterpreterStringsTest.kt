package com.roadmatekorea.android.interpreter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** 통역 화면 문구와 문장 카드가 네 언어에 모두 있고, 카드마다 한국어 문장이 있는지 확인한다. */
class InterpreterStringsTest {

    private val resDir = File("src/main/res")
    private val folders = listOf("values", "values-fr", "values-es", "values-ko")

    private fun load(path: String): Map<String, String> {
        val file = File(resDir, path)
        assertTrue("파일 없음: $file", file.exists())
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            nodes.item(i).attributes.getNamedItem("name").nodeValue to nodes.item(i).textContent
        }
    }

    @Test
    fun screenTextsAndPhrases_existInAllFourLanguages() {
        val english = load("values/interpreter_strings.xml").keys
        folders.drop(1).forEach { folder ->
            assertEquals("$folder 키가 영어와 다름", english, load("$folder/interpreter_strings.xml").keys)
        }
        Phrases.all.forEach { phrase ->
            assertTrue("문장 카드 ${phrase.id} 없음", "phrase_${phrase.id}" in english)
        }
    }

    @Test
    fun everyPhrase_hasAKoreanLine() {
        val korean = load("values/interpreter_korean.xml")
        Phrases.all.forEach { phrase ->
            val text = korean["phrase_${phrase.id}_ko"]
            assertTrue("${phrase.id}의 한국어 없음", !text.isNullOrBlank() && Regex("[가-힣]").containsMatchIn(text))
        }
        assertEquals("쓰지 않는 한국어 문장", Phrases.all.map { "phrase_${it.id}_ko" }.toSet(), korean.keys)
    }

    @Test
    fun phrases_coverEverySituation_withUniqueIds() {
        Situation.entries.forEach { assertTrue("$it 상황의 카드 없음", Phrases.of(it).isNotEmpty()) }
        assertEquals(Phrases.all.size, Phrases.all.map { it.id }.toSet().size)
    }

    @Test
    fun french_usesNoBreakSpaceBeforeHighPunctuation() {
        load("values-fr/interpreter_strings.xml").forEach { (key, text) ->
            assertTrue("fr/$key: ! ? : ; 앞에 일반 공백", !Regex(""" [!?:;]""").containsMatchIn(text))
        }
    }
}
