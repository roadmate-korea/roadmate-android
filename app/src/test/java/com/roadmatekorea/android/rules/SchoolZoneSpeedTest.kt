package com.roadmatekorea.android.rules

import com.roadmatekorea.android.profile.model.AppLanguage
import com.roadmatekorea.android.profile.model.DriverProfile
import com.roadmatekorea.android.profile.model.DrivingCountry
import com.roadmatekorea.android.profile.model.KoreaExperience
import com.roadmatekorea.android.profile.model.Personas
import com.roadmatekorea.android.rules.GuidanceId.SCHOOL_ZONE_OVER_SPEED
import com.roadmatekorea.android.rules.GuidanceId.SCHOOL_ZONE_SPEED_LIMIT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 차량 속도에 따른 어린이보호구역 안내: 제한보다 빠르면 "지금 몇 km, 제한 몇", 느리면 더 말하지 않음. 모든 사용자. */
class SchoolZoneSpeedTest {

    private val clock = FakeClock()
    private val experienced = DriverProfile(AppLanguage.EN, DrivingCountry.OTHER, KoreaExperience.DROVE_IN_KOREA)

    private fun engine(profile: DriverProfile = Personas.CARLOS) = GuidanceEngine(profile, clock).also {
        it.onEvent(DriveEvent.SessionStarted)
        it.onEvent(DriveEvent.GearChanged(Gear.DRIVE))
        it.onEvent(DriveEvent.SpeedLimitChanged(50))
    }

    private fun GuidanceEngine.enterZone() =
        onEvent(DriveEvent.CautionPointsUpdated(listOf(CautionPoint("sz", CautionKind.SCHOOL_ZONE, 300))))

    private fun GuidanceEngine.speed(kmh: Int) = onEvent(DriveEvent.VehicleSpeedChanged(kmh))

    private fun ids(list: List<Guidance>) = list.map { it.id }

    @Test
    fun alreadyFast_whenLimitIsKnown_saysSpeedAndLimitInsteadOfLimitOnly_forEveryone() {
        listOf(Personas.NAKAMURA, Personas.CARLOS, experienced).forEach { profile ->
            val e = engine(profile)
            e.speed(45)
            e.enterZone()
            assertEquals("$profile", listOf(Guidance(SCHOOL_ZONE_OVER_SPEED, listOf(45, 30))), e.onEvent(DriveEvent.SpeedLimitChanged(30)))
        }
    }

    @Test
    fun cameraAhead_saysLimitOnly_thenWarnsWhenTheRoadLimitConfirmsEntry() {
        val e = engine()
        e.speed(41)
        e.enterZone()
        // 지금 도로는 아직 50이라 "지금 41, 제한 30"은 틀린 말이다. 앞쪽 제한만 알린다.
        val out = e.onEvent(DriveEvent.CameraAlertsUpdated(listOf(CameraAlert("c", CameraKind.SCHOOL_ZONE, 30, 100))))
        assertEquals(listOf(Guidance(SCHOOL_ZONE_SPEED_LIMIT, listOf(30))), out)
        // 도로 제한이 30이 되는 순간(진입) 지금 속도로 판단
        assertEquals(listOf(Guidance(SCHOOL_ZONE_OVER_SPEED, listOf(41, 30))), e.onEvent(DriveEvent.SpeedLimitChanged(30)))
    }

    @Test
    fun cameraOnly_withoutRoadLimit_usesTheCameraLimit() {
        val e = GuidanceEngine(Personas.CARLOS, clock).also { it.onEvent(DriveEvent.GearChanged(Gear.DRIVE)) }
        e.speed(41)
        val out = e.onEvent(DriveEvent.CameraAlertsUpdated(listOf(CameraAlert("c", CameraKind.SCHOOL_ZONE, 30, 100))))
        assertEquals(listOf(Guidance(SCHOOL_ZONE_OVER_SPEED, listOf(41, 30))), out)
    }

