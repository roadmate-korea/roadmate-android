package com.roadmatekorea.android.rules

import com.roadmatekorea.android.profile.model.AppLanguage
import com.roadmatekorea.android.profile.model.DriverProfile
import com.roadmatekorea.android.profile.model.DrivingCountry
import com.roadmatekorea.android.profile.model.KoreaExperience
import com.roadmatekorea.android.profile.model.Personas
import com.roadmatekorea.android.rules.GuidanceId.BUS_LANE_AHEAD
import com.roadmatekorea.android.rules.GuidanceId.SECTION_OVER_AVERAGE
import com.roadmatekorea.android.rules.GuidanceId.SECTION_START
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 버스전용차로(상세 안내 대상), 구간 단속 시작(상세 안내 대상), 구간 평균 속도 초과(모든 사용자). */
class BusLaneSectionTest {

    private val clock = FakeClock()
    private val experienced = DriverProfile(AppLanguage.EN, DrivingCountry.OTHER, KoreaExperience.DROVE_IN_KOREA)

    private fun engine(profile: DriverProfile) = GuidanceEngine(profile, clock).also {
        it.onEvent(DriveEvent.SessionStarted)
        it.onEvent(DriveEvent.GearChanged(Gear.DRIVE))
    }

    private fun busLane(vararg points: Pair<String, Int>) =
        DriveEvent.CautionPointsUpdated(points.map { CautionPoint(it.first, CautionKind.BUS_LANE, it.second) })

    private fun camera(id: String, kind: CameraKind, limit: Int, average: Int = 0) =
        DriveEvent.CameraAlertsUpdated(listOf(CameraAlert(id, kind, limit, 500, sectionAverageKmh = average)))

    @Test
    fun busLane_within300m_isAnnouncedOnceWithRoundedDistance_forDetailedDrivers() {
        val e = engine(Personas.CARLOS)
        assertEquals(emptyList<Guidance>(), e.onEvent(busLane("bl-1" to 420)))
        assertEquals(listOf(Guidance(BUS_LANE_AHEAD, listOf(300))), e.onEvent(busLane("bl-1" to 290)))
        assertEquals(emptyList<Guidance>(), e.onEvent(busLane("bl-1" to 150)))
    }

    @Test
    fun busLane_nearestOnly_whenSeveralArriveTogether() {
        val e = engine(Personas.NAKAMURA)
        assertEquals(listOf(Guidance(BUS_LANE_AHEAD, listOf(100))), e.onEvent(busLane("far" to 280, "near" to 110)))
    }

    @Test
    fun busLane_isNotForExperiencedDrivers() {
        assertTrue(engine(experienced).onEvent(busLane("bl-1" to 200)).isEmpty())
    }

    @Test
    fun busLane_andSchoolZone_inTheSameUpdate_bothSpoken_safetyIsKept() {
        val e = engine(Personas.CARLOS)
        val out = e.onEvent(
            DriveEvent.CautionPointsUpdated(
                listOf(CautionPoint("sz", CautionKind.SCHOOL_ZONE, 300), CautionPoint("bl", CautionKind.BUS_LANE, 200)),
            ),
        ).map { it.id }
        assertTrue(GuidanceId.SCHOOL_ZONE_AHEAD in out)
        assertTrue(BUS_LANE_AHEAD in out)
    }

    @Test
    fun busLane_ruleRepeatsAtMostTwiceADay() {
        val e = engine(Personas.CARLOS)
        val spoken = (1..4).sumOf { i ->
            clock.advanceSeconds(600)
            e.onEvent(busLane("bl-$i" to 200)).size
        }
        assertEquals(2, spoken)
    }

    @Test
    fun sectionStart_announcesNaviLimit_forDetailedDrivers_once() {
        val e = engine(Personas.NAKAMURA)
        assertEquals(listOf(Guidance(SECTION_START, listOf(100))), e.onEvent(camera("s1", CameraKind.SECTION_START, 100)))
        assertTrue(e.onEvent(camera("s1", CameraKind.SECTION_START, 100)).isEmpty())
        assertTrue(engine(experienced).onEvent(camera("s2", CameraKind.SECTION_START, 100)).isEmpty())
    }

    @Test
    fun sectionStart_withoutLimit_isIgnored() {
        assertTrue(engine(Personas.NAKAMURA).onEvent(camera("s1", CameraKind.SECTION_START, 0)).isEmpty())
    }

