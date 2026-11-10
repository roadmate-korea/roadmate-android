package com.roadmatekorea.android.rules

import com.roadmatekorea.android.profile.model.AppLanguage
import com.roadmatekorea.android.profile.model.DriverProfile
import com.roadmatekorea.android.profile.model.DrivingCountry
import com.roadmatekorea.android.profile.model.KoreaExperience
import com.roadmatekorea.android.profile.model.Personas
import com.roadmatekorea.android.rules.GuidanceId.SCHOOL_ZONE_AHEAD
import com.roadmatekorea.android.rules.GuidanceId.SCHOOL_ZONE_CROSSWALK
import com.roadmatekorea.android.rules.GuidanceId.SCHOOL_ZONE_NEARBY
import com.roadmatekorea.android.rules.GuidanceId.SCHOOL_ZONE_SPEED_LIMIT
import org.junit.Assert.assertEquals
import org.junit.Test

/** 공공데이터 어린이보호구역 시설 위치로 하는 경고. */
class SchoolZoneLocationTest {

    private val clock = FakeClock()
    private val meterLat = 1 / 111_195.0

    /** 시설 A(37.5, 127.0)와 북쪽 1km의 시설 B. */
    private val schoolA = SchoolZoneMap.LatLon(37.5, 127.0)
    private val schoolB = SchoolZoneMap.LatLon(37.5 + 1_000 * meterLat, 127.0)
    private val map = SchoolZoneMap(listOf(schoolA, schoolB))

    private val experienced = DriverProfile(AppLanguage.EN, DrivingCountry.OTHER, KoreaExperience.DROVE_IN_KOREA)

    private fun engine(profile: DriverProfile = Personas.CARLOS, zones: SchoolZoneMap = map) =
        GuidanceEngine(profile, clock) { zones }.also { it.onEvent(DriveEvent.GearChanged(Gear.DRIVE)) }

    /** 시설 A에서 남북으로 [meters]만큼 떨어진 위치(음수는 남쪽). */
    private fun at(meters: Int) = DriveEvent.LocationChanged(schoolA.lat + meters * meterLat, schoolA.lon)

    private fun GuidanceEngine.send(vararg events: DriveEvent) = events.flatMap { onEvent(it) }.map { it.id }

    private fun schoolCaution(id: String, remain: Int) =
        DriveEvent.CautionPointsUpdated(listOf(CautionPoint(id, CautionKind.SCHOOL_ZONE, remain)))

    @Test
    fun enteringTheRadius_warnsOnce_forEveryone_withCrosswalkForDetailed() {
        val carlos = engine()
        assertEquals(emptyList<GuidanceId>(), carlos.send(at(-600), at(-400), at(-320)))
        assertEquals(listOf(SCHOOL_ZONE_NEARBY, SCHOOL_ZONE_CROSSWALK), carlos.send(at(-290)))
        assertEquals(emptyList<GuidanceId>(), carlos.send(at(-200), at(-50), at(100)))

        assertEquals(listOf(SCHOOL_ZONE_NEARBY), engine(experienced).send(at(-400), at(-250)))
    }

    @Test
    fun jitterAroundTheEdge_andComingBackToTheSameFacility_doNotRepeat() {
        val e = engine(experienced)
        assertEquals(listOf(SCHOOL_ZONE_NEARBY), e.send(at(-299)))
        // 경계 근처에서 흔들림(벗어나는 반경은 350m)
        assertEquals(emptyList<GuidanceId>(), e.send(at(-320), at(-340), at(-295), at(-345)))
        // 멀리 갔다가 같은 시설로 다시 옴: 세션당 한 번
        clock.advanceSeconds(300)
        assertEquals(emptyList<GuidanceId>(), e.send(at(-500), at(-200)))
    }

    @Test
    fun aDifferentFacilitySoonAfter_warnsAgain() {
        val e = engine(experienced)
        assertEquals(listOf(SCHOOL_ZONE_NEARBY), e.send(at(-250)))
        clock.advanceSeconds(20)
        assertEquals(emptyList<GuidanceId>(), e.send(at(400), at(600)))
        clock.advanceSeconds(20)
        assertEquals("1km 북쪽 시설 B", listOf(SCHOOL_ZONE_NEARBY), e.send(at(750)))
    }

    @Test
    fun parked_waitsUntilDriving_evenWhenAlreadyClose() {
        val e = engine(experienced)
        e.onEvent(DriveEvent.GearChanged(Gear.PARK))
        assertEquals(emptyList<GuidanceId>(), e.send(at(-100), at(-100)))
        e.onEvent(DriveEvent.GearChanged(Gear.DRIVE))
        assertEquals(listOf(SCHOOL_ZONE_NEARBY), e.send(at(-90)))
    }

    @Test
    fun firstFixAlreadyInside_whileDriving_warns() {
        assertEquals(listOf(SCHOOL_ZONE_NEARBY), engine(experienced).send(at(-150)))
    }

    @Test
    fun noData_saysNothing() {
        assertEquals(emptyList<GuidanceId>(), engine(zones = SchoolZoneMap.EMPTY).send(at(-600), at(0)))
    }

    @Test
    fun naviWarnedFirst_locationStaysSilent() {
        val e = engine(experienced)
        assertEquals(listOf(SCHOOL_ZONE_AHEAD), e.send(schoolCaution("sz", 300)))
        clock.advanceSeconds(30)
        assertEquals(emptyList<GuidanceId>(), e.send(at(-400), at(-250)))
    }

    @Test
    fun locationWarnedFirst_naviStaysSilent_andTheSpeedLimitStillFollows() {
        val e = engine(experienced)
        e.onEvent(DriveEvent.SpeedLimitChanged(50))
        assertEquals(listOf(SCHOOL_ZONE_NEARBY), e.send(at(-400), at(-250)))
        clock.advanceSeconds(10)
        assertEquals(emptyList<GuidanceId>(), e.send(schoolCaution("sz", 200)))
        // 제한 속도는 내비 값으로 이어진다
        assertEquals(listOf(SCHOOL_ZONE_SPEED_LIMIT), e.send(DriveEvent.SpeedLimitChanged(30)))
    }

    @Test
    fun locationAlone_speedLimitDropInsideTheZone_isAnnounced() {
        val e = engine(experienced)
        e.onEvent(DriveEvent.SpeedLimitChanged(50))
        assertEquals(listOf(SCHOOL_ZONE_NEARBY), e.send(at(-250)))
        val out = e.onEvent(DriveEvent.SpeedLimitChanged(30))
        assertEquals(listOf(Guidance(SCHOOL_ZONE_SPEED_LIMIT, listOf(30))), out)
    }

    @Test
    fun insideANaviZoneConfirmedByTheRoad_locationStaysSilent_evenLater() {
        val e = engine(experienced)
        e.onEvent(DriveEvent.SpeedLimitChanged(50))
        e.send(schoolCaution("sz", 300))
        e.send(DriveEvent.SpeedLimitChanged(30))
        clock.advanceSeconds(70)
        // 도로 제한이 계속 구역 제한을 확인해 주는 중
        e.send(DriveEvent.SpeedLimitChanged(30))
        assertEquals(emptyList<GuidanceId>(), e.send(at(-250)))
    }
}
