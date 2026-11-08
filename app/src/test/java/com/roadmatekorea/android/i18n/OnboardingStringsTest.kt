package com.roadmatekorea.android.i18n

import com.roadmatekorea.android.profile.model.DrivingCountry
import com.roadmatekorea.android.profile.model.KoreaExperience
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** 온보딩 문구가 4개 언어에 모두 있는지 XML로 직접 검사한다. */
class OnboardingStringsTest {

    private val folders = listOf("values", "values-fr", "values-es", "values-ko")

    private fun load(folder: String): Map<String, Pair<String, Boolean>> {
        val file = File("src/main/res/$folder/onboarding_strings.xml")
        assertTrue("파일 없음: $file", file.exists())
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            val n = nodes.item(i)
            val translatable = n.attributes.getNamedItem("translatable")?.nodeValue != "false"
            n.attributes.getNamedItem("name").nodeValue to (n.textContent to translatable)
        }
    }

    @Test
    fun translatableKeys_existInAllFourLanguages() {
        val base = load("values").filterValues { it.second }.keys
        folders.drop(1).forEach { folder ->
            assertEquals("$folder 키 목록", base, load(folder).keys)
        }
    }

    @Test
    fun allCountriesAndExperiences_haveLabels() {
        val keys = load("values").keys
        DrivingCountry.entries.forEach { assertTrue("country_${it.name.lowercase()}", "country_${it.name.lowercase()}" in keys) }
        KoreaExperience.entries.forEach { assertTrue("experience_${it.name.lowercase()}", "experience_${it.name.lowercase()}" in keys) }
    }

    @Test
    fun questions_matchScenarioWording() {
        val en = load("values")
        assertEquals("Choose your language", en.getValue("onboarding_language_title").first)
        assertEquals("Which country do you usually drive in?", en.getValue("onboarding_country_question").first)
        assertEquals("Have you driven in Korea before?", en.getValue("onboarding_experience_question").first)
        assertEquals("Otro país", load("values-es").getValue("country_other").first)
        assertEquals("기타 국가", load("values-ko").getValue("country_other").first)
    }

    @Test
    fun languageNames_areInTheirOwnLanguage_andNotTranslated() {
        val en = load("values")
        mapOf("en" to "English", "fr" to "Français", "es" to "Español", "ko" to "한국어").forEach { (code, name) ->
            assertEquals(name to false, en.getValue("language_name_$code"))
        }
    }

    @Test
    fun screenText_hasNoMiddleDotOrEmDash() {
        // 디자인 규칙: 화면 문구에서 가운데 점과 엠대시로 정보를 잇지 않는다(쉼표나 줄바꿈을 쓴다)
        folders.forEach { folder ->
            load(folder).forEach { (key, value) ->
                assertTrue("$folder $key: ${value.first}", '\u00B7' !in value.first && '\u2014' !in value.first)
            }
        }
    }

    @Test
    fun everyExperience_hasADescription() {
        val keys = load("values").keys
        KoreaExperience.entries.forEach { assertTrue("experience_desc_${it.name.lowercase()}" in keys) }
    }
}
