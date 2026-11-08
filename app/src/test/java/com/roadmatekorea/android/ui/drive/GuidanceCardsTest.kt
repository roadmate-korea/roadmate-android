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
            // Android 리소스의 \' 이스케이프를 푼다
            n.attributes.getNamedItem("name").nodeValue to n.textContent.replace("\\'", "'")
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
        listOf(GuidanceId.SCHOOL_ZONE_AHEAD, GuidanceId.SCHOOL_ZONE_SPEED_LIMIT).forEach { id ->
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

    @Test
    fun rightTurnSummary_keepsTheLegalConditions_inEveryLanguage() {
        // 우회전 신호등이 있으면 녹색 화살표만. 없을 때 적색이면 정지 후, 녹색 신호 차량을 막지 않을 때만, 건너거나 건너려는 보행자 앞 정지
        val required = mapOf(
            "values" to listOf("right-turn signal", "green arrow", "no such signal", "stop at the line", "green light", "about to cross"),
            "values-fr" to listOf("feu de virage à droite", "flèche verte", "sans ce feu", "arrêtez-vous d'abord", "au vert", "va traverser"),
            "values-es" to listOf("semáforo de giro a la derecha", "flecha verde", "sin ese semáforo", "detente primero", "luz verde", "vaya a cruzar"),
            "values-ko" to listOf("우회전 신호등이 있으면", "녹색 화살표", "신호등이 없고", "정지선에서 먼저 멈추", "녹색 신호", "건너려는"),
        )
        required.forEach { (folder, words) ->
            val strings = load(folder)
            val card = listOf("headline", "body").joinToString(" ") { strings.getValue("card_right_turn_rule_$it") }.lowercase()
            words.forEach { assertTrue("$folder: '$it' 없음 ($card)", it.lowercase() in card) }
        }
    }

    @Test
    fun withoutVoice_cardShowsTheFullSentence() {
        assertEquals("FULL", GuidanceCards.bodyText(voiced = false, summary = { "SUMMARY" }, fullText = "FULL"))
        assertEquals("SUMMARY", GuidanceCards.bodyText(voiced = true, summary = { "SUMMARY" }, fullText = "FULL"))
        assertEquals("SUMMARY", GuidanceCards.bodyText(voiced = false, summary = { "SUMMARY" }, fullText = null))
    }

    @Test
    fun schoolZonePicture_drawsANumberOnlyFromTheNaviLimit() {
        // 제한 속도 안내만 그림에 숫자를 넘긴다(인자 0번). 접근 경고와 횡단보도 안내는 숫자를 모르므로 그리지 않는다.
        assertEquals(0, GuidanceCards.of(GuidanceId.SCHOOL_ZONE_SPEED_LIMIT).sceneSpeedArg)
        assertNull(GuidanceCards.of(GuidanceId.SCHOOL_ZONE_AHEAD).sceneSpeedArg)
        assertNull(GuidanceCards.of(GuidanceId.SCHOOL_ZONE_CROSSWALK).sceneSpeedArg)
    }

    @Test
    fun leftTurnPictures_matchTheirMeaning() {
        // 녹색 좌회전 화살표 안내는 화살표 신호 그림, 좌회전 뒤 우측 진입 안내는 신호 없는 그림. 원형 녹색(비보호) 그림은 쓰지 않는다.
        assertEquals(SceneKind.LEFT_TURN_ARROW, GuidanceCards.of(GuidanceId.LEFT_TURN_RULE).scene)
        assertEquals(SceneKind.LEFT_TURN_KEEP_RIGHT, GuidanceCards.of(GuidanceId.RIGHT_SIDE_LEFT_TURN).scene)
        GuidanceId.entries.forEach { assertTrue("$it", GuidanceCards.of(it).scene != SceneKind.UNPROTECTED_LEFT) }
    }
}
