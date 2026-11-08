package com.roadmatekorea.android

import android.app.Application
import android.content.pm.PackageManager
import com.roadmatekorea.android.drive.DriveSignalHub
import com.roadmatekorea.android.drive.RealDriveSignalSource
import com.roadmatekorea.android.navi.NaviSignalSource
import com.roadmatekorea.android.profile.ProfileRepository
import com.roadmatekorea.android.vehicle.VehicleSignalSource
import com.roadmatekorea.android.vehicle.VehicleSpeedSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

/** 앱 전체에서 하나만 쓰는 객체를 둔다(간단한 수동 의존성 주입). */
class RoadMateApplication : Application() {

    /** 프로세스 수명 동안 살아 있는 범위. 신호는 메인 스레드에서 처리한다. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val profileRepository: ProfileRepository by lazy { ProfileRepository(this) }

    /** 차량 속도 권한(`CAR_SPEED`)이 허용되었는지. 화면에서 허용을 받으면 true로 바꾼다. */
    val speedPermissionGranted: MutableStateFlow<Boolean> by lazy { MutableStateFlow(hasSpeedPermission()) }

    fun hasSpeedPermission(): Boolean = checkSelfPermission(CAR_SPEED) == PackageManager.PERMISSION_GRANTED

    /** 실제 Vehicle·NaviHelper 신호. 처음 쓸 때 한 번 시작하고 프로세스가 끝날 때까지 수집한다. */
    val signalHub: DriveSignalHub by lazy {
        DriveSignalHub(
            RealDriveSignalSource(VehicleSignalSource(this), NaviSignalSource(this), VehicleSpeedSource(this, speedPermissionGranted)),
            appScope,
        ).also { it.start() }
    }

    companion object {
        /** Android Automotive 차량 속도 권한. Pleos 권한이 아닌 표준 권한이라 문자열로 둔다. */
        const val CAR_SPEED = "android.car.permission.CAR_SPEED"
    }
}
