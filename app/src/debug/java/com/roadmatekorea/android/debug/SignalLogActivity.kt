package com.roadmatekorea.android.debug

import android.os.Bundle
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.roadmatekorea.android.drive.RealDriveSignalSource
import com.roadmatekorea.android.drive.SignalMapper
import com.roadmatekorea.android.navi.NaviSignalSource
import com.roadmatekorea.android.vehicle.VehicleSignalSource
import kotlinx.coroutines.launch

/**
 * 디버그 빌드 전용: 실제 Vehicle·NaviHelper 신호가 어떻게 들어오는지 본다.
 *
 * adb shell am start -n com.roadmatekorea.android/.debug.SignalLogActivity
 * 결과: adb logcat -s RoadMateSignals RoadMateVehicle RoadMateNavi
 * 위치나 경로 원본은 기록하지 않는다(안내에 쓰는 값만).
 */
class SignalLogActivity : ComponentActivity() {

    private lateinit var log: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        log = TextView(this).apply { textSize = 20f; setPadding(32, 32, 32, 32) }
        setContentView(ScrollView(this).apply { addView(log) })

        val source = RealDriveSignalSource(VehicleSignalSource(this), NaviSignalSource(this))
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                write("collect start")
                source.signals().collect { raw ->
                    write("RAW $raw")
                    write("  -> ${SignalMapper.map(raw)}")
                }
            }
        }
    }

    private fun write(line: String) {
        Log.i(TAG, line)
        log.append(line + "\n")
    }

    private companion object {
        const val TAG = "RoadMateSignals"
    }
}
