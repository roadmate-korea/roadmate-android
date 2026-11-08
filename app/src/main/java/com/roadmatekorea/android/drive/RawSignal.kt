package com.roadmatekorea.android.drive

import ai.pleos.playground.navi.data.CameraAlert
import ai.pleos.playground.navi.data.CautionPoint
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear

/**
 * SDK 콜백 값을 그대로 담은 봉투.
 *
 * 실제 Vehicle·NaviHelper 어댑터와 시연 모드(DemoRoute)가 모두 이 형식으로 신호를 내고,
 * 같은 [SignalMapper]를 거쳐 판단 로직으로 들어간다. 시연 모드도 진짜 SDK 객체를 만들어 넣는다.
 */
sealed interface RawSignal {

    sealed interface Vehicle : RawSignal {
        /** `GearSelectionListener.onSelected`, `getCurrentGearSelectionState` */
        data class Gear(val value: VehicleGear) : Vehicle

        /** `TurnSignalStateListener.onTurnSignalStateUpdated`, `getCurrentTurnSignalState` */
        data class TurnSignal(val value: TurnSignalValue) : Vehicle

        /** Vehicle SDK를 쓸 수 없음(초기화 실패, 권한 없음 등). 판단 로직에는 들어가지 않는다. */
        data class Unavailable(val reason: String) : Vehicle
    }

    sealed interface Navi : RawSignal {
        /** `onCurrentRoadSpeedLimit` */
        data class SpeedLimit(val kmh: Int) : Navi

        /** `onCautionPointInfo` */
        data class CautionPoints(val points: List<CautionPoint>) : Navi

        /** `onCameraAlert` */
        data class CameraAlerts(val cameras: List<CameraAlert>) : Navi

        /** `onTBTInfo`. SDK 목록은 거리순이 아닐 수 있다. */
        data class Tbt(val turns: List<TBTInfo>) : Navi

        /** `onDestinationArrived` */
        data object Arrived : Navi

        /** NaviHelper를 쓸 수 없음. 판단 로직에는 들어가지 않는다. */
        data class Unavailable(val reason: String) : Navi
    }
}
