package com.roadmatekorea.android.drive

import com.roadmatekorea.android.rules.Gear

/**
 * 화면 조작 잠금에 쓰는 실제 차량 상태. 시연 모드의 가짜 기어가 아니라 항상 실제 Vehicle SDK 값을 따른다.
 *
 * - 기어가 P가 아니면 잠근다(주행 중 화면은 읽기 전용).
 * - 첫 기어 값을 받기 전(UNKNOWN)에도 잠근다.
 * - Vehicle SDK를 아예 쓸 수 없고 기어를 한 번도 받지 못했으면 잠그지 않고 배너로 알린다.
 *   (SDK가 없는 환경에서 앱이 완전히 막히지 않게 하려는 것. 한 번이라도 기어를 받았으면 마지막 값을 따른다.)
 */
data class VehicleStatus(
    val gear: Gear = Gear.UNKNOWN,
    val unavailable: Boolean = false,
) {
    val locked: Boolean get() = if (gear == Gear.UNKNOWN && unavailable) false else gear != Gear.PARK

    val showUnavailableBanner: Boolean get() = unavailable && gear == Gear.UNKNOWN

    fun on(signal: RawSignal): VehicleStatus = when (signal) {
        is RawSignal.Vehicle.Gear -> copy(gear = SignalMapper.gear(signal.value))
        is RawSignal.Vehicle.Unavailable -> copy(unavailable = true)
        else -> this
    }
}
