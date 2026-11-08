package com.roadmatekorea.android.i18n

import com.roadmatekorea.android.profile.model.AppLanguage
import com.roadmatekorea.android.rules.GuidanceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 안내 문장 리소스를 XML로 직접 읽어 검사한다(Robolectric 없이).
 * 시작 문장 두 개는 기획서와 글자 하나까지 같아야 한다.
 */
class StringResourcesTest {

    private val resDir = File("src/main/res")
    private val scenarioDoc = File("../docs/mvp-scenario.md")

    private val folders = mapOf(
        AppLanguage.EN to "values",
        AppLanguage.FR to "values-fr",
        AppLanguage.ES to "values-es",
        AppLanguage.KO to "values-ko",
    )

    private val nakamuraGreeting =
        "Welcome to Korea. In Korea, vehicles drive on the right side of the road. Please take extra care at intersections."
    private val carlosGreeting =
        "¡Bienvenido a Corea! Como es tu primera vez conduciendo aquí, te ayudaremos a conocer las normas de tráfico locales."

    private fun keyOf(id: GuidanceId) = "guidance_" + id.name.lowercase()

    /** guidance_strings.xml을 읽어 키 → Android 이스케이프를 푼 문장으로 돌려준다. */
    private fun load(language: AppLanguage): Map<String, String> {
        val file = File(resDir, "${folders.getValue(language)}/guidance_strings.xml")
        assertTrue("파일 없음: $file", file.exists())
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            val node = nodes.item(i)
            node.attributes.getNamedItem("name").nodeValue to unescape(node.textContent)
        }
    }

    private fun unescape(raw: String): String = buildString {
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\' && i + 1 < raw.length) {
                when (val next = raw[i + 1]) {
                    'n' -> append('\n')
                    't' -> append('\t')
                    else -> append(next)
                }
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }

    @Test
    fun nakamuraGreeting_matchesScenarioExactly() {
        assertEquals(nakamuraGreeting, load(AppLanguage.EN).getValue(keyOf(GuidanceId.WELCOME_RIGHT_SIDE)))
    }

    @Test
    fun carlosGreeting_matchesScenarioExactly() {
        assertEquals(carlosGreeting, load(AppLanguage.ES).getValue(keyOf(GuidanceId.WELCOME_FIRST_TIME)))
    }

    @Test
    fun expectedGreetings_appearVerbatimInScenarioDocument() {
        val doc = scenarioDoc.readText()
        assertTrue("기획서에 Nakamura 문장이 없음", doc.contains("\"$nakamuraGreeting\""))
        assertTrue("기획서에 Carlos 문장이 없음", doc.contains("\"$carlosGreeting\""))
    }

    @Test
    fun everyGuidanceId_hasTextInAllFourLanguages_andNoExtraKeys() {
        val expected = GuidanceId.entries.map(::keyOf).toSet()
        AppLanguage.entries.forEach { language ->
            val strings = load(language)
            assertEquals("$language 키 목록", expected, strings.keys)
            strings.forEach { (key, text) -> assertTrue("$language/$key 비어 있음", text.isNotBlank()) }
        }
    }

    @Test
    fun formatArguments_matchAcrossLanguages() {
        val argPattern = Regex("""%(\d+)\$[ds]""")
        val english = load(AppLanguage.EN)
        AppLanguage.entries.forEach { language ->
            load(language).forEach { (key, text) ->
                assertEquals(
                    "$language/$key 형식 인자",
                    argPattern.findAll(english.getValue(key)).map { it.value }.toSet(),
                    argPattern.findAll(text).map { it.value }.toSet(),
                )
            }
        }
        val withArgs = setOf(keyOf(GuidanceId.SCHOOL_ZONE_AHEAD), keyOf(GuidanceId.SCHOOL_ZONE_SPEED_LIMIT))
        english.forEach { (key, text) ->
            assertEquals("$key 인자 유무", key in withArgs, argPattern.containsMatchIn(text))
        }
    }

    @Test
    fun english_formatsLikeAndroid() {
        val text = load(AppLanguage.EN).getValue(keyOf(GuidanceId.SCHOOL_ZONE_SPEED_LIMIT))
        assertEquals("School zone. The speed limit is 30 kilometers per hour.", String.format(Locale.US, text, 30))
    }

    @Test
    fun french_usesNoBreakSpaceBeforeHighPunctuation() {
        load(AppLanguage.FR).forEach { (key, text) ->
            assertTrue("fr/$key: ! ? : ; 앞에 일반 공백", !Regex(""" [!?:;]""").containsMatchIn(text))
        }
    }

    @Test
    fun guidanceStrings_mapEveryIdToDistinctResource() {
        val ids = GuidanceId.entries.map(GuidanceStrings::resId)
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun locales_matchTtsLocaleMapping() {
        assertEquals(Locale.US, AppLocales.localeOf(AppLanguage.EN))
        assertEquals(Locale.FRANCE, AppLocales.localeOf(AppLanguage.FR))
        assertEquals(Locale("es", "ES"), AppLocales.localeOf(AppLanguage.ES))
        assertEquals(Locale.KOREA, AppLocales.localeOf(AppLanguage.KO))
    }
}
