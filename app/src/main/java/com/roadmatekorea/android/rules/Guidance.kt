package com.roadmatekorea.android.rules

/** 위험도 순. 값이 클수록 먼저 말한다. */
enum class Priority { INFO, ADAPTATION, RULE, SAFETY }

/**
 * 안내 종류. 문장은 i18n 층에서 이 ID로 찾는다.
 * 형식 인자가 있는 안내는 [Guidance.args]에 값을 담는다.
 */
enum class GuidanceId(val priority: Priority) {
    WELCOME_RIGHT_SIDE(Priority.INFO),
    WELCOME_FIRST_TIME(Priority.INFO),
    WELCOME_EXPERIENCED(Priority.INFO),

    LHD_INTRO(Priority.ADAPTATION),
    LHD_MIRROR_CHECK(Priority.ADAPTATION),
    RIGHT_SIDE_LEFT_TURN(Priority.ADAPTATION),

    RIGHT_TURN_RULE(Priority.RULE),
    LEFT_TURN_RULE(Priority.RULE),

    /** args: [남은 거리(m)] */
    SCHOOL_ZONE_AHEAD(Priority.SAFETY),

    /** args: [제한 속도(km/h)] — 항상 NaviHelper가 준 값. */
    SCHOOL_ZONE_SPEED_LIMIT(Priority.SAFETY),
    SCHOOL_ZONE_CROSSWALK(Priority.SAFETY),

    /** args: [지금 속도(km/h), 제한 속도(km/h)]. 어린이보호구역에서 제한보다 빠를 때. 모든 사용자. */
    SCHOOL_ZONE_OVER_SPEED(Priority.SAFETY),

    /** args: [남은 거리(m)]. 상세 안내 대상만. */
    BUS_LANE_AHEAD(Priority.RULE),

    /** args: [구간 제한 속도(km/h)]. 상세 안내 대상만. */
    SECTION_START(Priority.RULE),

    /** args: [평균 속도, 제한 속도]. 과속이라 모든 사용자. */
    SECTION_OVER_AVERAGE(Priority.SAFETY),

    ARRIVED(Priority.INFO),

    // ---- 음성 질문에 대한 답(QuestionAnswers). 운전자가 직접 물었으므로 엔진 쿨다운을 거치지 않는다 ----
    ANSWER_SCHOOL_ZONE(Priority.RULE),
    ANSWER_BUS_LANE(Priority.RULE),
    ANSWER_SECTION(Priority.RULE),

    /** args: [제한 속도(km/h)] — NaviHelper가 준 지금 도로의 값. */
    ANSWER_SPEED_LIMIT(Priority.RULE),
    ANSWER_SPEED_LIMIT_UNKNOWN(Priority.RULE),
    ANSWER_HELP(Priority.RULE),
    ANSWER_NOT_HEARD(Priority.RULE),

    // ---- 상황 도움(정차 중 화면에서 운전자가 열 때만) ----
    /** 사고 뒤 할 일을 차례로 읽는다(프로필 언어). */
    HELP_ACCIDENT_STEPS(Priority.INFO),

    /** 주변 사람에게 들려줄 한국어 문장. 화면 버튼으로 한국어로 읽는다. */
    PHRASE_ACCIDENT_HELP(Priority.INFO),
}

data class Guidance(val id: GuidanceId, val args: List<Int> = emptyList()) {
    val priority: Priority get() = id.priority
}
