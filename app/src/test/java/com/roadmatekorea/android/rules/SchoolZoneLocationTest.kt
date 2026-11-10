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
    fun naviAndLocation_doNotSilenceEachOther_becauseNaviPointsHaveNoCoordinates() {
        // 같은 구역인지 확인할 수 없으므로 둘 다 말한다(필수 경고를 빠뜨리지 않는다)
        val e = engine(experienced)
        e.send(at(-700))
        assertEquals(listOf(SCHOOL_ZONE_AHEAD), e.send(schoolCaution("sz", 300)))
        clock.advanceSeconds(30)
        assertEquals(listOf(SCHOOL_ZONE_NEARBY), e.send(at(-400), at(-250)))
    }

    @Test
    fun naviWarnedForA_thenTurningTowardsNearbyB_warnsForB() {
        // 재현: A/B 사이에서 내비가 A를 알린 뒤 북쪽 B로 감
        val e = engine(experienced)
        e.send(at(600))
        assertEquals(listOf(SCHOOL_ZONE_AHEAD), e.send(schoolCaution("A", 300)))
        clock.advanceSeconds(45)
        assertEquals(listOf(SCHOOL_ZONE_NEARBY), e.send(at(750)))
    }

    @Test
    fun naviWarnedForA_thenSchoolBFurtherOn_stillWarns() {
        // 내비가 A를 알린 직후 1km 떨어진 B
        val e = engine(experienced)
        e.send(at(-700))
        assertEquals(listOf(SCHOOL_ZONE_AHEAD), e.send(schoolCaution("szA", 300)))
        clock.advanceSeconds(30)
        assertEquals(emptyList<GuidanceId>(), e.send(at(500)))
        assertEquals(listOf(SCHOOL_ZONE_NEARBY), e.send(at(750)))
    }

    @Test
    fun naviWarnedWithoutKnownPosition_locationStillWarns() {
        val e = engine(experienced)
        assertEquals(listOf(SCHOOL_ZONE_AHEAD), e.send(schoolCaution("sz", 300)))
        assertEquals(listOf(SCHOOL_ZONE_NEARBY), e.send(at(-250)))
    }

    @Test
    fun locationWarnedFirst_naviStillWarns_andTheSpeedLimitFollows() {
        val e = engine(experienced)
        e.onEvent(DriveEvent.SpeedLimitChanged(50))
        assertEquals(listOf(SCHOOL_ZONE_NEARBY), e.send(at(-400), at(-250)))
        clock.advanceSeconds(5)
        e.send(at(-200))
        assertEquals(listOf(SCHOOL_ZONE_AHEAD), e.send(schoolCaution("sz", 150)))
        // 제한 속도는 내비 값으로 이어진다
        assertEquals(listOf(SCHOOL_ZONE_SPEED_LIMIT), e.send(DriveEvent.SpeedLimitChanged(30)))
    }

    @Test
    fun locationWarned_thenMovingAway_aNaviZoneAhead_isAnotherZone_andWarns() {
        val e = engine(experienced)
        assertEquals(listOf(SCHOOL_ZONE_NEARBY), e.send(at(-250)))
        clock.advanceSeconds(5)
        e.send(at(-50), at(150))
        assertEquals(listOf(SCHOOL_ZONE_AHEAD), e.send(schoolCaution("next", 300)))
    }

    @Test
    fun locationGap_thenADifferentSchool_warns() {
        // A 근처에서 위치가 끊겼다가 B 근처에서 다시 들어옴
        val e = engine(experienced)
        assertEquals(listOf(SCHOOL_ZONE_NEARBY), e.send(at(0)))
        clock.advanceSeconds(180)
        assertEquals(listOf(SCHOOL_ZONE_NEARBY), e.send(at(1_000)))
    }

    @Test
    fun withoutAGap_jumpingToAFarSchool_isANewEntry() {
        val e = engine(experienced)
        assertEquals(listOf(SCHOOL_ZONE_NEARBY), e.send(at(0)))
        clock.advanceSeconds(1)
        assertEquals(listOf(SCHOOL_ZONE_NEARBY), e.send(at(1_000)))
    }

    @Test
    fun neighbouringFacilities_areOneCluster() {
        val near = SchoolZoneMap.LatLon(schoolA.lat + 500 * meterLat, schoolA.lon)
        val e = engine(experienced, SchoolZoneMap(listOf(schoolA, near)))
        assertEquals(listOf(SCHOOL_ZONE_NEARBY), e.send(at(-250)))
        assertEquals(emptyList<GuidanceId>(), e.send(at(0), at(250), at(500), at(700)))
    }

    @Test
    fun clusterNeighbourPassedSilently_thenAGap_thenTheSameNeighbour_staysSilent() {
        val neighbour = SchoolZoneMap.LatLon(schoolA.lat + 500 * meterLat, schoolA.lon)
        val e = engine(experienced, SchoolZoneMap(listOf(schoolA, neighbour, schoolB)))
        assertEquals(listOf(SCHOOL_ZONE_NEARBY), e.send(at(0)))
        clock.advanceSeconds(20)
        assertEquals(emptyList<GuidanceId>(), e.send(at(500)))
        clock.advanceSeconds(31)
        assertEquals("같은 이웃 시설", emptyList<GuidanceId>(), e.send(at(500)))
        clock.advanceSeconds(60)
        assertEquals("멀리 떨어진 새 시설은 알림", listOf(SCHOOL_ZONE_NEARBY), e.send(at(1_000)))
    }

    @Test
    fun locationAlone_speedLimitDropInsideTheZone_isAnnounced() {
        val e = engine(experienced)
        e.onEvent(DriveEvent.SpeedLimitChanged(50))
        assertEquals(listOf(SCHOOL_ZONE_NEARBY), e.send(at(-250)))
        val out = e.onEvent(DriveEvent.SpeedLimitChanged(30))
        assertEquals(listOf(Guidance(SCHOOL_ZONE_SPEED_LIMIT, listOf(30))), out)
    }
}
