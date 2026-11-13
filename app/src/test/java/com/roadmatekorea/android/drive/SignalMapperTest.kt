package com.roadmatekorea.android.drive

import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.navi.constants.CameraType
import ai.pleos.playground.navi.constants.CautionPointType
import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import android.car.VehicleGear
import com.roadmatekorea.android.rules.CameraAlert
import com.roadmatekorea.android.rules.CameraKind
import com.roadmatekorea.android.rules.CautionKind
import com.roadmatekorea.android.rules.CautionPoint
import com.roadmatekorea.android.rules.DriveEvent
import com.roadmatekorea.android.rules.FakeClock
import com.roadmatekorea.android.rules.Gear
import com.roadmatekorea.android.rules.GuidanceEngine
import com.roadmatekorea.android.rules.GuidanceId
import com.roadmatekorea.android.rules.TurnDirection
import com.roadmatekorea.android.rules.TurnInstruction
import com.roadmatekorea.android.rules.TurnSignal
import com.roadmatekorea.android.profile.model.Personas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ai.pleos.playground.navi.data.CameraAlert as SdkCameraAlert
import ai.pleos.playground.navi.data.CautionPoint as SdkCautionPoint

/** 진짜 SDK 객체를 넣어 판단 로직 이벤트로 바뀌는지 확인한다. 시연 모드도 같은 변환을 쓴다. */
class SignalMapperTest {

    @Test
    fun tollGate_isItsOwnDirection() {
        assertEquals(TurnDirection.TOLL, SignalMapper.turnDirection(TurnType.TURN_TOLL))
    }

    @Test
    fun location_becomesCoordinatesOnly_andNeverPrintsThem() {
        val fix = RawSignal.Location.Fix(LocationData(37.4907, 127.0154, 12.0, 90f, 5f, 1L))
        assertEquals(DriveEvent.LocationChanged(37.4907, 127.0154), SignalMapper.map(fix))
        assertFalse("로그에 좌표 없음", fix.toString().contains("37.49") || SignalMapper.map(fix).toString().contains("37.49"))
        assertNull(SignalMapper.map(RawSignal.Location.Fix(LocationData(Double.NaN, 127.0, 0.0, 0f, 0f, 1L))))
        assertNull(SignalMapper.map(RawSignal.Location.Unavailable("x")))
    }

    @Test
    fun gear_parkIsPark_forwardGearsAreDrive() {
        assertEquals(DriveEvent.GearChanged(Gear.PARK), SignalMapper.map(RawSignal.Vehicle.Gear(VehicleGear.GEAR_PARK)))
        assertEquals(DriveEvent.GearChanged(Gear.REVERSE), SignalMapper.map(RawSignal.Vehicle.Gear(VehicleGear.GEAR_REVERSE)))
        assertEquals(DriveEvent.GearChanged(Gear.NEUTRAL), SignalMapper.map(RawSignal.Vehicle.Gear(VehicleGear.GEAR_NEUTRAL)))
        assertEquals(DriveEvent.GearChanged(Gear.UNKNOWN), SignalMapper.map(RawSignal.Vehicle.Gear(VehicleGear.GEAR_UNKNOWN)))
        listOf(
            VehicleGear.GEAR_DRIVE, VehicleGear.GEAR_FIRST, VehicleGear.GEAR_SECOND, VehicleGear.GEAR_THIRD, VehicleGear.GEAR_FOURTH,
            VehicleGear.GEAR_FIFTH, VehicleGear.GEAR_SIXTH, VehicleGear.GEAR_SEVENTH, VehicleGear.GEAR_EIGHTH, VehicleGear.GEAR_NINTH,
        ).forEach { assertEquals("gear $it", Gear.DRIVE, SignalMapper.gear(it)) }
        // 모르는 값(AAOS가 나중에 더하는 값 등)은 주행으로 보지 않고 UNKNOWN(화면 잠금)
        assertEquals(Gear.UNKNOWN, SignalMapper.gear(3))
    }

    @Test
    fun turnSignal_allValues() {
        assertEquals(DriveEvent.TurnSignalChanged(TurnSignal.LEFT), SignalMapper.map(RawSignal.Vehicle.TurnSignal(TurnSignalValue.LEFT)))
        assertEquals(DriveEvent.TurnSignalChanged(TurnSignal.RIGHT), SignalMapper.map(RawSignal.Vehicle.TurnSignal(TurnSignalValue.RIGHT)))
        assertEquals(DriveEvent.TurnSignalChanged(TurnSignal.OFF), SignalMapper.map(RawSignal.Vehicle.TurnSignal(TurnSignalValue.OFF)))
    }

