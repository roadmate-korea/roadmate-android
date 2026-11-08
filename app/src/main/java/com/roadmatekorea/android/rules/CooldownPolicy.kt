package com.roadmatekorea.android.rules

fun interface Clock {
    fun nowMillis(): Long

    companion object {
        val SYSTEM = Clock { System.currentTimeMillis() }
    }
}

/**
 * 같은 안내가 너무 자주 나오지 않게 막는다.
 *
 * - [minIntervalMs]: 같은 안내 사이 최소 간격. TBT와 방향지시등이 같은 회전을 두 번 알리는 것도 이걸로 막는다.
 * - [maxPerDay]: 최근 24시간 안에 낼 수 있는 횟수. null이면 상한 없음.
 */
data class CooldownRule(val minIntervalMs: Long, val maxPerDay: Int?)

/**
 * 기록은 메모리에만 둔다(MVP). 앱을 다시 시작하거나 시연 세션을 새로 열면 초기화된다.
 * 여기 규칙이 없는 안내는 엔진이 지점 ID나 세션 단위로 직접 중복을 막는다.
 */
class CooldownPolicy(
    private val clock: Clock,
    private val rules: Map<GuidanceId, CooldownRule> = DEFAULT_RULES,
) {
    private val history = mutableMapOf<GuidanceId, MutableList<Long>>()

    /** 지금 낼 수 있으면 기록하고 true를 돌려준다. */
    fun tryAcquire(id: GuidanceId): Boolean {
        val rule = rules[id] ?: return true
        val now = clock.nowMillis()
        val times = history.getOrPut(id) { mutableListOf() }
        times.removeAll { now - it >= DAY_MS }
        val last = times.lastOrNull()
        if (last != null && now - last < rule.minIntervalMs) return false
        if (rule.maxPerDay != null && times.size >= rule.maxPerDay) return false
        times += now
        return true
    }

    companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
        private const val SECOND_MS = 1000L

        val DEFAULT_RULES: Map<GuidanceId, CooldownRule> = mapOf(
            GuidanceId.RIGHT_TURN_RULE to CooldownRule(minIntervalMs = 60 * SECOND_MS, maxPerDay = 2),
            GuidanceId.LEFT_TURN_RULE to CooldownRule(minIntervalMs = 60 * SECOND_MS, maxPerDay = 2),
            GuidanceId.RIGHT_SIDE_LEFT_TURN to CooldownRule(minIntervalMs = 120 * SECOND_MS, maxPerDay = 2),
            GuidanceId.LHD_MIRROR_CHECK to CooldownRule(minIntervalMs = 120 * SECOND_MS, maxPerDay = 2),
            GuidanceId.SCHOOL_ZONE_CROSSWALK to CooldownRule(minIntervalMs = 0, maxPerDay = 2),
            GuidanceId.ARRIVED to CooldownRule(minIntervalMs = 60 * SECOND_MS, maxPerDay = null),
        )
    }
}
