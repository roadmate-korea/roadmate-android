package com.roadmatekorea.android.debug

import android.os.Bundle
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
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
 * 결과: adb logcat -s RoadMateLocation. 위치는 기기 로그에만 남고 밖으로 보내지 않는다.
 * lat/lon을 주면 그 지점에 근접 알림(addProximityAlert)도 걸어 본다.
 */
class LocationProbeActivity : ComponentActivity() {

    private lateinit var log: TextView
    private var fused: FusedLocation? = null
    private var alertId: String? = null

    private val listener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) = write("onChanged $location")
        override fun onError(e: Exception) = write("onError $e")
    }

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
                override fun onChanged(location: LocationData?) = write("last $location")
                override fun onError(e: Exception) = write("last error $e")
            })
            f.registerFusedLocationCallback(listener)
            write("registered")
        }.onFailure { write("failed $it") }

        val lat = intent.getFloatExtra("lat", Float.NaN)
        val lon = intent.getFloatExtra("lon", Float.NaN)
        if (!lat.isNaN() && !lon.isNaN()) {
            val radius = intent.getFloatExtra("radius", 300f)
            runCatching {
                f.addProximityAlert(
                    ProximityAlertRequest(lat.toDouble(), lon.toDouble(), radius, setOf(ProximityTransition.ENTER, ProximityTransition.EXIT)),
                    object : ProximityAlertListener {
                        override fun onRegistered(id: String) {
                            alertId = id
                            write("proximity registered $id")
                        }

                        override fun onTransition(event: ProximityAlertEvent) = write("proximity $event")
                        override fun onError(e: Exception) = write("proximity error $e")
                    },
                )
            }.onFailure { write("proximity failed $it") }
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
