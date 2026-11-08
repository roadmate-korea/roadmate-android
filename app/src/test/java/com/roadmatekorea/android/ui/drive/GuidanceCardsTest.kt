package com.roadmatekorea.android.ui.drive

import com.roadmatekorea.android.rules.GuidanceId
import com.roadmatekorea.android.rules.Priority
import com.roadmatekorea.android.ui.scene.SceneKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** 안내 카드: 모든 안내에 카드가 있고, 문구가 4개 언어에 있으며, 페르소나 규칙(국가 비교는 좌측통행 국가만)을 지킨다. */
class GuidanceCardsTest {

    private val folders = listOf("values", "values-fr", "values-es", "values-ko")

    private fun load(folder: String): Map<String, String> {
        val file = File("src/main/res/$folder/guidance_card_strings.xml")
        assertTrue("파일 없음: $file", file.exists())
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            val n = nodes.item(i)
            n.attributes.getNamedItem("name").nodeValue to n.textContent
        }
    }

    @Test
    fun everyGuidance_hasThreeCardLines_inAllFourLanguages() {
        val keys = GuidanceId.entries.flatMap { id -> listOf("kicker", "headline", "body").map { "card_${id.name.lowercase()}_$it" } }.toSet()
        folders.forEach { folder -> assertEquals("$folder 키 목록", keys, load(folder).keys) }
    }

    @Test
    fun guidanceWithArgs_showsTheValueOnTheCard_inEveryLanguage() {
        // 거리와 제한 속도는 NaviHelper 값이다. 카드에도 그 값이 보여야 한다(고정값 금지).
        listOf(GuidanceId.SCHOOL_ZONE_AHEAD, GuidanceId.SCHOOL_ZONE_SPEED_LIMIT, GuidanceId.BUS_LANE_AHEAD, GuidanceId.SECTION_START, GuidanceId.SECTION_OVER_AVERAGE).forEach { id ->
            folders.forEach { folder ->
                val strings = load(folder)
                val lines = listOf("kicker", "headline", "body").map { strings.getValue("card_${id.name.lowercase()}_$it") }
                assertTrue("$folder $id", lines.any { "%1\$d" in it })
                assertTrue("$folder $id 고정 30 금지", lines.none { Regex("\\b30\\b").containsMatchIn(it) })
            }
        }
    }

    @Test
    fun cardText_hasNoMiddleDotOrEmDash() {
        folders.forEach { folder ->
            load(folder).forEach { (k, v) -> assertTrue("$folder $k: $v", '·' !in v && '—' !in v) }
        }
    }

    @Test
    fun keepRightComparisonPicture_isOnlyForTheRightSideWelcome() {
        // 우측통행 비교 그림(왼쪽 차선 금지)은 좌측통행 국가 운전자용 안내에만. Carlos(기타 국가)에게는 나가지 않는다.
        GuidanceId.entries.filter { it != GuidanceId.WELCOME_RIGHT_SIDE }.forEach {
            assertTrue("$it", GuidanceCards.of(it).scene != SceneKind.KEEP_RIGHT)
        }
        assertEquals(SceneKind.KEEP_RIGHT, GuidanceCards.of(GuidanceId.WELCOME_RIGHT_SIDE).scene)
        assertNull(GuidanceCards.of(GuidanceId.WELCOME_FIRST_TIME).scene)
    }

    @Test
    fun safetyLook_followsPriority() {
        GuidanceId.entries.forEach { assertEquals("$it", it.priority == Priority.SAFETY, GuidanceCards.of(it).safety) }
    }
}
