package com.roadmatekorea.android.debug

import android.os.Bundle
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.roadmatekorea.android.rules.SchoolZoneMap
import kotlin.math.roundToInt
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.listener.ProximityAlertListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.location.fused.model.ProximityAlertEvent
import ai.pleos.playground.location.fused.model.ProximityAlertRequest
import ai.pleos.playground.location.fused.model.ProximityTransition

/**
 * 디버그 빌드 전용: FusedLocation이 에뮬레이터에서 어떻게 들어오는지 본다(`adb emu geo fix`를 따라가는지).
 *
 * adb shell am start -n com.roadmatekorea.android/.debug.LocationProbeActivity [--ef lat 37.49 --ef lon 127.015 --ef radius 300]
 * 결과: adb logcat -s RoadMateLocation. 좌표·시각 원본은 로그와 화면에 남기지 않는다.
 * 받은 횟수, 직전 위치에서 움직인 거리, 그리고 lat/lon을 주면 그 지점까지의 거리(10m 단위)만 적는다.
 * lat/lon을 주면 그 지점에 근접 알림(addProximityAlert)도 걸어 본다(진입·이탈만 적는다).
 */
class LocationProbeActivity : ComponentActivity() {

    private lateinit var log: TextView
    private var fused: FusedLocation? = null
    private var alertId: String? = null
    private var count = 0
    private var previous: LocationData? = null
    private var target: Pair<Double, Double>? = null

    private val listener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) = write("fix ${++count} ${describe(location)}")
        override fun onError(e: Exception) = write("onError ${e.javaClass.simpleName}")
    }

    /** 좌표 없이 움직임과 목표까지 거리만. */
    private fun describe(location: LocationData?): String {
        if (location == null) return "null"
        val moved = previous?.let { meters(it.latitude, it.longitude, location.latitude, location.longitude) }
        previous = location
        val toTarget = target?.let { (lat, lon) -> meters(location.latitude, location.longitude, lat, lon) }
        return buildString {
            append("moved=").append(moved?.let { "${it}m" } ?: "-")
            if (toTarget != null) append(" toTarget=").append(toTarget).append("m")
        }
    }

    private fun meters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Int =
        (SchoolZoneMap.distanceMeters(lat1, lon1, lat2, lon2) / 10).roundToInt() * 10

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log = TextView(this).apply { textSize = 20f; setPadding(32, 32, 32, 32) }
        setContentView(ScrollView(this).apply { addView(log) })

        val f = FusedLocation(this)
        fused = f
        runCatching {
            write("isSupported=${f.isSupported()}")
            f.initialize()
            write("initialized")
            f.getLastKnownLocation(object : FusedLocationListener {
                override fun onChanged(location: LocationData?) = write("last ${if (location == null) "null" else "received"}")
                override fun onError(e: Exception) = write("last error ${e.javaClass.simpleName}")
            })
            f.registerFusedLocationCallback(listener)
            write("registered")
        }.onFailure { write("failed ${it.javaClass.simpleName}") }

        val lat = intent.getFloatExtra("lat", Float.NaN)
        val lon = intent.getFloatExtra("lon", Float.NaN)
        if (!lat.isNaN() && !lon.isNaN()) {
            target = lat.toDouble() to lon.toDouble()
            val radius = intent.getFloatExtra("radius", 300f)
            runCatching {
                f.addProximityAlert(
                    ProximityAlertRequest(lat.toDouble(), lon.toDouble(), radius, setOf(ProximityTransition.ENTER, ProximityTransition.EXIT)),
                    object : ProximityAlertListener {
                        override fun onRegistered(id: String) {
                            alertId = id
                            write("proximity registered")
                        }

                        override fun onTransition(event: ProximityAlertEvent) = write("proximity ${event.transition}")
                        override fun onError(e: Exception) = write("proximity error ${e.javaClass.simpleName}")
                    },
                )
            }.onFailure { write("proximity failed ${it.javaClass.simpleName}") }
        }
    }

    override fun onDestroy() {
        fused?.let { f ->
            runCatching {
                alertId?.let(f::removeProximityAlert)
                f.unregisterFusedLocationCallback(listener)
                f.release()
            }
        }
        super.onDestroy()
    }

    private fun write(line: String) {
        Log.i(TAG, line)
        runOnUiThread { log.append(line + "\n") }
    }

    private companion object {
        const val TAG = "RoadMateLocation"
    }
}
