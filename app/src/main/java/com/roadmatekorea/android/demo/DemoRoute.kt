package com.roadmatekorea.android.demo

import ai.pleos.playground.navi.constants.CameraType
import ai.pleos.playground.navi.constants.CautionPointType
import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.navi.data.CameraAlert
import ai.pleos.playground.navi.data.CautionPoint
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import com.roadmatekorea.android.drive.RawSignal
import com.roadmatekorea.android.profile.model.DriverProfile
import com.roadmatekorea.android.profile.model.Personas

/** 시연 단계: [delayMs] 기다린 뒤 [signals]를 차례로 낸다. */
data class DemoStep(val delayMs: Long, val signals: List<RawSignal>)

/**
 * 정해진 주행을 실제 SDK와 같은 입력 형식으로 재생하는 시나리오.
 *
 * 신호는 모두 진짜 SDK 객체(VehicleGear, TurnSignalValue, CautionPoint, CameraAlert, TBTInfo)로 만든다.
 * 실제 어댑터와 같은 [RawSignal] → SignalMapper → GuidanceEngine 경로를 타며, 판단 로직에 시연용 분기는 없다.
 */
object DemoRoute {

    private fun gear(value: VehicleGear) = RawSignal.Vehicle.Gear(value)
    private fun turnSignal(value: TurnSignalValue) = RawSignal.Vehicle.TurnSignal(value)
    private fun tbt(vararg turns: Pair<TurnType, Double>) =
        RawSignal.Navi.Tbt(turns.map { TBTInfo(it.first, it.second, "demo") })

    /** 출발 → 어린이보호구역 300m 전 → 제한 속도 30 → 버스전용차로 → 차선 변경 → 우회전 → 좌회전 → 구간 단속 → 도착 */
    val DEFAULT: List<DemoStep> = listOf(
        // 출발: 기어 D. 시작 안내
        DemoStep(0, listOf(gear(VehicleGear.GEAR_DRIVE))),
        DemoStep(2_000, listOf(RawSignal.Navi.SpeedLimit(50))),
        // 어린이보호구역 300m 전 (모든 사용자)
        DemoStep(3_000, listOf(RawSignal.Navi.CautionPoints(listOf(CautionPoint("demo-sz-1", CautionPointType.SCHOOL_ZONE, 300))))),
        // 구역 안: 제한 속도는 NaviHelper 값(도로 제한 속도와 단속 카메라)
        DemoStep(
            3_000,
            listOf(
                RawSignal.Navi.SpeedLimit(30),
                RawSignal.Navi.CameraAlerts(
                    listOf(CameraAlert("demo-cam-1", CameraType.SPEED_CAMERA_SCHOOL_ZONE, true, 30, 100, 0, false, false)),
                ),
            ),
        ),
        // 구역을 벗어남. 경로 안내 시작(600m 앞 우회전)
        DemoStep(
            4_000,
            listOf(
                RawSignal.Navi.CautionPoints(emptyList()),
                RawSignal.Navi.CameraAlerts(emptyList()),
                RawSignal.Navi.SpeedLimit(50),
                tbt(TurnType.TURN_RIGHT to 600.0, TurnType.TURN_STRAIGHT to 1_500.0),
            ),
        ),
        // 250m 앞 버스전용차로 단속 지점(상세 안내 대상)
        DemoStep(2_000, listOf(RawSignal.Navi.CautionPoints(listOf(CautionPoint("demo-bl-1", CautionPointType.BUS_LANE_VIOLATION_ENFORCEMENT, 250))))),
        DemoStep(3_000, listOf(RawSignal.Navi.CautionPoints(emptyList()))),
        // 근처에 좌회전이 없는데 좌측 깜빡이 → 차선 변경
        DemoStep(2_000, listOf(turnSignal(TurnSignalValue.LEFT))),
        DemoStep(3_000, listOf(turnSignal(TurnSignalValue.OFF))),
        // 120m 앞 우회전 (SDK처럼 가까운 순이 아닌 목록) + 우측 깜빡이
        DemoStep(2_000, listOf(tbt(TurnType.TURN_LEFT to 900.0, TurnType.TURN_RIGHT to 120.0))),
        DemoStep(1_000, listOf(turnSignal(TurnSignalValue.RIGHT))),
        DemoStep(3_000, listOf(turnSignal(TurnSignalValue.OFF))),
        // 120m 앞 좌회전 + 좌측 깜빡이
        DemoStep(2_000, listOf(tbt(TurnType.TURN_LEFT to 120.0))),
        DemoStep(1_000, listOf(turnSignal(TurnSignalValue.LEFT))),
        DemoStep(3_000, listOf(turnSignal(TurnSignalValue.OFF), tbt())),
        // 구간 단속 시작(제한 80). 평균 속도는 아직 없음
        DemoStep(
            2_000,
            listOf(
                RawSignal.Navi.SpeedLimit(80),
                RawSignal.Navi.CameraAlerts(listOf(CameraAlert("demo-sec-1", CameraType.SPEED_CAMERA_SECTION_START, true, 80, 300, 0, false, false))),
            ),
        ),
        DemoStep(3_000, listOf(RawSignal.Navi.CameraAlerts(emptyList()), RawSignal.Navi.SpeedLimit(50))),
        // 도착, 정차
        DemoStep(2_000, listOf(RawSignal.Navi.Arrived)),
        DemoStep(1_000, listOf(gear(VehicleGear.GEAR_PARK))),
    )
}

/** 시연 페르소나 프리셋. 같은 [DemoRoute]를 서로 다른 프로필로 재생한다. */
enum class DemoPersona(val profile: DriverProfile) {
    NAKAMURA(Personas.NAKAMURA),
    CARLOS(Personas.CARLOS),
}
