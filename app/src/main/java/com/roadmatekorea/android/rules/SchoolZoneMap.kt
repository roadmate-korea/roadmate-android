package com.roadmatekorea.android.rules

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * 어린이보호구역 대상 시설 좌표(공공데이터포털 전국어린이보호구역표준데이터). 가까운 시설을 찾는다.
 *
 * 좌표는 시설(학교, 유치원, 어린이집 등) 위치다. 구역은 보통 시설 주 출입문에서 300m 안쪽이지만 곳마다 다르다.
 * 그래서 거리는 "구역 경계까지"가 아니라 "시설까지"이고, 안내에 숫자로 말하지 않는다.
 *
 * 격자(약 1km) 버킷으로 나눠 주변 칸만 본다. 만든 뒤에는 바꾸지 않는다.
 */
class SchoolZoneMap(points: List<LatLon>) {

    private val zones: List<LatLon> = points.toList()
    private val cells: Map<Long, IntArray>

    init {
        val buckets = HashMap<Long, MutableList<Int>>()
        zones.forEachIndexed { i, p -> buckets.getOrPut(cellKey(cellOf(p.lat), cellOf(p.lon))) { mutableListOf() }.add(i) }
        cells = buckets.mapValues { it.value.toIntArray() }
    }

    val size: Int get() = zones.size

    /** [radiusMeters] 안에서 가장 가까운 시설. 없으면 null. [radiusMeters]는 격자 한 칸(약 1km)보다 작아야 한다. */
    fun nearest(lat: Double, lon: Double, radiusMeters: Int): Nearby? {
        require(radiusMeters in 0..MAX_RADIUS_METERS)
        if (zones.isEmpty() || !lat.isFinite() || !lon.isFinite()) return null
        val row = cellOf(lat)
        val col = cellOf(lon)
        var best: Nearby? = null
        for (dr in -1..1) for (dc in -1..1) {
            val ids = cells[cellKey(row + dr, col + dc)] ?: continue
            for (i in ids) {
                val d = distanceMeters(lat, lon, zones[i].lat, zones[i].lon)
                if (d <= radiusMeters && (best == null || d < best.meters)) best = Nearby(i, d)
            }
        }
        return best
    }

    /** 시설 [id]의 좌표. */
    fun point(id: Int): LatLon = zones[id]

    data class LatLon(val lat: Double, val lon: Double)

    /** [id]는 이 지도 안의 번호(앱을 다시 만들면 바뀔 수 있다). */
    data class Nearby(val id: Int, val meters: Double)

    companion object {
        val EMPTY = SchoolZoneMap(emptyList())

        /** 격자 한 칸(도). 위도 방향 약 1.1km, 한국 경도 방향 약 0.9km. */
        private const val CELL_DEGREES = 0.01

        /** 이웃 칸까지만 보므로 반경은 한 칸의 짧은 변(경도 방향 약 0.88km)보다 작아야 한다. */
        const val MAX_RADIUS_METERS = 800

        private const val EARTH_RADIUS_M = 6_371_000.0

        private fun cellOf(degrees: Double): Int = floor(degrees / CELL_DEGREES).toInt()

        private fun cellKey(row: Int, col: Int): Long = (row.toLong() shl 32) or (col.toLong() and 0xffffffffL)

        /** 짧은 거리용 근사(등장방형). 1km 안에서 오차는 1m보다 작다. */
        fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val rad = PI / 180
            val x = (lon2 - lon1) * rad * cos((lat1 + lat2) / 2 * rad)
            val y = (lat2 - lat1) * rad
            return sqrt(x * x + y * y) * EARTH_RADIUS_M
        }

        /**
         * `lat,lon` 줄(앞에 `#` 주석과 머리글 허용)을 읽는다. 읽을 수 없는 줄은 건너뛴다.
         */
        fun parse(lines: Sequence<String>): SchoolZoneMap = SchoolZoneMap(
            lines.mapNotNull { line ->
                if (line.isBlank() || line.startsWith("#")) return@mapNotNull null
                val parts = line.split(',')
                if (parts.size < 2) return@mapNotNull null
                val lat = parts[0].trim().toDoubleOrNull() ?: return@mapNotNull null
                val lon = parts[1].trim().toDoubleOrNull() ?: return@mapNotNull null
                LatLon(lat, lon).takeIf { lat in 33.0..39.0 && lon in 124.0..132.0 }
            }.toList(),
        )
    }
}
