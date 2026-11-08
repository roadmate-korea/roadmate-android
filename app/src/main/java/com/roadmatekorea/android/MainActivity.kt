package com.roadmatekorea.android

import ai.pleos.playground.vehicle.constant.control.VehicleGear
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.roadmatekorea.android.ui.RoadMateApp
import com.roadmatekorea.android.ui.theme.RoadMateTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val app get() = application as RoadMateApplication

    private val requestSpeedPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        app.speedPermissionGranted.value = granted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = app.profileRepository
        setContent {
            RoadMateTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RoadMateApp(repository)
                }
            }
        }
        askSpeedPermissionWhenParked(savedInstanceState == null)
    }

    override fun onResume() {
        super.onResume()
        // 시스템 설정에서 바꾸고 돌아온 경우
        if (app.hasSpeedPermission()) app.speedPermissionGranted.value = true
    }

    /**
     * 차량 속도 권한은 정차(P)가 확인된 뒤에 한 번만 묻는다. 주행 중에는 허용 창을 띄우지 않는다.
     * 거부해도 속도에 따른 안내만 빠지고 나머지는 그대로 동작한다.
     */
    private fun askSpeedPermissionWhenParked(firstCreate: Boolean) {
        if (!firstCreate || app.hasSpeedPermission()) return
        lifecycleScope.launch {
            app.signalHub.lastGear.first { it?.value == VehicleGear.GEAR_PARK }
            if (!app.hasSpeedPermission()) requestSpeedPermission.launch(RoadMateApplication.CAR_SPEED)
        }
    }
}
