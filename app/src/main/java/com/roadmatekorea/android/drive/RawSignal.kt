package com.roadmatekorea.android.drive

import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.navi.data.CameraAlert
import ai.pleos.playground.navi.data.CautionPoint
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import kotlin.math.roundToInt

/**
 * SDK 콜백 값을 그대로 담은 봉투.
 *
 * 실제 Vehicle·NaviHelper 어댑터와 시연 모드(DemoRoute)가 모두 이 형식으로 신호를 내고,
 * 같은 [SignalMapper]를 거쳐 판단 로직으로 들어간다. 시연 모드도 진짜 SDK 객체를 만들어 넣는다.
 */
sealed interface RawSignal {

    sealed interface Vehicle : RawSignal {
        /** Android Automotive `GEAR_SELECTION` 값(`android.car.VehicleGear` 상수). */
        data class Gear(val value: Int) : Vehicle

        /** `TurnSignalStateListener.onTurnSignalStateUpdated`, `getCurrentTurnSignalState` */
        data class TurnSignal(val value: TurnSignalValue) : Vehicle

        /**
         * 차량 속도. Android Automotive `PERF_VEHICLE_SPEED`(m/s, Pleos Vehicle SDK에는 속도 API가 없다).
         * 후진이면 음수가 올 수 있다.
         */
        data class Speed(val metersPerSecond: Float) : Vehicle {
            val kmh: Int get() = kotlin.math.abs(metersPerSecond * 3.6f).roundToInt()
        }

        /** 속도를 지금 읽을 수 없음(센서 상태 이상, 차량 서비스 끊김). 마지막 속도를 지운다. 화면은 잠그지 않는다. */
        data object SpeedUnavailable : Vehicle

        /** 기어를 읽을 수 없음(차량 서비스 없음·끊김, 권한 없음 등). 화면을 잠근다. 판단 로직에는 들어가지 않는다. */
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

    /** FusedLocation. 위치는 기기 안에서 공공데이터 어린이보호구역과 견주는 데만 쓰고, 밖으로 보내거나 기록하지 않는다. */
    sealed interface Location : RawSignal {
        /** `FusedLocationListener.onChanged` */
        data class Fix(val value: LocationData) : Location {
            // 로그나 화면에 좌표가 찍히지 않게 한다
            override fun toString() = "Location.Fix"
        }

        /** FusedLocation을 쓸 수 없음(지원 안 함, 초기화 실패, 오류). 판단 로직에는 들어가지 않는다. */
        data class Unavailable(val reason: String) : Location
    }
}