    @Test
    fun slowEnough_orUnknownSpeed_keepsTheLimitOnlyGuidance() {
        val slow = engine().also { it.speed(28); it.enterZone() }
        assertEquals(listOf(Guidance(SCHOOL_ZONE_SPEED_LIMIT, listOf(30))), slow.onEvent(DriveEvent.SpeedLimitChanged(30)))
        val unknown = engine().also { it.enterZone() }
        assertEquals(listOf(Guidance(SCHOOL_ZONE_SPEED_LIMIT, listOf(30))), unknown.onEvent(DriveEvent.SpeedLimitChanged(30)))
    }

    @Test
    fun withinMargin_isNotOverSpeed() {
        val e = engine().also { it.speed(30 + GuidanceEngine.OVER_SPEED_MARGIN_KMH); it.enterZone() }
        assertEquals(listOf(SCHOOL_ZONE_SPEED_LIMIT), ids(e.onEvent(DriveEvent.SpeedLimitChanged(30))))
        assertTrue(e.speed(30 + GuidanceEngine.OVER_SPEED_MARGIN_KMH).isEmpty())
    }

    @Test
    fun speedingUpInsideZone_warnsAtOnce_thenEvery30sWhileStillOver() {
        val e = engine().also { it.speed(25); it.enterZone(); it.onEvent(DriveEvent.SpeedLimitChanged(30)) }
        assertEquals(listOf(Guidance(SCHOOL_ZONE_OVER_SPEED, listOf(38, 30))), e.speed(38))
        // 계속 빠르면 30초 동안은 다시 말하지 않고, 30초가 지나면 지금 속도로 다시 경고한다
        clock.advanceSeconds(10)
        assertTrue(e.speed(40).isEmpty())
        clock.advanceSeconds(19)
        assertTrue(e.speed(36).isEmpty())
        clock.advanceSeconds(1)
        assertEquals(listOf(Guidance(SCHOOL_ZONE_OVER_SPEED, listOf(37, 30))), e.speed(37))
        // 제한 아래로 줄였다가 다시 넘으면 최소 간격(20초) 뒤 다시 경고
        assertTrue(e.speed(29).isEmpty())
        clock.advanceSeconds(20)
        assertEquals(listOf(Guidance(SCHOOL_ZONE_OVER_SPEED, listOf(35, 30))), e.speed(35))
    }

    @Test
    fun rapidReSpeeding_isLimitedByCooldown() {
        val e = engine().also { it.speed(25); it.enterZone(); it.onEvent(DriveEvent.SpeedLimitChanged(30)) }
        assertEquals(1, e.speed(38).size)
        e.speed(29)
        clock.advanceSeconds(5)
        assertTrue(e.speed(38).isEmpty())
        e.speed(29)
        clock.advanceSeconds(20)
        assertEquals(1, e.speed(38).size)
    }

    @Test
    fun noWarning_outsideZone_beforeLimitIsKnown_orWhenParked() {
        val e = engine()
        assertTrue(e.speed(80).isEmpty())
        e.enterZone()
        // 구역 접근 중이지만 제한 속도를 아직 모름
        assertTrue(e.speed(70).isEmpty())
        // 구역이 끝나면(유효 시간 경과) 말하지 않는다
        e.onEvent(DriveEvent.SpeedLimitChanged(30))
        e.speed(20)
        clock.advanceSeconds(GuidanceEngine.SCHOOL_ZONE_LIMIT_WINDOW_MS / 1000 + 1)
        assertTrue(e.speed(60).isEmpty())

        val parked = engine().also { it.speed(20); it.enterZone(); it.onEvent(DriveEvent.SpeedLimitChanged(30)) }
        parked.onEvent(DriveEvent.GearChanged(Gear.PARK))
        assertTrue(parked.speed(40).isEmpty())
    }

    @Test
    fun leavingZone_whenRoadLimitRisesAgain_stopsOverSpeedWarnings() {
        val e = engine().also { it.speed(25); it.enterZone(); it.onEvent(DriveEvent.SpeedLimitChanged(30)) }
        assertTrue(e.onEvent(DriveEvent.SpeedLimitChanged(50)).isEmpty())
        assertTrue(e.speed(45).isEmpty())
    }

