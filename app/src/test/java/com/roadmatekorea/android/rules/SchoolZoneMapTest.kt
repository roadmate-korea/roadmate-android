package com.roadmatekorea.android.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolZoneMapTest {

    /** 위도 1m(남북). */
    private val meterLat = 1 / 111_195.0

    @Test
    fun distance_isAccurateEnoughForShortRanges() {
        assertEquals(300.0, SchoolZoneMap.distanceMeters(37.5, 127.0, 37.5 + 300 * meterLat, 127.0), 0.5)
        // 경도 0.001°는 위도 37.5°에서 약 88m
        assertEquals(88.2, SchoolZoneMap.distanceMeters(37.5, 127.0, 37.5, 127.001), 0.5)
    }

    @Test
    fun nearest_findsTheClosestFacilityWithinTheRadius() {
        val map = SchoolZoneMap(
            listOf(
                SchoolZoneMap.LatLon(37.5, 127.0),
                SchoolZoneMap.LatLon(37.5 + 200 * meterLat, 127.0),
            ),
        )
        val near = map.nearest(37.5 + 150 * meterLat, 127.0, 300)!!
        assertEquals(1, near.id)
        assertEquals(50.0, near.meters, 0.5)
        assertNull(map.nearest(37.5 - 301 * meterLat, 127.0, 300))
    }

    @Test
    fun nearest_looksAcrossGridCellBorders() {
        // 격자 경계(0.01°) 바로 건너편의 시설
        val map = SchoolZoneMap(listOf(SchoolZoneMap.LatLon(37.5100001, 127.0099999)))
        val near = map.nearest(37.5099999 - 100 * meterLat, 127.0100001, 300)
        assertTrue("경계 건너편도 찾음", near != null && near.meters < 110)
    }

    @Test
    fun emptyMap_andBadInput_findNothing() {
        assertNull(SchoolZoneMap.EMPTY.nearest(37.5, 127.0, 300))
        val map = SchoolZoneMap(listOf(SchoolZoneMap.LatLon(37.5, 127.0)))
        assertNull(map.nearest(Double.NaN, 127.0, 300))
    }

    @Test
    fun parse_skipsCommentsHeaderBrokenAndOutOfKoreaLines() {
        val map = SchoolZoneMap.parse(
            sequenceOf(
                "# 출처: 공공데이터포털",
                "lat,lon",
                "37.49074,127.01542",
                "",
                "not,a number",
                "127.01542,37.49074",
                "0,0",
                "33.25,126.56",
            ),
        )
        assertEquals(2, map.size)
    }
}
