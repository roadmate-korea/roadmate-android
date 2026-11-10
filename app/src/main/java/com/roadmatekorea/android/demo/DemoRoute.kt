package com.roadmatekorea.android.demo

import ai.pleos.playground.location.fused.model.LocationData
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

    /** 차량 속도 신호. 실제 센서처럼 m/s로 넣는다. */
    private fun speed(kmh: Int) = RawSignal.Vehicle.Speed(kmh / 3.6f)
    /**
     * 시연용 공공데이터 시설: 서울교대부설초(서울 서초구 파일, 앱 데이터에 들어 있음). 이 시설에서 남쪽으로 [metersSouth]만큼
     * 떨어진 위치 신호. 실제 FusedLocation과 같은 [LocationData] 객체로 만든다.
     */
    private fun southOfDemoSchool(metersSouth: Int) = RawSignal.Location.Fix(
        LocationData(DEMO_SCHOOL_LAT - metersSouth / 111_195.0, DEMO_SCHOOL_LON, 0.0, 0f, 5f, 0L),
    )

    const val DEMO_SCHOOL_LAT = 37.4907
    const val DEMO_SCHOOL_LON = 127.0154

    private fun tbt(vararg turns: Pair<TurnType, Double>) =
        RawSignal.Navi.Tbt(turns.map { TBTInfo(it.first, it.second, "demo") })

    /** 출발 → 어린이보호구역 300m 전 → 제한 속도 30(이때 시속 45라 과속 경고) → 감속 → 버스전용차로 → 차선 변경 → 우회전 → 좌회전 → 구간 단속 → 학교 근처(공공데이터) → 도착 */
    val DEFAULT: List<DemoStep> = listOf(
        // 출발: 기어 D. 시작 안내
        DemoStep(0, listOf(gear(VehicleGear.GEAR_DRIVE))),
        DemoStep(2_000, listOf(RawSignal.Navi.SpeedLimit(50), speed(48))),
        // 어린이보호구역 300m 전 (모든 사용자)
        DemoStep(3_000, listOf(speed(45), RawSignal.Navi.CautionPoints(listOf(CautionPoint("demo-sz-1", CautionPointType.SCHOOL_ZONE, 300))))),
        // 구역 안: 제한 속도는 NaviHelper 값(도로 제한 속도와 단속 카메라). 아직 시속 45라 제한 안내 대신 과속 경고
        DemoStep(
            3_000,
            listOf(
                RawSignal.Navi.SpeedLimit(30),
                RawSignal.Navi.CameraAlerts(
                    listOf(CameraAlert("demo-cam-1", CameraType.SPEED_CAMERA_SCHOOL_ZONE, true, 30, 100, 0, false, false)),
                ),
            ),
        ),
        // 속도를 줄임(제한보다 느리면 더 말하지 않는다)
        DemoStep(2_000, listOf(speed(32), speed(28))),
        // 구역을 벗어남. 경로 안내 시작(600m 앞 우회전)
        DemoStep(
            4_000,
            listOf(
                speed(45),
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
        // 120m 앞 우회전, 그 뒤 780m 더 가서 좌회전(SDK처럼 경로 순서, 구간 거리) + 우측 깜빡이
        DemoStep(2_000, listOf(speed(25), tbt(TurnType.TURN_RIGHT to 120.0, TurnType.TURN_LEFT to 780.0))),
        DemoStep(1_000, listOf(turnSignal(TurnSignalValue.RIGHT))),
        DemoStep(3_000, listOf(turnSignal(TurnSignalValue.OFF))),
        // 120m 앞 좌회전 + 좌측 깜빡이
        DemoStep(2_000, listOf(tbt(TurnType.TURN_LEFT to 120.0))),
        DemoStep(1_000, listOf(turnSignal(TurnSignalValue.LEFT))),
        DemoStep(3_000, listOf(turnSignal(TurnSignalValue.OFF), tbt(), speed(40))),
        // 구간 단속 시작(제한 80). 평균 속도는 아직 없음
        DemoStep(
            2_000,
            listOf(
                RawSignal.Navi.SpeedLimit(80),
                speed(76),
                RawSignal.Navi.CameraAlerts(listOf(CameraAlert("demo-sec-1", CameraType.SPEED_CAMERA_SECTION_START, true, 80, 300, 0, false, false))),
            ),
        ),
        DemoStep(3_000, listOf(RawSignal.Navi.CameraAlerts(emptyList()), RawSignal.Navi.SpeedLimit(50))),
        // 경로 안내 정보 없이 학교 쪽으로(내비 주의 지점 없음): 공공데이터 시설 위치로 어린이보호구역을 알린다
        DemoStep(2_000, listOf(speed(40), southOfDemoSchool(600))),
        DemoStep(1_000, listOf(southOfDemoSchool(450))),
        DemoStep(1_000, listOf(southOfDemoSchool(280))),
        DemoStep(3_000, listOf(speed(28), southOfDemoSchool(150))),
        DemoStep(2_000, listOf(southOfDemoSchool(0))),
        // 도착, 정차
        DemoStep(2_000, listOf(speed(0), RawSignal.Navi.Arrived)),
        DemoStep(1_000, listOf(gear(VehicleGear.GEAR_PARK))),
    )
}

/** 시연 페르소나 프리셋. 같은 [DemoRoute]를 서로 다른 프로필로 재생한다. */
enum class DemoPersona(val profile: DriverProfile) {
    NAKAMURA(Personas.NAKAMURA),
    CARLOS(Personas.CARLOS),
}