    @Test
    fun limitDropsInsideZone_40then30_judgesAndSaysTheNewLimit() {
        val e = engine().also { it.speed(25); it.enterZone() }
        assertEquals(listOf(Guidance(SCHOOL_ZONE_SPEED_LIMIT, listOf(40))), e.onEvent(DriveEvent.SpeedLimitChanged(40)))
        assertEquals(listOf(Guidance(SCHOOL_ZONE_SPEED_LIMIT, listOf(30))), e.onEvent(DriveEvent.SpeedLimitChanged(30)))
        assertEquals(listOf(Guidance(SCHOOL_ZONE_OVER_SPEED, listOf(38, 30))), e.speed(38))
    }

    @Test
    fun cameraAnnouncesFutureLimit_repeatedCurrentRoadLimitBeforeEntry_doesNotEndZone() {
        val e = engine().also { it.speed(25); it.enterZone() }
        val cam = DriveEvent.CameraAlertsUpdated(listOf(CameraAlert("c", CameraKind.SCHOOL_ZONE, 30, 200)))
        assertEquals(listOf(Guidance(SCHOOL_ZONE_SPEED_LIMIT, listOf(30))), e.onEvent(cam))
        // 아직 지금 도로의 제한(50)이 반복해서 온다
        assertTrue(e.onEvent(DriveEvent.SpeedLimitChanged(50)).isEmpty())
        e.onEvent(DriveEvent.CautionPointsUpdated(listOf(CautionPoint("sz", CautionKind.SCHOOL_ZONE, 100))))
        // 구역에 들어옴: 이미 말한 30이라 다시 말하지 않는다
        assertTrue(e.onEvent(DriveEvent.SpeedLimitChanged(30)).isEmpty())
        assertEquals(listOf(Guidance(SCHOOL_ZONE_OVER_SPEED, listOf(45, 30))), e.speed(45))
        // 들어온 뒤 제한이 다시 오르면 구역을 벗어난 것
        e.speed(25)
        e.onEvent(DriveEvent.SpeedLimitChanged(50))
        assertTrue(e.speed(45).isEmpty())
    }

    @Test
    fun longStayInZone_keepsWatching_whileZoneEvidenceKeepsComing() {
        val e = engine().also { it.speed(20); it.enterZone(); it.onEvent(DriveEvent.SpeedLimitChanged(30)) }
        repeat(4) {
            clock.advanceSeconds(60)
            // NaviHelper는 같은 도로 제한을 계속 보낸다
            e.onEvent(DriveEvent.SpeedLimitChanged(30))
        }
        assertEquals(listOf(Guidance(SCHOOL_ZONE_OVER_SPEED, listOf(45, 30))), e.speed(45))
    }

    @Test
    fun longWaitBeforeZone_sameApproachPointKeepsZone() {
        val e = engine().also { it.speed(0); it.enterZone(); it.onEvent(DriveEvent.SpeedLimitChanged(30)) }
        clock.advanceSeconds(91)
        e.onEvent(DriveEvent.CautionPointsUpdated(listOf(CautionPoint("sz", CautionKind.SCHOOL_ZONE, 80))))
        assertEquals(listOf(Guidance(SCHOOL_ZONE_OVER_SPEED, listOf(45, 30))), e.speed(45))
    }

    @Test
    fun unknownSpeed_clearsTheOldValue() {
        val e = engine().also { it.speed(45); it.enterZone() }
        assertTrue(e.onEvent(DriveEvent.VehicleSpeedChanged(null)).isEmpty())
        assertEquals(listOf(Guidance(SCHOOL_ZONE_SPEED_LIMIT, listOf(30))), e.onEvent(DriveEvent.SpeedLimitChanged(30)))
    }

    @Test
    fun repeatedSameSpeed_afterCooldown_warnsAgain() {
        // 실제 어댑터는 같은 속도도 1초마다 다시 보낸다
        val e = engine().also { it.speed(25); it.enterZone(); it.onEvent(DriveEvent.SpeedLimitChanged(30)) }
        assertEquals(1, e.speed(38).size)
        e.speed(29)
        clock.advanceSeconds(5)
        assertTrue(e.speed(38).isEmpty())
        clock.advanceSeconds(16)
        assertEquals(listOf(Guidance(SCHOOL_ZONE_OVER_SPEED, listOf(38, 30))), e.speed(38))
    }

