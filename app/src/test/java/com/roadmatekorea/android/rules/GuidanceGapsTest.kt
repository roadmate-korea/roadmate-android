package com.roadmatekorea.android.rules

import com.roadmatekorea.android.profile.model.AppLanguage
import com.roadmatekorea.android.profile.model.DriverProfile
import com.roadmatekorea.android.profile.model.DrivingCountry
import com.roadmatekorea.android.profile.model.KoreaExperience
import com.roadmatekorea.android.profile.model.Personas
import com.roadmatekorea.android.rules.GuidanceId.SCHOOL_ZONE_APPROACH_FAST
import com.roadmatekorea.android.rules.GuidanceId.SCHOOL_ZONE_SPEED_LIMIT
import com.roadmatekorea.android.rules.GuidanceId.TOLL_AHEAD
import org.junit.Assert.assertEquals
import org.junit.Test

/** roadmate-AI 규칙 팩(rules-v5)과 비교해 앱에 더한 안내: 구역 앞 과속, 요금소 하이패스. */
class GuidanceGapsTest {

    private val clock = FakeClock()
    private val experienced = DriverProfile(AppLanguage.EN, DrivingCountry.OTHER, KoreaExperience.DROVE_IN_KOREA)

    private fun engine(profile: DriverProfile = Personas.CARLOS) =
        GuidanceEngine(profile, clock).also { it.onEvent(DriveEvent.GearChanged(Gear.DRIVE)) }

    private fun GuidanceEngine.send(vararg events: DriveEvent) = events.flatMap { onEvent(it) }

    private fun schoolCamera(limit: Int, remain: Int = 250) =
        DriveEvent.CameraAlertsUpdated(listOf(CameraAlert("cam", CameraKind.SCHOOL_ZONE, limit, remain)))

    private fun toll(meters: Int) = DriveEvent.TurnInstructionsUpdated(listOf(TurnInstruction(TurnDirection.TOLL, meters)))

    @Test
    fun schoolZoneAhead_alreadyTooFast_saysLimitAndSpeed_forEveryone() {
        for (profile in listOf(Personas.CARLOS, experienced)) {
            val e = engine(profile)
            e.send(DriveEvent.SpeedLimitChanged(50), DriveEvent.VehicleSpeedChanged(45))
            assertEquals(listOf(Guidance(SCHOOL_ZONE_APPROACH_FAST, listOf(45, 30))), e.send(schoolCamera(30)))
        }
    }

    @Test
    fun schoolZoneAhead_slowEnough_saysOnlyTheLimit() {
        val e = engine()
        e.send(DriveEvent.SpeedLimitChanged(50), DriveEvent.VehicleSpeedChanged(31))
        assertEquals("여유(2km/h) 안이면 과속으로 보지 않음", listOf(Guidance(SCHOOL_ZONE_SPEED_LIMIT, listOf(30))), e.send(schoolCamera(30)))
    }

    @Test
    fun schoolZoneAhead_unknownSpeed_orParked_saysOnlyTheLimit() {
        val unknown = engine()
        unknown.send(DriveEvent.SpeedLimitChanged(50))
        assertEquals(listOf(Guidance(SCHOOL_ZONE_SPEED_LIMIT, listOf(30))), unknown.send(schoolCamera(30)))

        val parked = engine()
        parked.send(DriveEvent.SpeedLimitChanged(50), DriveEvent.VehicleSpeedChanged(45), DriveEvent.GearChanged(Gear.PARK))
        assertEquals(listOf(Guidance(SCHOOL_ZONE_SPEED_LIMIT, listOf(30))), parked.send(schoolCamera(30)))
    }

    @Test
    fun tollAhead_onceWhenApproaching_forDetailedDrivers() {
        val e = engine()
        assertEquals(emptyList<Guidance>(), e.send(toll(900)))
        assertEquals(listOf(Guidance(TOLL_AHEAD, listOf(400))), e.send(toll(420)))
        assertEquals("다가오는 동안 다시 말하지 않음", emptyList<Guidance>(), e.send(toll(300), toll(100)))
    }

    @Test
    fun tollAhead_notForExperiencedDrivers_norWhileParked() {
        assertEquals(emptyList<Guidance>(), engine(experienced).send(toll(400)))
        val parked = engine()
        parked.send(DriveEvent.GearChanged(Gear.PARK))
        assertEquals(emptyList<Guidance>(), parked.send(toll(400)))
    }

    @Test
    fun tollAhead_atMostTwiceADay() {
        val e = engine()
        repeat(3) {
            e.send(toll(400))
            e.send(DriveEvent.TurnInstructionsUpdated(emptyList()))
            clock.advanceSeconds(120)
        }
        assertEquals(emptyList<Guidance>(), e.send(toll(400)))
    }

    @Test
    fun aTollIsNotATurn_soTheTurnSignalNearItIsALaneChange() {
        val e = engine(Personas.NAKAMURA)
        e.send(toll(100))
        clock.advanceSeconds(1)
        val out = e.send(DriveEvent.TurnSignalChanged(TurnSignal.LEFT)).map { it.id }
        assertEquals("요금소는 회전이 아님: 좌측 깜빡이는 차선 변경", listOf(GuidanceId.LHD_MIRROR_CHECK), out)
    }
}
