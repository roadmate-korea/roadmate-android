package com.roadmatekorea.android.rules

import java.text.Normalizer
import java.util.Locale

/** 운전자가 음성으로 물을 수 있는 주제. */
enum class QuestionTopic {
    RIGHT_TURN,
    LEFT_TURN,
    SCHOOL_ZONE,
    BUS_LANE,
    SECTION_CHECK,
    SPEED_LIMIT,
    REPEAT,
    UNKNOWN,
}

/**
 * 음성 인식 결과 → 주제. 서버나 AI 없이 언어별 키워드로 기기 안에서 고른다.
 *
 * 운전자가 프로필 언어가 아닌 말(예: 프랑스어 프로필인데 영어)로 물을 수도 있어 모든 언어의 키워드를 함께 본다.
 * 한 질문에 여러 주제가 섞이면 아래 순서로 고른다: 다시 말해 줘 > 어린이보호구역 > 버스전용차로 > 구간 단속
 * > 제한 속도 > 우회전 > 좌회전. 예: "speed limit in a school zone"은 어린이보호구역.
 *
 * 라틴 문자는 악센트와 대소문자를 무시하고 단어 단위로 맞춘다("bright"는 "right"가 아니다).
 * 한글은 띄어쓰기가 일정하지 않아 부분 문자열로 맞춘다.
 */
object QuestionMatcher {

    private val rules: List<Pair<QuestionTopic, List<String>>> = listOf(
        QuestionTopic.REPEAT to listOf(
            "repeat", "again", "say that", "what did you say",
            "repete", "repeter", "encore", "redis",
            "repite", "repetir", "otra vez", "de nuevo",
            "다시", "반복",
        ),
        QuestionTopic.SCHOOL_ZONE to listOf(
            "school", "schools", "children", "kids",
            "ecole", "ecoles", "enfants", "scolaire",
            "escuela", "escolar", "escolares", "ninos",
            "어린이", "스쿨존", "학교",
        ),
        QuestionTopic.BUS_LANE to listOf(
            "bus", "buses",
            "autobus",
            "carril bus", "autobuses",
            "버스",
        ),
        QuestionTopic.SECTION_CHECK to listOf(
            "average speed", "section", "speed check",
            "vitesse moyenne", "troncon", "moyenne",
            "velocidad media", "velocidad promedio", "tramo", "promedio",
            "구간",
        ),
        QuestionTopic.SPEED_LIMIT to listOf(
            "speed limit", "limit", "how fast", "maximum speed",
            "limite", "limitation", "vitesse maximale", "vitesse maximum",
            "velocidad maxima", "limite de velocidad",
            "제한 속도", "제한속도", "속도 제한", "최고 속도",
        ),
        QuestionTopic.RIGHT_TURN to listOf(
            "right",
            "droite",
            "derecha",
            "우회전", "오른쪽",
        ),
        QuestionTopic.LEFT_TURN to listOf(
            "left",
            "gauche",
            "izquierda",
            "좌회전", "왼쪽",
        ),
    )

    fun match(text: String?): QuestionTopic {
        if (text.isNullOrBlank()) return QuestionTopic.UNKNOWN
        val normalized = normalize(text)
        val padded = " $normalized "
        for ((topic, keywords) in rules) {
            if (keywords.any { keyword -> contains(normalized, padded, normalize(keyword)) }) return topic
        }
        return QuestionTopic.UNKNOWN
    }

    private fun contains(normalized: String, padded: String, keyword: String): Boolean =
        if (keyword.any { it.isHangul() }) normalized.contains(keyword) else padded.contains(" $keyword ")

    /** 악센트 제거, 소문자, 문장부호를 공백으로. 한글은 그대로 둔다. */
    fun normalize(text: String): String {
        val stripped = Normalizer.normalize(text, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")
        return Normalizer.normalize(stripped, Normalizer.Form.NFC)
            .lowercase(Locale.ROOT)
            .replace(NON_WORD, " ")
            .trim()
            .replace(SPACES, " ")
    }

    private fun Char.isHangul() = this in '가'..'힣' || this in 'ㄱ'..'ㆎ'

    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val NON_WORD = Regex("[^\\p{L}\\p{Nd}]+")
    private val SPACES = Regex(" +")
}

/**
 * 주제 → 답할 안내. 우회전과 좌회전은 주행 중 안내 문장을 그대로 쓴다.
 *
 * @param speedLimitKmh NaviHelper가 준 지금 도로의 제한 속도. 없으면 모른다고 답한다(추정 금지).
 * @param lastGuidance 마지막으로 낸 주행 안내(답변 제외). "다시 말해 줘"에 쓴다.
 */
object QuestionAnswers {

    fun answer(topic: QuestionTopic, speedLimitKmh: Int?, lastGuidance: Guidance?): Guidance = when (topic) {
        QuestionTopic.RIGHT_TURN -> Guidance(GuidanceId.RIGHT_TURN_RULE)
        QuestionTopic.LEFT_TURN -> Guidance(GuidanceId.LEFT_TURN_RULE)
        QuestionTopic.SCHOOL_ZONE -> Guidance(GuidanceId.ANSWER_SCHOOL_ZONE)
        QuestionTopic.BUS_LANE -> Guidance(GuidanceId.ANSWER_BUS_LANE)
        QuestionTopic.SECTION_CHECK -> Guidance(GuidanceId.ANSWER_SECTION)
        QuestionTopic.SPEED_LIMIT ->
            if (speedLimitKmh != null && speedLimitKmh > 0) {
                Guidance(GuidanceId.ANSWER_SPEED_LIMIT, listOf(speedLimitKmh))
            } else {
                Guidance(GuidanceId.ANSWER_SPEED_LIMIT_UNKNOWN)
            }
        // 지금 속도가 들어간 경고는 지나면 숫자가 맞지 않는다. 다시 읽지 않고 어린이보호구역 안내로 답한다
        QuestionTopic.REPEAT -> when {
            lastGuidance == null -> Guidance(GuidanceId.ANSWER_HELP)
            lastGuidance.id in MOMENTARY -> Guidance(GuidanceId.ANSWER_SCHOOL_ZONE)
            else -> lastGuidance
        }
        QuestionTopic.UNKNOWN -> Guidance(GuidanceId.ANSWER_HELP)
    }

    /** 그 순간의 속도로 만든 경고. "다시 말해 줘"로 같은 숫자를 다시 읽지 않는다. */
    private val MOMENTARY = setOf(GuidanceId.SCHOOL_ZONE_APPROACH_FAST, GuidanceId.SCHOOL_ZONE_OVER_SPEED)

    /** 인식 결과가 없거나 오류일 때. */
    val notHeard = Guidance(GuidanceId.ANSWER_NOT_HEARD)

    fun isAnswer(id: GuidanceId) = id.name.startsWith("ANSWER_")
}
