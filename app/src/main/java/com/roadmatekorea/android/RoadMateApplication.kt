package com.roadmatekorea.android

import android.app.Application
import com.roadmatekorea.android.drive.DriveSignalHub
import com.roadmatekorea.android.drive.RealDriveSignalSource
import com.roadmatekorea.android.navi.NaviSignalSource
import com.roadmatekorea.android.profile.ProfileRepository
import com.roadmatekorea.android.vehicle.VehicleSignalSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** 앱 전체에서 하나만 쓰는 객체를 둔다(간단한 수동 의존성 주입). */
class RoadMateApplication : Application() {

    /** 프로세스 수명 동안 살아 있는 범위. 신호는 메인 스레드에서 처리한다. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val profileRepository: ProfileRepository by lazy { ProfileRepository(this) }

    /** 실제 Vehicle·NaviHelper 신호. 처음 쓸 때 한 번 시작하고 프로세스가 끝날 때까지 수집한다. */
    val signalHub: DriveSignalHub by lazy {
        DriveSignalHub(RealDriveSignalSource(VehicleSignalSource(this), NaviSignalSource(this)), appScope).also { it.start() }
    }
}