    @Test
    fun laterCameraWithAnotherLimit_doesNotOverrideTheConfirmedRoadLimit() {
        val e = engine().also { it.speed(25); it.enterZone(); it.onEvent(DriveEvent.SpeedLimitChanged(40)); it.onEvent(DriveEvent.SpeedLimitChanged(30)) }
        assertTrue(e.onEvent(DriveEvent.CameraAlertsUpdated(listOf(CameraAlert("c40", CameraKind.SCHOOL_ZONE, 40, 100)))).isEmpty())
        assertEquals(listOf(Guidance(SCHOOL_ZONE_OVER_SPEED, listOf(38, 30))), e.speed(38))
    }

    @Test
    fun lateApproachPoint_keepsTheLimitLearnedFromCameraAndRoad() {
        val e = engine().also { it.speed(25) }
        val cam = { m: Int -> DriveEvent.CameraAlertsUpdated(listOf(CameraAlert("cam", CameraKind.SCHOOL_ZONE, 30, m))) }
        e.onEvent(cam(200))
        e.onEvent(DriveEvent.SpeedLimitChanged(30))
        e.onEvent(DriveEvent.CautionPointsUpdated(listOf(CautionPoint("z", CautionKind.SCHOOL_ZONE, 100))))
        e.onEvent(cam(80))
        e.onEvent(DriveEvent.SpeedLimitChanged(30))
        assertEquals(listOf(Guidance(SCHOOL_ZONE_OVER_SPEED, listOf(45, 30))), e.speed(45))
    }

    @Test
    fun sameCameraBackAfterExpiry_revivesWatchingSilently() {
        val e = engine().also { it.speed(25) }
        val cam = { m: Int -> DriveEvent.CameraAlertsUpdated(listOf(CameraAlert("cam", CameraKind.SCHOOL_ZONE, 30, m))) }
        assertEquals(listOf(Guidance(SCHOOL_ZONE_SPEED_LIMIT, listOf(30))), e.onEvent(cam(200)))
        clock.advanceSeconds(91)
        assertTrue(e.onEvent(cam(80)).isEmpty())
        e.onEvent(DriveEvent.SpeedLimitChanged(30))
        assertEquals(listOf(Guidance(SCHOOL_ZONE_OVER_SPEED, listOf(45, 30))), e.speed(45))
    }

    @Test
    fun overSpeedWarningLostInTheVoiceQueue_isRebuiltFromTheCurrentSpeed() {
        // 음성 큐가 오래 기다린 경고를 버려도, 계속 빠르면 30초 안에 지금 속도로 다시 나온다
        val e = engine().also { it.speed(25); it.enterZone(); it.onEvent(DriveEvent.SpeedLimitChanged(30)) }
        assertEquals(1, e.speed(45).size)
        clock.advanceSeconds(16)
        assertTrue(e.speed(45).isEmpty())
        clock.advanceSeconds(14)
        assertEquals(listOf(Guidance(SCHOOL_ZONE_OVER_SPEED, listOf(45, 30))), e.speed(45))
    }

    @Test
    fun firstCameraWithHigherLimitThanTheKnownRoad_usesTheRoadLimitOnceItConfirms() {
        val e = GuidanceEngine(Personas.CARLOS, clock).also {
            it.onEvent(DriveEvent.GearChanged(Gear.DRIVE)); it.onEvent(DriveEvent.SpeedLimitChanged(30)); it.speed(25)
        }
        assertEquals(
            listOf(Guidance(SCHOOL_ZONE_SPEED_LIMIT, listOf(40))),
            e.onEvent(DriveEvent.CameraAlertsUpdated(listOf(CameraAlert("c40", CameraKind.SCHOOL_ZONE, 40, 100)))),
        )
        // 카메라 값(40)으로는 판정하지 않는다
        assertTrue(e.speed(38).isEmpty())
        // 도로 제한(30)이 다시 오면 그 값이 구역 제한이 되고 지금 속도로 판단한다
        assertEquals(listOf(Guidance(SCHOOL_ZONE_OVER_SPEED, listOf(38, 30))), e.onEvent(DriveEvent.SpeedLimitChanged(30)))
    }

