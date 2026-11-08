package com.roadmatekorea.android.drive

import com.roadmatekorea.android.profile.ProfileRead
import com.roadmatekorea.android.profile.model.DriverProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 저장된 프로필을 읽어 안내 세션을 연다(화면 없이 서비스만 다시 만들어진 경우).
 * 저장소 읽기는 늦거나 재시도 중일 수 있으므로 기다리는 작업을 들고 있다가, 사용자가 안내를 끝내면 취소한다.
 * 그래야 종료 뒤에 늦게 도착한 프로필이 세션을 다시 열지 않는다.
 */
class SavedSessionRestorer(
    private val scope: CoroutineScope,
    private val profiles: Flow<ProfileRead>,
    private val open: (DriverProfile) -> Unit,
) {
    private var job: Job? = null

    /** 이미 기다리는 중이면 새로 시작하지 않는다. 온보딩 전(프로필 없음)이면 아무것도 하지 않는다. */
    fun restore() {
        if (job?.isActive == true) return
        job = scope.launch {
            val saved = profiles.first { it is ProfileRead.Loaded } as ProfileRead.Loaded
            saved.profile?.let(open)
        }
    }

    /** 기다리던 복원을 버린다. 다음 [restore]는 새로 시작한다. */
    fun cancel() {
        job?.cancel()
        job = null
    }
}
