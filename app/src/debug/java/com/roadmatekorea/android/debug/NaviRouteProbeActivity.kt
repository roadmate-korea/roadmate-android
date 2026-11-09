package com.roadmatekorea.android.debug

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.CameraAlert
import ai.pleos.playground.navi.data.CautionPoint
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.os.Bundle
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity

/**
 * 디버그 빌드 전용: NaviHelper로 경로 안내를 직접 걸고, 안내 중에 오는 이벤트를 그대로 기록한다.
 * TBT 거리의 기준(현재 위치부터인지, 앞 안내 지점부터인지)과 주의 지점·카메라 이벤트가 실제로 오는지 확인하는 용도.
 *
 * adb shell am start --user 10 -n com.roadmatekorea.android/.debug.NaviRouteProbeActivity \
 *   --ef lat 37.7519 --ef lon 128.8761 --es name Gangneung
 * 취소: --ez cancel true
 * 결과: adb logcat -s RoadMateNaviProbe
 * 목적지는 시험용 공공 장소 좌표만 쓴다. 현재 위치는 기록하지 않는다.
 */
class NaviRouteProbeActivity : ComponentActivity() {

    private lateinit var log: TextView
    private var navi: NaviHelper? = null

    private val listener = object : NaviHelperEventListener {
        override fun onRouteStarted(info: RouteStartInfo) = write("routeStarted $info")
        override fun onRouteEnded() = write("routeEnded")
        override fun onRouteCancelled() = write("routeCancelled")
        override fun onError(code: NaviErrorCode) = write("error $code")
        override fun onNavigationStatus(status: NavigationStatus) = write("status $status")
        override fun onDestinationInfo(info: DestinationInfo) = writeThrottled("dest", "destination $info")
        override fun onDrivingInfo(info: DrivingInfo) = writeThrottled("driving", "driving $info")
        override fun onCurrentRoadSpeedLimit(kmh: Int) = writeThrottled("limit", "speedLimit $kmh")
        override fun onCautionPointInfo(points: List<CautionPoint>) =
            write("caution n=${points.size} " + points.joinToString { "${it.type}@${it.remainDistance}m#${it.id}" })
        override fun onCameraAlert(cameras: List<CameraAlert>) = write("camera n=${cameras.size} $cameras")
        override fun onTBTInfo(turns: List<TBTInfo>) {
            var sum = 0.0
            val rows = turns.mapIndexed { i, t ->
                sum += t.distance
                "#$i ${t.type} d=${"%.0f".format(t.distance)} cum=${"%.0f".format(sum)} ${t.description}"
            }
            writeThrottled("tbt", "tbt n=${turns.size} sum=${"%.0f".format(sum)}\n  " + rows.take(8).joinToString("\n  "))
        }
    }

    private val lastWrite = mutableMapOf<String, Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log = TextView(this).apply { textSize = 18f; setPadding(32, 32, 32, 32) }
        setContentView(ScrollView(this).apply { addView(log) })
        val helper = NaviHelper(applicationContext).also { navi = it }
        helper.initialize({
            helper.addListener(listener)
            if (intent.getBooleanExtra("cancel", false)) {
                write("cancelRoute")
                helper.cancelRoute()
                return@initialize
            }
            val lat = intent.getFloatExtra("lat", 0f).toDouble()
            val lon = intent.getFloatExtra("lon", 0f).toDouble()
            val name = intent.getStringExtra("name") ?: "probe"
            if (lat == 0.0) {
                write("listening only (no lat/lon)")
                helper.getTBTInfo()
                return@initialize
            }
            write("requestRoute $name")
            // poiSubId 등에 null을 넣으면 SDK 안에서 NPE가 난다는 기록이 있어 빈 문자열을 넣는다
            helper.requestRoute(RouteInfo(lat, lon, null, null, name, "", "", "", RouteOption.RECOMMENDED, "", false))
        }, { e -> write("init failed $e") })
    }

    override fun onDestroy() {
        navi?.removeListener(listener)
        navi?.release()
        super.onDestroy()
    }

    private fun writeThrottled(key: String, line: String) {
        val now = System.currentTimeMillis()
        if (now - (lastWrite[key] ?: 0L) < THROTTLE_MS) return
        lastWrite[key] = now
        write(line)
    }

    private fun write(line: String) {
        Log.i(TAG, line)
        runOnUiThread { log.append(line + "\n") }
    }

    private companion object {
        const val TAG = "RoadMateNaviProbe"
        const val THROTTLE_MS = 5_000L
    }
}