    @Test
    fun cautionPoints_schoolZoneKept_othersMarkedOther() {
        val event = SignalMapper.map(
            RawSignal.Navi.CautionPoints(
                listOf(
                    SdkCautionPoint("sz-1", CautionPointType.SCHOOL_ZONE, 300),
                    SdkCautionPoint("bump-1", CautionPointType.SPEED_BUMP, 120),
                ),
            ),
        )
        assertEquals(
            DriveEvent.CautionPointsUpdated(
                listOf(CautionPoint("sz-1", CautionKind.SCHOOL_ZONE, 300), CautionPoint("bump-1", CautionKind.OTHER, 120)),
            ),
            event,
        )
    }

    @Test
    fun cameras_bothSchoolZoneTypes_keepSdkSpeedLimit() {
        val event = SignalMapper.map(
            RawSignal.Navi.CameraAlerts(
                listOf(
                    SdkCameraAlert("c1", CameraType.SPEED_CAMERA_SCHOOL_ZONE, true, 30, 100, 0, false, false),
                    SdkCameraAlert("c2", CameraType.SPEED_CAMERA_SCHOOL_ZONE_TRAFFIC_LIGHT, true, 40, 200, 0, false, false),
                    SdkCameraAlert("c3", CameraType.SPEED_CAMERA_FIX, true, 60, 300, 0, false, false),
                ),
            ),
        )
        assertEquals(
            DriveEvent.CameraAlertsUpdated(
                listOf(
                    CameraAlert("c1", CameraKind.SCHOOL_ZONE, 30, 100),
                    CameraAlert("c2", CameraKind.SCHOOL_ZONE, 40, 200),
                    CameraAlert("c3", CameraKind.OTHER, 60, 300),
                ),
            ),
            event,
        )
    }

    @Test
    fun tbt_keepsRouteOrder_andAccumulatesSegmentDistances() {
        // 에뮬레이터 실측(강릉 경로) 앞부분: 거리는 앞 안내 지점부터의 구간 거리다
        val event = SignalMapper.map(
            RawSignal.Navi.Tbt(
                listOf(
                    TBTInfo(TurnType.TURN_LEFT, 80.0, "a"),
                    TBTInfo(TurnType.TURN_LEFT, 390.0, "b"),
                    TBTInfo(TurnType.TURN_KEEP_R, 1000.0, "c"),
                    TBTInfo(TurnType.TURN_RIGHT, 70.0, "d"),
                    TBTInfo(TurnType.TURN_SHARP_R, 120.6, "e"),
                ),
            ),
        )
        assertEquals(
            DriveEvent.TurnInstructionsUpdated(
                listOf(
                    TurnInstruction(TurnDirection.LEFT, 80),
                    TurnInstruction(TurnDirection.LEFT, 470),
                    TurnInstruction(TurnDirection.OTHER, 1470),
                    // 구간 거리 70m지만 실제로는 1.5km 앞의 우회전
                    TurnInstruction(TurnDirection.RIGHT, 1540),
                    TurnInstruction(TurnDirection.RIGHT, 1661),
                ),
            ),
            event,
        )
    }

    @Test
    fun tbt_invalidDistance_dropsThatTurnAndEverythingAfter_butZeroIsValid() {
        fun map(vararg turns: Pair<TurnType, Double>) =
            (SignalMapper.map(RawSignal.Navi.Tbt(turns.map { TBTInfo(it.first, it.second, "x") })) as DriveEvent.TurnInstructionsUpdated).turns
        // 첫 항목 음수: 0m 회전으로 만들지 않는다
        assertEquals(emptyList<TurnInstruction>(), map(TurnType.TURN_RIGHT to -1.0, TurnType.TURN_LEFT to 200.0))
        // 중간 음수와 NaN: 그 앞까지만
        assertEquals(listOf(TurnInstruction(TurnDirection.LEFT, 100)), map(TurnType.TURN_LEFT to 100.0, TurnType.TURN_RIGHT to -5.0, TurnType.TURN_LEFT to 50.0))
        assertEquals(listOf(TurnInstruction(TurnDirection.LEFT, 100)), map(TurnType.TURN_LEFT to 100.0, TurnType.TURN_RIGHT to Double.NaN))
        // 유효한 0m는 그대로
        assertEquals(
            listOf(TurnInstruction(TurnDirection.OTHER, 0), TurnInstruction(TurnDirection.RIGHT, 0), TurnInstruction(TurnDirection.LEFT, 200)),
            map(TurnType.TURN_STRAIGHT to 0.0, TurnType.TURN_RIGHT to 0.0, TurnType.TURN_LEFT to 200.0),
        )
    }

