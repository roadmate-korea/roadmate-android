package com.roadmatekorea.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.roadmatekorea.android.drive.DriveGuidanceService
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
        askSpeedPermissionWhenParked()
    }

    override fun onStart() {
        super.onStart()
        // 화면이 보일 때 안내 서비스를 켜 둔다. 내비로 넘어가도 음성 안내가 계속된다.
        DriveGuidanceService.start(this)
    }

    override fun onResume() {
        super.onResume()
        // 시스템 설정에서 바꾸고 돌아온 경우
        if (app.hasSpeedPermission()) app.speedPermissionGranted.value = true
    }

    /**
     * 차량 속도 권한은 정차(P)가 정상적으로 확인되고 화면이 앞에 있을 때 한 번만 묻는다. 주행 중이나 기어 오류 뒤에는 띄우지 않는다.
     * 화면이 다시 만들어지면 대기를 다시 시작하고, 실제로 띄웠는지는 앱 범위에서 기억한다.
     * 거부해도 속도에 따른 안내만 빠지고 나머지는 그대로 동작한다.
     */
    private fun askSpeedPermissionWhenParked() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                if (app.hasSpeedPermission() || app.speedPermissionAsked) return@repeatOnLifecycle
                app.signalHub.parkedConfirmed.first { it }
                // 띄우기 직전에 다시 확인한다
                if (app.signalHub.parkedConfirmed.value && !app.hasSpeedPermission() && !app.speedPermissionAsked) {
                    app.speedPermissionAsked = true
                    requestSpeedPermission.launch(RoadMateApplication.CAR_SPEED)
                }
            }
        }
    }
}
