package com.roadmatekorea.android.rules

/**
 * 판단 로직이 받는 내부 주행 이벤트.
 *
 * SDK 타입은 여기 들어오지 않는다. Vehicle, NaviHelper 어댑터와 시연 모드가
 * 같은 변환 함수를 거쳐 이 타입으로 바꿔 넣는다.
 */
sealed interface DriveEvent {

    /** 온보딩 START 직후, 또는 시연 시작. */
    data object SessionStarted : DriveEvent

    data class GearChanged(val gear: Gear) : DriveEvent

    data class TurnSignalChanged(val signal: TurnSignal) : DriveEvent

    /** 현재 도로 제한 속도(km/h). NaviHelper `onCurrentRoadSpeedLimit`. */
    data class SpeedLimitChanged(val kmh: Int) : DriveEvent

    /** NaviHelper `onCautionPointInfo`. 빈 목록은 주의 지점이 없다는 뜻이다. */
    data class CautionPointsUpdated(val points: List<CautionPoint>) : DriveEvent

    /** NaviHelper `onCameraAlert`. */
    data class CameraAlertsUpdated(val cameras: List<CameraAlert>) : DriveEvent

    /** NaviHelper `onTBTInfo`. 빈 목록은 경로 안내 정보가 없다는 뜻이다. */
    data class TurnInstructionsUpdated(val turns: List<TurnInstruction>) : DriveEvent

    data object DestinationArrived : DriveEvent
}

enum class Gear {
    PARK,
    DRIVE,
    REVERSE,
    NEUTRAL,

    /** 아직 값을 받지 못했거나 알 수 없는 값. */
    UNKNOWN,
}

enum class TurnSignal { OFF, LEFT, RIGHT }

enum class TurnDirection { LEFT, RIGHT, OTHER }

enum class CautionKind {
    SCHOOL_ZONE,

    /** 버스전용차로 단속 지점, 또는 버스전용차로 운영 중 알림 */
    BUS_LANE,
    OTHER,
}

enum class CameraKind {
    SCHOOL_ZONE,

    /** 구간 단속 시작 카메라 */
    SECTION_START,

    /** 구간 단속 중 평균 속도 알림([CameraAlert.sectionAverageKmh]) */
    SECTION_AVERAGE,

    /** 구간 단속 끝 카메라 */
    SECTION_END,
    OTHER,
}

data class CautionPoint(val id: String, val kind: CautionKind, val remainMeters: Int)

/**
 * [speedLimitKmh]가 0 이하면 값이 없는 것으로 본다.
 * [sectionAverageKmh]: 구간 단속 중 지금까지의 평균 속도(NaviHelper `sectionAvrSpeed`). 0 이하면 값 없음.
 */
data class CameraAlert(
    val id: String,
    val kind: CameraKind,
    val speedLimitKmh: Int,
    val remainMeters: Int,
    val sectionAverageKmh: Int = 0,
)

data class TurnInstruction(val direction: TurnDirection, val distanceMeters: Int)