    @Test
    fun cameraBeforeAnyRoadLimit_thenAHigherRoadLimit_isStillApproach_notExit() {
        val e = GuidanceEngine(Personas.CARLOS, clock).also { it.onEvent(DriveEvent.GearChanged(Gear.DRIVE)); it.speed(25) }
        val cam = { m: Int -> DriveEvent.CameraAlertsUpdated(listOf(CameraAlert("cam", CameraKind.SCHOOL_ZONE, 30, m))) }
        assertEquals(listOf(Guidance(SCHOOL_ZONE_SPEED_LIMIT, listOf(30))), e.onEvent(cam(200)))
        assertTrue(e.onEvent(DriveEvent.SpeedLimitChanged(50)).isEmpty())
        e.onEvent(cam(80))
        assertTrue(e.onEvent(DriveEvent.SpeedLimitChanged(30)).isEmpty())
        assertEquals(listOf(Guidance(SCHOOL_ZONE_OVER_SPEED, listOf(45, 30))), e.speed(45))
    }

    @Test
    fun sameZoneAgainAfterLeaving_restartsWatchingSilently() {
        val e = engine().also { it.speed(25) }
        val point = DriveEvent.CautionPointsUpdated(listOf(CautionPoint("zoneA", CautionKind.SCHOOL_ZONE, 300)))
        val cam = DriveEvent.CameraAlertsUpdated(listOf(CameraAlert("camA", CameraKind.SCHOOL_ZONE, 30, 200)))
        e.onEvent(point); e.onEvent(cam); e.onEvent(DriveEvent.SpeedLimitChanged(30))
        assertEquals(listOf(Guidance(SCHOOL_ZONE_OVER_SPEED, listOf(45, 30))), e.speed(45))
        e.speed(25)
        e.onEvent(DriveEvent.SpeedLimitChanged(50)) // 이탈
        clock.advanceSeconds(60)
        // 같은 구역에 다시 들어옴: 접근 경고는 다시 하지 않지만 감시는 다시 한다
        assertTrue(e.onEvent(point).isEmpty())
        e.onEvent(cam)
        e.onEvent(DriveEvent.SpeedLimitChanged(30))
        assertEquals(listOf(Guidance(SCHOOL_ZONE_OVER_SPEED, listOf(45, 30))), e.speed(45))
    }

    @Test
    fun reentry_roadLimitFirst_thenSamePointAndCamera_restartsWatching() {
        val e = engine().also { it.speed(25) }
        val point = DriveEvent.CautionPointsUpdated(listOf(CautionPoint("zoneA", CautionKind.SCHOOL_ZONE, 100)))
        val cam = DriveEvent.CameraAlertsUpdated(listOf(CameraAlert("camA", CameraKind.SCHOOL_ZONE, 30, 80)))
        e.onEvent(point); e.onEvent(cam); e.onEvent(DriveEvent.SpeedLimitChanged(30))
        assertEquals(1, e.speed(45).size)
        e.speed(25); e.onEvent(DriveEvent.SpeedLimitChanged(50)); clock.advanceSeconds(60)
        // 재진입: 도로 제한이 먼저, 그다음 같은 지점과 같은 카메라
        e.onEvent(DriveEvent.SpeedLimitChanged(30))
        assertTrue(e.onEvent(point).isEmpty())
        assertTrue(e.onEvent(cam).isEmpty())
        assertEquals(listOf(Guidance(SCHOOL_ZONE_OVER_SPEED, listOf(45, 30))), e.speed(45))
    }

