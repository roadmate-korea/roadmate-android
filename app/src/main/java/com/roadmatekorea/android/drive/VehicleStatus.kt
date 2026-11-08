package com.roadmatekorea.android.drive

import com.roadmatekorea.android.rules.Gear

/**
 * 화면 조작 잠금에 쓰는 실제 차량 상태. 시연 모드의 가짜 기어가 아니라 항상 실제 Vehicle SDK 값을 따른다.
 *
 * 정상적으로 확인된 P에서만 조작을 허용한다.
 * - 기어가 P가 아니면 잠근다.
 * - 첫 기어 값을 받기 전(UNKNOWN)에도 잠근다.
 * - 기어를 읽지 못했다는 신호(Unavailable)를 받으면 마지막 기어와 상관없이 잠그고 배너로 알린다.
 *   이후 정상 기어 신호를 다시 받으면 오류 상태를 풀고 그 값을 따른다.
 */
data class VehicleStatus(
    val gear: Gear = Gear.UNKNOWN,
    val error: Boolean = false,
) {
    val locked: Boolean get() = error || gear != Gear.PARK

    val showUnavailableBanner: Boolean get() = error

    fun on(signal: RawSignal): VehicleStatus = when (signal) {
        is RawSignal.Vehicle.Gear -> VehicleStatus(gear = SignalMapper.gear(signal.value), error = false)
        is RawSignal.Vehicle.Unavailable -> copy(error = true)
        else -> this
    }
}
