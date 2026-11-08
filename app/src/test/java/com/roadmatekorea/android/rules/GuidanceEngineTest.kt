package com.roadmatekorea.android.rules

import com.roadmatekorea.android.profile.model.AppLanguage
import com.roadmatekorea.android.profile.model.DriverProfile
import com.roadmatekorea.android.profile.model.DrivingCountry
import com.roadmatekorea.android.profile.model.KoreaExperience
import com.roadmatekorea.android.profile.model.Personas
import com.roadmatekorea.android.rules.GuidanceId.ARRIVED
import com.roadmatekorea.android.rules.GuidanceId.LEFT_TURN_RULE
import com.roadmatekorea.android.rules.GuidanceId.LHD_INTRO
import com.roadmatekorea.android.rules.GuidanceId.LHD_MIRROR_CHECK
import com.roadmatekorea.android.rules.GuidanceId.RIGHT_SIDE_LEFT_TURN
import com.roadmatekorea.android.rules.GuidanceId.RIGHT_TURN_RULE
import com.roadmatekorea.android.rules.GuidanceId.SCHOOL_ZONE_AHEAD
import com.roadmatekorea.android.rules.GuidanceId.SCHOOL_ZONE_CROSSWALK
import com.roadmatekorea.android.rules.GuidanceId.SCHOOL_ZONE_SPEED_LIMIT
import com.roadmatekorea.android.rules.GuidanceId.WELCOME_EXPERIENCED
import com.roadmatekorea.android.rules.GuidanceId.WELCOME_FIRST_TIME
import com.roadmatekorea.android.rules.GuidanceId.WELCOME_RIGHT_SIDE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GuidanceEngineTest {

    private val clock = FakeClock()

    private fun engine(profile: DriverProfile) = GuidanceEngine(profile, clock)

    private val experienced = DriverProfile(AppLanguage.EN, DrivingCountry.OTHER, KoreaExperience.DROVE_IN_KOREA)
    private val otherLhd = DriverProfile(AppLanguage.EN, DrivingCountry.OTHER, KoreaExperience.FIRST_TIME_LEFT_HAND_DRIVE)

    private fun GuidanceEngine.send(vararg events: DriveEvent): List<Guidance> = events.flatMap { onEvent(it) }
    private fun List<Guidance>.ids() = map { it.id }

    private fun GuidanceEngine.startDriving() =
        send(DriveEvent.SessionStarted, DriveEvent.GearChanged(Gear.DRIVE))

    private fun schoolZone(id: String, remain: Int) =
        DriveEvent.CautionPointsUpdated(listOf(CautionPoint(id, CautionKind.SCHOOL_ZONE, remain)))

    private fun schoolCamera(id: String, limit: Int, remain: Int = 100) =
        DriveEvent.CameraAlertsUpdated(listOf(CameraAlert(id, CameraKind.SCHOOL_ZONE, limit, remain)))

    private fun tbt(vararg turns: Pair<TurnDirection, Int>) =
        DriveEvent.TurnInstructionsUpdated(turns.map { TurnInstruction(it.first, it.second) })

    // ---- 시작 안내 ----

    @Test
    fun nakamura_greetsWithRightSideThenLhdIntro() {
        assertEquals(listOf(WELCOME_RIGHT_SIDE, LHD_INTRO), engine(Personas.NAKAMURA).startDriving().ids())
    }

    @Test
    fun carlos_greetsWithFirstTimeOnly() {
        assertEquals(listOf(WELCOME_FIRST_TIME), engine(Personas.CARLOS).startDriving().ids())
    }

    @Test
    fun experienced_greetsBriefly() {
        assertEquals(listOf(WELCOME_EXPERIENCED), engine(experienced).startDriving().ids())
    }

    @Test
    fun otherCountryLhd_getsFirstTimeGreetingAndLhdIntro_withoutRightSide() {
        assertEquals(listOf(WELCOME_FIRST_TIME, LHD_INTRO), engine(otherLhd).startDriving().ids())
    }

    @Test
    fun greeting_alsoOnFirstDriveGear_andOnlyOncePerSession() {
        val e = engine(Personas.CARLOS)
        assertEquals(emptyList<Guidance>(), e.send(DriveEvent.GearChanged(Gear.PARK)))
        assertEquals(emptyList<Guidance>(), e.send(DriveEvent.GearChanged(Gear.UNKNOWN)))
        assertEquals(listOf(WELCOME_FIRST_TIME), e.send(DriveEvent.GearChanged(Gear.DRIVE)).ids())
        assertEquals(
            emptyList<Guidance>(),
            e.send(DriveEvent.GearChanged(Gear.PARK), DriveEvent.GearChanged(Gear.DRIVE), DriveEvent.SessionStarted),
        )
    }

    // ---- 어린이보호구역: 모든 사용자 ----

    @Test
    fun schoolZoneAhead_givenToEveryone_crosswalkDetailOnlyForDetailed() {
        assertEquals(
            listOf(Guidance(SCHOOL_ZONE_AHEAD, listOf(300)), Guidance(SCHOOL_ZONE_CROSSWALK)),
            engine(Personas.CARLOS).send(schoolZone("sz", 300)),
        )
        assertEquals(
            listOf(Guidance(SCHOOL_ZONE_AHEAD, listOf(300))),
            engine(experienced).send(schoolZone("sz", 300)),
        )
    }

    @Test
    fun schoolZoneAhead_onlyWithin300m_andOncePerPoint() {
        val e = engine(experienced)
        assertEquals(emptyList<Guidance>(), e.send(schoolZone("sz", 450)))
        assertEquals(listOf(Guidance(SCHOOL_ZONE_AHEAD, listOf(250))), e.send(schoolZone("sz", 260)))
        assertEquals(emptyList<Guidance>(), e.send(schoolZone("sz", 120), schoolZone("sz", 10)))
        assertEquals(listOf(Guidance(SCHOOL_ZONE_AHEAD, listOf(200))), e.send(schoolZone("sz2", 200)))
    }

    @Test
    fun nonSchoolCautionPoints_areIgnored() {
        val e = engine(experienced)
        val bump = DriveEvent.CautionPointsUpdated(listOf(CautionPoint("b", CautionKind.OTHER, 100)))
        assertEquals(emptyList<Guidance>(), e.send(bump))
    }

    @Test
    fun schoolZoneSpeedLimit_usesValueFromCamera_neverAFixedValue() {
        listOf(20, 30, 40, 50).forEach { limit ->
            assertEquals(
                listOf(Guidance(SCHOOL_ZONE_SPEED_LIMIT, listOf(limit))),
                engine(experienced).send(schoolCamera("cam", limit)),
            )
        }
    }

    @Test
    fun schoolZoneSpeedLimit_fromRoadLimitDropAfterAheadWarning() {
        val e = engine(experienced)
        e.send(DriveEvent.SpeedLimitChanged(50))
        e.send(schoolZone("sz", 300))
        clock.advanceSeconds(10)
        assertEquals(listOf(Guidance(SCHOOL_ZONE_SPEED_LIMIT, listOf(40))), e.send(DriveEvent.SpeedLimitChanged(40)))
        // 같은 구역에서 다시 말하지 않는다(카메라로 같은 값이 와도)
        assertEquals(emptyList<Guidance>(), e.send(schoolCamera("cam", 40), DriveEvent.SpeedLimitChanged(30)))
    }

    @Test
    fun schoolZoneSpeedLimit_notAnnouncedWithoutValue() {
        val e = engine(experienced)
        e.send(schoolZone("sz", 300))
        assertEquals(emptyList<Guidance>(), e.send(schoolCamera("cam", 0), DriveEvent.SpeedLimitChanged(0)))
    }

    @Test
    fun roadLimit_ignoredWhenNotLowerOrOutsideWindow() {
        val e = engine(experienced)
        e.send(DriveEvent.SpeedLimitChanged(30))
        e.send(schoolZone("sz", 300))
        assertEquals(emptyList<Guidance>(), e.send(DriveEvent.SpeedLimitChanged(50)))
        clock.advanceSeconds(91)
        assertEquals(emptyList<Guidance>(), e.send(DriveEvent.SpeedLimitChanged(20)))
    }

    @Test
    fun roadLimit_withoutAheadWarning_isNotSchoolZone() {
        assertEquals(emptyList<Guidance>(), engine(experienced).send(DriveEvent.SpeedLimitChanged(30)))
    }

    // ---- 회전 ----

    @Test
    fun rightSignal_withoutRoute_givesRightTurnRuleToDetailedOnly() {
        val carlos = engine(Personas.CARLOS).apply { startDriving() }
        assertEquals(listOf(RIGHT_TURN_RULE), carlos.send(DriveEvent.TurnSignalChanged(TurnSignal.RIGHT)).ids())
        val exp = engine(experienced).apply { startDriving() }
        assertEquals(emptyList<Guidance>(), exp.send(DriveEvent.TurnSignalChanged(TurnSignal.RIGHT)))
    }

    @Test
    fun leftSignal_nakamuraGetsRuleRightSideAndLhd_carlosGetsRuleOnly() {
        val nakamura = engine(Personas.NAKAMURA).apply { startDriving() }
        assertEquals(
            listOf(LEFT_TURN_RULE, RIGHT_SIDE_LEFT_TURN, LHD_MIRROR_CHECK),
            nakamura.send(DriveEvent.TurnSignalChanged(TurnSignal.LEFT)).ids(),
        )
        val carlos = engine(Personas.CARLOS).apply { startDriving() }
        assertEquals(listOf(LEFT_TURN_RULE), carlos.send(DriveEvent.TurnSignalChanged(TurnSignal.LEFT)).ids())
    }

    @Test
    fun noTurnGuidance_whenParked() {
        val e = engine(Personas.NAKAMURA)
        e.send(DriveEvent.GearChanged(Gear.PARK))
        assertEquals(
            emptyList<Guidance>(),
            e.send(DriveEvent.TurnSignalChanged(TurnSignal.LEFT), tbt(TurnDirection.RIGHT to 100)),
        )
    }

    @Test
    fun tbtTurnWithin150m_triggersOnce_evenAsDistanceShrinks() {
        val e = engine(Personas.CARLOS).apply { startDriving() }
        assertEquals(emptyList<Guidance>(), e.send(tbt(TurnDirection.RIGHT to 400)))
        assertEquals(listOf(RIGHT_TURN_RULE), e.send(tbt(TurnDirection.RIGHT to 150)).ids())
        clock.advanceSeconds(90)
        assertEquals(emptyList<Guidance>(), e.send(tbt(TurnDirection.RIGHT to 80), tbt(TurnDirection.RIGHT to 20)))
    }

    @Test
    fun tbtList_isUsedRegardlessOfOrder() {
        val e = engine(Personas.CARLOS).apply { startDriving() }
        // SDK가 가까운 순으로 주지 않아도 된다
        val unsorted = tbt(TurnDirection.LEFT to 900, TurnDirection.OTHER to 1500, TurnDirection.RIGHT to 120)
        assertEquals(listOf(RIGHT_TURN_RULE), e.send(unsorted).ids())
    }

    @Test
    fun tbtAndSignalForSameTurn_announceOnce() {
        val e = engine(Personas.CARLOS).apply { startDriving() }
        assertEquals(listOf(RIGHT_TURN_RULE), e.send(tbt(TurnDirection.RIGHT to 120)).ids())
        clock.advanceSeconds(5)
        assertEquals(emptyList<Guidance>(), e.send(DriveEvent.TurnSignalChanged(TurnSignal.RIGHT)))
    }

    @Test
    fun signalWithoutNearTurnOnActiveRoute_isLaneChange() {
        val nakamura = engine(Personas.NAKAMURA).apply { startDriving() }
        nakamura.send(tbt(TurnDirection.RIGHT to 600))
        assertEquals(listOf(LHD_MIRROR_CHECK), nakamura.send(DriveEvent.TurnSignalChanged(TurnSignal.LEFT)).ids())

        val carlos = engine(Personas.CARLOS).apply { startDriving() }
        carlos.send(tbt(TurnDirection.RIGHT to 600))
        assertEquals(emptyList<Guidance>(), carlos.send(DriveEvent.TurnSignalChanged(TurnSignal.LEFT)))
    }

    @Test
    fun signal_onlyOnChange() {
        val e = engine(Personas.CARLOS).apply { startDriving() }
        assertEquals(listOf(RIGHT_TURN_RULE), e.send(DriveEvent.TurnSignalChanged(TurnSignal.RIGHT)).ids())
        clock.advanceSeconds(120)
        assertEquals(emptyList<Guidance>(), e.send(DriveEvent.TurnSignalChanged(TurnSignal.RIGHT)))
        assertEquals(emptyList<Guidance>(), e.send(DriveEvent.TurnSignalChanged(TurnSignal.OFF)))
        assertEquals(listOf(RIGHT_TURN_RULE), e.send(DriveEvent.TurnSignalChanged(TurnSignal.RIGHT)).ids())
    }

    @Test
    fun turnRule_capsAtTwicePerDay() {
        val e = engine(Personas.CARLOS).apply { startDriving() }
        val said = (1..4).flatMap {
            clock.advanceSeconds(300)
            e.send(DriveEvent.TurnSignalChanged(TurnSignal.RIGHT), DriveEvent.TurnSignalChanged(TurnSignal.OFF))
        }
        assertEquals(listOf(RIGHT_TURN_RULE, RIGHT_TURN_RULE), said.ids())
    }

    // ---- 도착 ----

    @Test
    fun arrival_givenToEveryone() {
        assertEquals(listOf(ARRIVED), engine(experienced).send(DriveEvent.DestinationArrived).ids())
    }

    @Test
    fun arrival_oncePerSession_andAgainInNewSession() {
        val e = engine(experienced)
        assertEquals(listOf(ARRIVED), e.send(DriveEvent.DestinationArrived).ids())
        assertEquals(emptyList<Guidance>(), e.send(DriveEvent.DestinationArrived))
        clock.advanceSeconds(3600)
        assertEquals(emptyList<Guidance>(), e.send(DriveEvent.DestinationArrived))
        assertEquals(listOf(ARRIVED), engine(experienced).send(DriveEvent.DestinationArrived).ids())
    }

    // ---- 리뷰 회귀 테스트 ----

    @Test
    fun severalSchoolZonesAtOnce_remainingOnesAnnouncedLater() {
        val e = engine(experienced)
        val both = DriveEvent.CautionPointsUpdated(
            listOf(
                CautionPoint("A", CautionKind.SCHOOL_ZONE, 100),
                CautionPoint("B", CautionKind.SCHOOL_ZONE, 250),
            ),
        )
        assertEquals(listOf(Guidance(SCHOOL_ZONE_AHEAD, listOf(100))), e.send(both))
        clock.advanceSeconds(20)
        assertEquals(listOf(Guidance(SCHOOL_ZONE_AHEAD, listOf(100))), e.send(schoolZone("B", 100)))
        assertEquals(emptyList<Guidance>(), e.send(schoolZone("B", 50)))
    }

    @Test
    fun camerasOfTwoDifferentZones_eachAnnounceTheirLimit() {
        val e = engine(experienced)
        assertEquals(listOf(Guidance(SCHOOL_ZONE_SPEED_LIMIT, listOf(30))), e.send(schoolCamera("A", 30)))
        e.send(DriveEvent.CameraAlertsUpdated(emptyList()))
        clock.advanceSeconds(91)
        assertEquals(listOf(Guidance(SCHOOL_ZONE_SPEED_LIMIT, listOf(40))), e.send(schoolCamera("B", 40)))
        assertEquals(emptyList<Guidance>(), e.send(schoolCamera("B", 40)))
    }

    @Test
    fun cameraOfAnotherZoneWithin90s_withDifferentLimit_isAnnounced() {
        val e = engine(experienced)
        assertEquals(listOf(Guidance(SCHOOL_ZONE_SPEED_LIMIT, listOf(30))), e.send(schoolCamera("A", 30)))
        e.send(DriveEvent.CameraAlertsUpdated(emptyList()))
        clock.advanceSeconds(20)
        assertEquals(listOf(Guidance(SCHOOL_ZONE_SPEED_LIMIT, listOf(40))), e.send(schoolCamera("B", 40)))
    }

    @Test
    fun skippedCamera_isNotPermanentlyExcluded() {
        val e = engine(experienced)
        e.send(schoolCamera("A", 30))
        clock.advanceSeconds(20)
        assertEquals(emptyList<Guidance>(), e.send(schoolCamera("B", 30)))
        clock.advanceSeconds(71) // A 구역 시작에서 91초
        assertEquals(listOf(Guidance(SCHOOL_ZONE_SPEED_LIMIT, listOf(30))), e.send(schoolCamera("B", 30)))
        assertEquals(emptyList<Guidance>(), e.send(schoolCamera("B", 30)))
    }

    @Test
    fun cameraBeforeCautionPoint_bothWarningsGiven() {
        val e = engine(experienced)
        assertEquals(listOf(Guidance(SCHOOL_ZONE_SPEED_LIMIT, listOf(30))), e.send(schoolCamera("A", 30, remain = 250)))
        assertEquals(listOf(Guidance(SCHOOL_ZONE_AHEAD, listOf(200))), e.send(schoolZone("sz", 200)))
    }

    @Test
    fun secondCameraInSameZone_doesNotRepeatLimit() {
        val e = engine(experienced)
        e.send(schoolCamera("A", 30))
        clock.advanceSeconds(20)
        assertEquals(emptyList<Guidance>(), e.send(schoolCamera("A2", 30)))
    }

    @Test
    fun nearTurnReceivedWhileParked_isAnnouncedAfterDriving() {
        val e = engine(Personas.CARLOS)
        e.send(DriveEvent.GearChanged(Gear.PARK))
        assertEquals(emptyList<Guidance>(), e.send(tbt(TurnDirection.RIGHT to 100)))
        e.send(DriveEvent.GearChanged(Gear.DRIVE))
        assertEquals(listOf(RIGHT_TURN_RULE), e.send(tbt(TurnDirection.RIGHT to 90)).ids())
    }

    // ---- Same Road, Different Guidance ----

    @Test
    fun sameEvents_differentGuidanceForTwoPersonas() {
        fun run(profile: DriverProfile): List<GuidanceId> {
            val c = FakeClock()
            val e = GuidanceEngine(profile, c)
            val route = listOf(
                DriveEvent.GearChanged(Gear.DRIVE),
                DriveEvent.SpeedLimitChanged(50),
                schoolZone("sz", 300),
                DriveEvent.SpeedLimitChanged(30),
                schoolCamera("cam", 30),
                DriveEvent.CautionPointsUpdated(emptyList()),
                DriveEvent.SpeedLimitChanged(50),
                tbt(TurnDirection.RIGHT to 600),
                DriveEvent.TurnSignalChanged(TurnSignal.LEFT),
                DriveEvent.TurnSignalChanged(TurnSignal.OFF),
                tbt(TurnDirection.LEFT to 900, TurnDirection.RIGHT to 120),
                DriveEvent.TurnSignalChanged(TurnSignal.RIGHT),
                DriveEvent.TurnSignalChanged(TurnSignal.OFF),
                tbt(TurnDirection.LEFT to 120),
                DriveEvent.TurnSignalChanged(TurnSignal.LEFT),
                DriveEvent.TurnSignalChanged(TurnSignal.OFF),
                DriveEvent.DestinationArrived,
                DriveEvent.GearChanged(Gear.PARK),
            )
            return route.flatMap { c.advanceSeconds(6); e.onEvent(it) }.ids()
        }

        val nakamura = run(Personas.NAKAMURA)
        val carlos = run(Personas.CARLOS)

        assertEquals(
            listOf(
                WELCOME_RIGHT_SIDE, LHD_INTRO,
                SCHOOL_ZONE_AHEAD, SCHOOL_ZONE_CROSSWALK, SCHOOL_ZONE_SPEED_LIMIT,
                LHD_MIRROR_CHECK,
                RIGHT_TURN_RULE,
                LEFT_TURN_RULE, RIGHT_SIDE_LEFT_TURN,
                ARRIVED,
            ),
            nakamura,
        )
        assertEquals(
            listOf(
                WELCOME_FIRST_TIME,
                SCHOOL_ZONE_AHEAD, SCHOOL_ZONE_CROSSWALK, SCHOOL_ZONE_SPEED_LIMIT,
                RIGHT_TURN_RULE,
                LEFT_TURN_RULE,
                ARRIVED,
            ),
            carlos,
        )
        assertNotEquals(nakamura, carlos)
        // 카를로스에게는 통행 방향 비교, 좌핸들 적응 안내가 없다
        assertTrue(carlos.none { it in setOf(WELCOME_RIGHT_SIDE, RIGHT_SIDE_LEFT_TURN, LHD_INTRO, LHD_MIRROR_CHECK) })
    }

    @Test
    fun roundDistance_toNearest50m() {
        assertEquals(300, GuidanceEngine.roundDistance(300))
        assertEquals(300, GuidanceEngine.roundDistance(276))
        assertEquals(100, GuidanceEngine.roundDistance(120))
        assertEquals(50, GuidanceEngine.roundDistance(10))
    }
}