    /** 도로 제한, 접근 지점, 카메라가 어떤 순서로 와도(첫 방문, 재진입) 구역 안 과속을 놓치지 않는다. */
    @Test
    fun anyOrderOfRoadLimitPointAndCamera_firstVisitAndReentry_warns() {
        val point = DriveEvent.CautionPointsUpdated(listOf(CautionPoint("zoneA", CautionKind.SCHOOL_ZONE, 100)))
        val single = DriveEvent.CameraAlertsUpdated(listOf(CameraAlert("camA", CameraKind.SCHOOL_ZONE, 30, 80)))
        // 더 먼 다음 구역 카메라가 함께 오는 목록
        val withFar = DriveEvent.CameraAlertsUpdated(
            listOf(CameraAlert("camA", CameraKind.SCHOOL_ZONE, 30, 80), CameraAlert("camB", CameraKind.SCHOOL_ZONE, 20, 200)),
        )
        val road = DriveEvent.SpeedLimitChanged(30)
        val orders = listOf(listOf(0, 1, 2), listOf(0, 2, 1), listOf(1, 0, 2), listOf(1, 2, 0), listOf(2, 0, 1), listOf(2, 1, 0))
        for (cam in listOf(single, withFar)) for (first in orders) for (again in orders) {
            val signals = listOf(road, point, cam)
            val e = engine().also { it.speed(25) }
            first.forEach { e.onEvent(signals[it]) }
            assertEquals("${cam.cameras.size} cams, first $first", listOf(Guidance(SCHOOL_ZONE_OVER_SPEED, listOf(45, 30))), e.speed(45))
            e.speed(25); e.onEvent(DriveEvent.SpeedLimitChanged(50)); clock.advanceSeconds(60)
            again.forEach { e.onEvent(signals[it]) }
            assertEquals("${cam.cameras.size} cams, first $first, again $again", listOf(Guidance(SCHOOL_ZONE_OVER_SPEED, listOf(45, 30))), e.speed(45))
            clock.advanceSeconds(120)
        }
    }

    @Test
    fun severalCameras_watchTheNearest_evenIfItWasAlreadyAnnounced() {
        val near = CameraAlert("camA", CameraKind.SCHOOL_ZONE, 30, 80)
        val far = CameraAlert("camB", CameraKind.SCHOOL_ZONE, 20, 200)
        val both = DriveEvent.CameraAlertsUpdated(listOf(near, far))
        // 첫 방문: 같은 목록이 반복돼도 가까운 30으로 감시
        val e = engine().also { it.speed(25) }
        assertEquals(listOf(Guidance(SCHOOL_ZONE_SPEED_LIMIT, listOf(30))), e.onEvent(both))
        assertTrue(e.onEvent(both).isEmpty())
        e.onEvent(DriveEvent.SpeedLimitChanged(30))
        assertEquals(listOf(Guidance(SCHOOL_ZONE_OVER_SPEED, listOf(45, 30))), e.speed(45))
        // 이탈 뒤 재진입: 이미 안내한 가까운 A와 새 먼 B가 함께 와도 A(30)로 감시
        e.speed(25); e.onEvent(DriveEvent.SpeedLimitChanged(50)); clock.advanceSeconds(60)
        e.onEvent(DriveEvent.SpeedLimitChanged(30))
        e.onEvent(DriveEvent.CautionPointsUpdated(listOf(CautionPoint("zoneA", CautionKind.SCHOOL_ZONE, 100))))
        e.onEvent(both)
        assertEquals(listOf(Guidance(SCHOOL_ZONE_OVER_SPEED, listOf(45, 30))), e.speed(45))
        repeat(3) {
            clock.advanceSeconds(31)
            e.onEvent(DriveEvent.SpeedLimitChanged(30)); e.onEvent(both)
            assertEquals(listOf(Guidance(SCHOOL_ZONE_OVER_SPEED, listOf(45, 30))), e.speed(45))
        }
    }

    @Test
    fun nextZonePointArrivesBeforeLeavingThePreviousZone_nextZoneIsStillWatched() {
        val e = engine().also { it.speed(25); it.enterZone(); it.onEvent(DriveEvent.SpeedLimitChanged(30)) }
        val pointB = { m: Int -> DriveEvent.CautionPointsUpdated(listOf(CautionPoint("zoneB", CautionKind.SCHOOL_ZONE, m))) }
        e.onEvent(pointB(300)) // A 안에서 B 접근 경고
        e.onEvent(DriveEvent.SpeedLimitChanged(50)) // A 이탈
        e.onEvent(pointB(100))
        e.onEvent(DriveEvent.SpeedLimitChanged(30)) // B 진입
        assertEquals(listOf(Guidance(SCHOOL_ZONE_OVER_SPEED, listOf(45, 30))), e.speed(45))
    }

    @Test
    fun overSpeed_isSafetyPriority() {
        assertEquals(Priority.SAFETY, SCHOOL_ZONE_OVER_SPEED.priority)
    }
}
