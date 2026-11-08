package com.roadmatekorea.android.drive

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 실제 차량·내비 신호를 앱 프로세스 수명 동안 한 번만 수집해 여러 곳에 나눠 준다.
 *
 * Vehicle SDK 2.3.2는 `release`가 `initialize`의 연결 작업을 취소하지 않아, 연결 중에 해제하면 해제 뒤
 * 다시 서비스에 붙을 수 있다. 그래서 화면이 바뀔 때마다 수집을 끊고 다시 시작하지 않는다.
 * (NaviHelper 3.0.5는 정리할 때 연결 작업을 취소하지만, 같은 허브에서 함께 관리한다.)
 * 프로세스가 끝나면 SDK 연결도 프로세스·Binder 자원 회수로 정리된다.
 *
 * 주의: [lastGear]는 마지막으로 받은 값이지 지금 연결이 살아 있다는 보장이 아니다.
 * SDK는 서비스 연결이 끊겨도 앱 콜백을 부르지 않는 경로가 있다.
 *
 * 늦게 구독한 쪽도 지금 기어를 알 수 있도록 마지막 기어 신호와 Vehicle 사용 불가 여부를 따로 보관한다.
 */
class DriveSignalHub(
    private val source: DriveSignalSource,
    private val scope: CoroutineScope,
) {
    private val _signals = MutableSharedFlow<RawSignal>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** 실제 신호. 구독한 뒤에 들어온 것만 받는다. */
    val signals: SharedFlow<RawSignal> = _signals.asSharedFlow()

    private val _lastGear = MutableStateFlow<RawSignal.Vehicle.Gear?>(null)

    /** 마지막으로 받은 실제 기어. 아직 없으면 null. */
    val lastGear: StateFlow<RawSignal.Vehicle.Gear?> = _lastGear.asStateFlow()

    private val _vehicleUnavailable = MutableStateFlow(false)

    /** Vehicle SDK를 쓸 수 없다는 신호를 받은 적이 있음. */
    val vehicleUnavailable: StateFlow<Boolean> = _vehicleUnavailable.asStateFlow()

    private var job: Job? = null

    /** 여러 번 불러도 한 번만 시작한다. */
    fun start() {
        if (job != null) return
        job = scope.launch {
            source.signals().collect { signal ->
                when (signal) {
                    is RawSignal.Vehicle.Gear -> _lastGear.value = signal
                    is RawSignal.Vehicle.Unavailable -> _vehicleUnavailable.value = true
                    else -> Unit
                }
                _signals.emit(signal)
            }
        }
    }
}
