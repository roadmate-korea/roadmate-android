package com.roadmatekorea.android.rules

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeClock(var now: Long = 1_000_000L) : Clock {
    override fun nowMillis() = now
    fun advanceSeconds(seconds: Long) {
        now += seconds * 1000
    }
}

class CooldownPolicyTest {

    private val clock = FakeClock()
    private val policy = CooldownPolicy(clock)

    @Test
    fun turnRule_blockedWithinMinInterval() {
        assertTrue(policy.tryAcquire(GuidanceId.RIGHT_TURN_RULE))
        clock.advanceSeconds(59)
        assertFalse(policy.tryAcquire(GuidanceId.RIGHT_TURN_RULE))
        clock.advanceSeconds(1)
        assertTrue(policy.tryAcquire(GuidanceId.RIGHT_TURN_RULE))
    }

    @Test
    fun turnRule_atMostTwicePer24Hours() {
        assertTrue(policy.tryAcquire(GuidanceId.LEFT_TURN_RULE))
        clock.advanceSeconds(3600)
        assertTrue(policy.tryAcquire(GuidanceId.LEFT_TURN_RULE))
        clock.advanceSeconds(3600)
        assertFalse(policy.tryAcquire(GuidanceId.LEFT_TURN_RULE))
        // 첫 안내에서 24시간이 지나면 다시 한 번 가능
        clock.advanceSeconds(22 * 3600)
        assertTrue(policy.tryAcquire(GuidanceId.LEFT_TURN_RULE))
    }

    @Test
    fun blockedAttempt_isNotCounted() {
        assertTrue(policy.tryAcquire(GuidanceId.RIGHT_TURN_RULE))
        assertFalse(policy.tryAcquire(GuidanceId.RIGHT_TURN_RULE))
        assertFalse(policy.tryAcquire(GuidanceId.RIGHT_TURN_RULE))
        clock.advanceSeconds(60)
        assertTrue(policy.tryAcquire(GuidanceId.RIGHT_TURN_RULE))
    }

    @Test
    fun differentIds_doNotShareCooldown() {
        assertTrue(policy.tryAcquire(GuidanceId.RIGHT_TURN_RULE))
        assertTrue(policy.tryAcquire(GuidanceId.LEFT_TURN_RULE))
    }

    @Test
    fun safetyWarnings_haveNoCooldownRule() {
        repeat(10) {
            assertTrue(policy.tryAcquire(GuidanceId.SCHOOL_ZONE_AHEAD))
            assertTrue(policy.tryAcquire(GuidanceId.SCHOOL_ZONE_SPEED_LIMIT))
        }
    }
}