    @Test
    fun negativeFirstTurn_isNotAnnounced_andALaterValidUpdateStillWorks() {
        val engine = GuidanceEngine(Personas.CARLOS, FakeClock())
        engine.onEvent(DriveEvent.SessionStarted)
        engine.onEvent(DriveEvent.GearChanged(Gear.DRIVE))
        fun tbt(vararg turns: Pair<TurnType, Double>) =
            engine.onEvent(SignalMapper.map(RawSignal.Navi.Tbt(turns.map { TBTInfo(it.first, it.second, "x") }))!!).map { it.id }
        assertFalse(tbt(TurnType.TURN_RIGHT to -1.0).contains(GuidanceId.RIGHT_TURN_RULE))
        assertTrue(tbt(TurnType.TURN_RIGHT to 120.0).contains(GuidanceId.RIGHT_TURN_RULE))
    }

    @Test
    fun realRouteTbt_rightTurnFarAhead_isNotAnnouncedUntilItIsNear() {
        val engine = GuidanceEngine(Personas.CARLOS, FakeClock())
        engine.onEvent(DriveEvent.SessionStarted)
        engine.onEvent(DriveEvent.GearChanged(Gear.DRIVE))
        fun tbt(vararg turns: Pair<TurnType, Double>) =
            engine.onEvent(SignalMapper.map(RawSignal.Navi.Tbt(turns.map { TBTInfo(it.first, it.second, "x") }))!!).map { it.id }
        // 우회전의 구간 거리는 70m지만 실제로는 1,540m 앞이다
        val far = tbt(TurnType.TURN_LEFT to 80.0, TurnType.TURN_LEFT to 390.0, TurnType.TURN_KEEP_R to 1000.0, TurnType.TURN_RIGHT to 70.0)
        assertFalse(far.contains(GuidanceId.RIGHT_TURN_RULE))
        // 앞의 안내 지점을 지나 우회전이 첫 항목으로 가까워지면 그때 안내한다
        val near = tbt(TurnType.TURN_RIGHT to 100.0, TurnType.TURN_LEFT to 2000.0)
        assertTrue(near.contains(GuidanceId.RIGHT_TURN_RULE))
    }

    @Test
    fun speedLimitAndArrival() {
        assertEquals(DriveEvent.SpeedLimitChanged(30), SignalMapper.map(RawSignal.Navi.SpeedLimit(30)))
        assertEquals(DriveEvent.DestinationArrived, SignalMapper.map(RawSignal.Navi.Arrived))
    }

    @Test
    fun unavailable_isNotADriveEvent() {
        assertNull(SignalMapper.map(RawSignal.Vehicle.Unavailable("x")))
        assertNull(SignalMapper.map(RawSignal.Navi.Unavailable("x")))
    }

    @Test
    fun inactiveCameraAlerts_areDropped_andSectionFieldsAreKept() {
        val event = SignalMapper.map(
            RawSignal.Navi.CameraAlerts(
                listOf(
                    SdkCameraAlert("off", CameraType.SECTION_SPEED_ALERT, false, 80, 300, 95, false, false),
                    SdkCameraAlert("on", CameraType.SECTION_SPEED_ALERT, true, 80, 300, 95, false, false),
                    SdkCameraAlert("start", CameraType.SPEED_CAMERA_SECTION_START, true, 80, 500, 0, false, false),
                ),
            ),
        ) as com.roadmatekorea.android.rules.DriveEvent.CameraAlertsUpdated
        assertEquals(listOf("on", "start"), event.cameras.map { it.id })
        assertEquals(com.roadmatekorea.android.rules.CameraKind.SECTION_AVERAGE, event.cameras[0].kind)
        assertEquals(95, event.cameras[0].sectionAverageKmh)
        assertEquals(com.roadmatekorea.android.rules.CameraKind.SECTION_START, event.cameras[1].kind)
    }

    @Test
    fun busLaneCautionTypes_mapToBusLane() {
        assertEquals(com.roadmatekorea.android.rules.CautionKind.BUS_LANE, SignalMapper.cautionKind(CautionPointType.BUS_LANE_VIOLATION_ENFORCEMENT))
        assertEquals(com.roadmatekorea.android.rules.CautionKind.BUS_LANE, SignalMapper.cautionKind(CautionPointType.BUS_LANE_UNAVAILABLE_NOTICE))
        assertEquals(com.roadmatekorea.android.rules.CautionKind.OTHER, SignalMapper.cautionKind(CautionPointType.BUS_LANE_AVAILABLE_NOTICE))
    }

    @Test
    fun vehicleSpeed_isMetersPerSecond_toWholeKmh_andReverseIsPositive() {
        assertEquals(DriveEvent.VehicleSpeedChanged(45), SignalMapper.map(RawSignal.Vehicle.Speed(12.5f)))
        assertEquals(DriveEvent.VehicleSpeedChanged(0), SignalMapper.map(RawSignal.Vehicle.Speed(0f)))
        assertEquals(DriveEvent.VehicleSpeedChanged(18), SignalMapper.map(RawSignal.Vehicle.Speed(-5f)))
        assertEquals(DriveEvent.VehicleSpeedChanged(null), SignalMapper.map(RawSignal.Vehicle.SpeedUnavailable))
    }
}
