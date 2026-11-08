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

    ARRIVED(Priority.INFO),
}

data class Guidance(val id: GuidanceId, val args: List<Int> = emptyList()) {
    val priority: Priority get() = id.priority
}