    @Test
    fun overAverage_warnsEveryone_withAverageAndLimit() {
        for (profile in listOf(Personas.NAKAMURA, Personas.CARLOS, experienced)) {
            val out = engine(profile).onEvent(camera("a1", CameraKind.SECTION_AVERAGE, 100, average = 112))
            assertEquals("$profile", listOf(Guidance(SECTION_OVER_AVERAGE, listOf(112, 100))), out)
        }
    }

    @Test
    fun overAverage_notWhenAtOrBelowLimit() {
        val e = engine(Personas.CARLOS)
        assertTrue(e.onEvent(camera("a1", CameraKind.SECTION_AVERAGE, 100, average = 100)).isEmpty())
        assertTrue(e.onEvent(camera("a1", CameraKind.SECTION_AVERAGE, 100, average = 0)).isEmpty())
    }

    @Test
    fun overAverage_repeatsAtMostEveryMinute_whileStillOver() {
        val e = engine(Personas.CARLOS)
        val first = e.onEvent(camera("a1", CameraKind.SECTION_AVERAGE, 100, average = 112))
        clock.advanceSeconds(20)
        val soon = e.onEvent(camera("a1", CameraKind.SECTION_AVERAGE, 100, average = 115))
        clock.advanceSeconds(45)
        val later = e.onEvent(camera("a1", CameraKind.SECTION_AVERAGE, 100, average = 110))
        assertEquals(1, first.size)
        assertTrue(soon.isEmpty())
        assertEquals(listOf(Guidance(SECTION_OVER_AVERAGE, listOf(110, 100))), later)
    }

    @Test
    fun nothingWhileParked_exceptNothingNew() {
        val e = GuidanceEngine(Personas.CARLOS, clock)
        e.onEvent(DriveEvent.GearChanged(Gear.PARK))
        // 정차 중에는 버스전용차로와 구간 단속 안내를 내지 않는다(실제로 지나가는 중이 아님)
        assertTrue(e.onEvent(busLane("bl" to 200)).isEmpty())
        assertTrue(e.onEvent(camera("s1", CameraKind.SECTION_START, 100)).isEmpty())
    }

    @Test
    fun overAverageWarning_comesBeforeTheSectionExplanation_inTheSameUpdate() {
        val e = engine(Personas.NAKAMURA)
        val out = e.onEvent(
            DriveEvent.CameraAlertsUpdated(
                listOf(
                    CameraAlert("s1", CameraKind.SECTION_START, 100, 300),
                    CameraAlert("a1", CameraKind.SECTION_AVERAGE, 100, 300, sectionAverageKmh = 120),
                ),
            ),
        ).map { it.id }
        assertEquals(listOf(SECTION_OVER_AVERAGE, SECTION_START), out)
    }

    @Test
    fun busLaneHeldBackByInterval_isSpokenLater_ifStillAhead() {
        val e = engine(Personas.CARLOS)
        assertEquals(1, e.onEvent(busLane("A" to 200)).size)
        clock.advanceSeconds(30)
        assertTrue("간격 제한 중", e.onEvent(busLane("B" to 280)).isEmpty())
        clock.advanceSeconds(35)
        assertEquals(listOf(Guidance(BUS_LANE_AHEAD, listOf(150))), e.onEvent(busLane("B" to 160)))
    }

    @Test
    fun sectionStartHeldBackByInterval_isSpokenLater_ifStillReported() {
        val e = engine(Personas.CARLOS)
        assertEquals(1, e.onEvent(camera("A", CameraKind.SECTION_START, 100)).size)
        clock.advanceSeconds(30)
        assertTrue(e.onEvent(camera("B", CameraKind.SECTION_START, 80)).isEmpty())
        clock.advanceSeconds(35)
        assertEquals(listOf(Guidance(SECTION_START, listOf(80))), e.onEvent(camera("B", CameraKind.SECTION_START, 80)))
    }

    @Test
    fun greetingOrder_isUnchanged_byTheSafetyFirstRule() {
        // 안전 경고만 앞으로 옮긴다. 시작 안내(환영 → 좌핸들)는 그대로.
        val e = GuidanceEngine(Personas.NAKAMURA, clock)
        assertEquals(listOf(GuidanceId.WELCOME_RIGHT_SIDE, GuidanceId.LHD_INTRO), e.onEvent(DriveEvent.SessionStarted).map { it.id })
    }
}
