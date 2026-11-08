package com.roadmatekorea.android.drive

import ai.pleos.playground.navi.constants.CameraType
import ai.pleos.playground.navi.constants.CautionPointType
import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import com.roadmatekorea.android.rules.CameraAlert
import com.roadmatekorea.android.rules.CameraKind
import com.roadmatekorea.android.rules.CautionKind
import com.roadmatekorea.android.rules.CautionPoint
import com.roadmatekorea.android.rules.DriveEvent
import com.roadmatekorea.android.rules.Gear
import com.roadmatekorea.android.rules.TurnDirection
import com.roadmatekorea.android.rules.TurnInstruction
import com.roadmatekorea.android.rules.TurnSignal
import kotlin.math.roundToInt

/**
 * SDK 신호 → 판단 로직 이벤트. SDK 타입을 아는 곳은 여기와 어댑터뿐이다.
 * 실제 SDK 경로와 시연 모드가 이 함수 하나를 함께 쓴다.
 */
object SignalMapper {

    /** 판단 로직과 상관없는 신호(Unavailable 등)는 null. */
    fun map(signal: RawSignal): DriveEvent? = when (signal) {
        is RawSignal.Vehicle.Gear -> DriveEvent.GearChanged(gear(signal.value))
        is RawSignal.Vehicle.TurnSignal -> DriveEvent.TurnSignalChanged(turnSignal(signal.value))
        is RawSignal.Vehicle.Unavailable -> null
        is RawSignal.Navi.SpeedLimit -> DriveEvent.SpeedLimitChanged(signal.kmh)
        is RawSignal.Navi.CautionPoints -> DriveEvent.CautionPointsUpdated(
            signal.points.map { CautionPoint(it.id, cautionKind(it.type), it.remainDistance) },
        )
        is RawSignal.Navi.CameraAlerts -> DriveEvent.CameraAlertsUpdated(
            // status=false는 SDK가 비활성으로 표시한 알림이다(Pleos CameraAlert 명세). 안내하지 않는다.
            signal.cameras.filter { it.status }
                .map { CameraAlert(it.id, cameraKind(it.type), it.speedLimit, it.remainDistance, it.sectionAvrSpeed) },
        )
        is RawSignal.Navi.Tbt -> DriveEvent.TurnInstructionsUpdated(
            // SDK 목록은 가까운 순이 아닐 수 있어 거리순으로 정렬한다
            signal.turns
                .map { TurnInstruction(turnDirection(it.type), it.distance.roundToInt()) }
                .sortedBy { it.distanceMeters },
        )
        RawSignal.Navi.Arrived -> DriveEvent.DestinationArrived
        is RawSignal.Navi.Unavailable -> null
    }

    fun gear(value: VehicleGear): Gear = when (value) {
        VehicleGear.GEAR_PARK -> Gear.PARK
        VehicleGear.GEAR_REVERSE -> Gear.REVERSE
        VehicleGear.GEAR_NEUTRAL -> Gear.NEUTRAL
        VehicleGear.GEAR_DRIVE,
        VehicleGear.GEAR_1, VehicleGear.GEAR_2, VehicleGear.GEAR_3,
        VehicleGear.GEAR_4, VehicleGear.GEAR_5, VehicleGear.GEAR_6,
        VehicleGear.GEAR_7, VehicleGear.GEAR_8, VehicleGear.GEAR_9 -> Gear.DRIVE
        VehicleGear.GEAR_UNKNOWN -> Gear.UNKNOWN
    }

    fun turnSignal(value: TurnSignalValue): TurnSignal = when (value) {
        TurnSignalValue.OFF -> TurnSignal.OFF
        TurnSignalValue.LEFT -> TurnSignal.LEFT
        TurnSignalValue.RIGHT -> TurnSignal.RIGHT
    }

    fun cautionKind(type: CautionPointType): CautionKind = when (type) {
        CautionPointType.SCHOOL_ZONE -> CautionKind.SCHOOL_ZONE
        // 단속 지점과 "버스전용차로 이용 불가(운영 중)" 알림. "이용 가능" 알림은 안내하지 않는다.
        CautionPointType.BUS_LANE_VIOLATION_ENFORCEMENT,
        CautionPointType.BUS_LANE_UNAVAILABLE_NOTICE -> CautionKind.BUS_LANE
        else -> CautionKind.OTHER
    }

    fun cameraKind(type: CameraType): CameraKind = when (type) {
        CameraType.SPEED_CAMERA_SCHOOL_ZONE,
        CameraType.SPEED_CAMERA_SCHOOL_ZONE_TRAFFIC_LIGHT -> CameraKind.SCHOOL_ZONE
        CameraType.SPEED_CAMERA_SECTION_START -> CameraKind.SECTION_START
        CameraType.SECTION_SPEED_ALERT -> CameraKind.SECTION_AVERAGE
        CameraType.SPEED_CAMERA_SECTION_END -> CameraKind.SECTION_END
        else -> CameraKind.OTHER
    }

    fun turnDirection(type: TurnType): TurnDirection = when (type) {
        TurnType.TURN_RIGHT, TurnType.TURN_SHARP_R -> TurnDirection.RIGHT
        TurnType.TURN_LEFT, TurnType.TURN_SHARP_L -> TurnDirection.LEFT
        else -> TurnDirection.OTHER
    }
}
