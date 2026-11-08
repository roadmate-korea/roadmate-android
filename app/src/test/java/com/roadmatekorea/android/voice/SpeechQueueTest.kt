package com.roadmatekorea.android.voice

import com.roadmatekorea.android.rules.FakeClock
import com.roadmatekorea.android.rules.Guidance
import com.roadmatekorea.android.rules.GuidanceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class SpeechQueueTest {

    private val clock = FakeClock()
    private val queue = SpeechQueue(clock)

    private fun add(id: GuidanceId, vararg args: Int) = queue.enqueue(Guidance(id, args.toList()), id.name, Locale.US)

    @Test
    fun higherUrgencyFirst_thenFifoWithinTier() {
        add(GuidanceId.WELCOME_FIRST_TIME)
        add(GuidanceId.RIGHT_TURN_RULE)
        add(GuidanceId.LHD_INTRO)
        add(GuidanceId.SCHOOL_ZONE_AHEAD, 300)
        add(GuidanceId.SCHOOL_ZONE_CROSSWALK)
        val order = generateSequence { queue.poll()?.guidance?.id }.toList()
        assertEquals(
            listOf(
                GuidanceId.SCHOOL_ZONE_AHEAD,
                GuidanceId.SCHOOL_ZONE_CROSSWALK,
                GuidanceId.RIGHT_TURN_RULE,
                // 시작 인사와 적응 안내는 같은 단계라 들어온 순서대로
                GuidanceId.WELCOME_FIRST_TIME,
                GuidanceId.LHD_INTRO,
            ),
            order,
        )
    }

    @Test
    fun greetingThenLhdIntro_keepOrder() {
        add(GuidanceId.WELCOME_RIGHT_SIDE)
        add(GuidanceId.LHD_INTRO)
        assertEquals(GuidanceId.WELCOME_RIGHT_SIDE, queue.poll()?.guidance?.id)
        assertEquals(GuidanceId.LHD_INTRO, queue.poll()?.guidance?.id)
    }

    @Test
    fun expiredItem_doesNotBlockFreshSameGuidance() {
        add(GuidanceId.SCHOOL_ZONE_AHEAD, 300)
        clock.advanceSeconds(16)
        assertEquals(true, add(GuidanceId.SCHOOL_ZONE_AHEAD, 300))
        assertEquals(GuidanceId.SCHOOL_ZONE_AHEAD, queue.poll()?.guidance?.id)
        assertNull(queue.poll())
    }

    @Test
    fun expiredItems_areDropped_greetingIsKept() {
        add(GuidanceId.WELCOME_RIGHT_SIDE)
        add(GuidanceId.RIGHT_TURN_RULE)
        add(GuidanceId.SCHOOL_ZONE_AHEAD, 300)
        add(GuidanceId.LHD_INTRO)
        clock.advanceSeconds(16) // 회전 규칙(10초)과 안전 경고(15초)는 만료
        assertEquals(GuidanceId.WELCOME_RIGHT_SIDE, queue.poll()?.guidance?.id)
        clock.advanceSeconds(25) // 적응 안내(40초)도 만료
        assertNull(queue.poll())
        add(GuidanceId.WELCOME_FIRST_TIME)
        clock.advanceSeconds(3600) // 시작 안내는 버리지 않음
        assertEquals(GuidanceId.WELCOME_FIRST_TIME, queue.poll()?.guidance?.id)
    }

    @Test
    fun ruleExpiresAfter10s_safetyAfter15s() {
        add(GuidanceId.RIGHT_TURN_RULE)
        clock.advanceSeconds(10)
        assertEquals(GuidanceId.RIGHT_TURN_RULE, queue.poll()?.guidance?.id)
        add(GuidanceId.RIGHT_TURN_RULE)
        clock.advanceSeconds(11)
        assertNull(queue.poll())
        add(GuidanceId.SCHOOL_ZONE_AHEAD, 100)
        clock.advanceSeconds(15)
        assertEquals(GuidanceId.SCHOOL_ZONE_AHEAD, queue.poll()?.guidance?.id)
    }

    @Test
    fun sameGuidanceWaiting_isNotAddedTwice_butDifferentArgsAre() {
        add(GuidanceId.SCHOOL_ZONE_AHEAD, 300)
        assertFalse(add(GuidanceId.SCHOOL_ZONE_AHEAD, 300))
        add(GuidanceId.SCHOOL_ZONE_AHEAD, 200)
        assertEquals(2, queue.size)
    }
}
