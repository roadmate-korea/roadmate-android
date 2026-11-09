package com.roadmatekorea.android.drive

import ai.pleos.playground.navi.constants.CameraType
import ai.pleos.playground.navi.constants.CautionPointType
import ai.pleos.playground.navi.constants.TurnType
import ai.pleos.playground.navi.data.TBTInfo
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ai.pleos.playground.navi.data.CameraAlert as SdkCameraAlert
import ai.pleos.playground.navi.data.CautionPoint as SdkCautionPoint

/** 진짜 SDK 객체를 넣어 판단 로직 이벤트로 바뀌는지 확인한다. 시연 모드도 같은 변환을 쓴다. */
class SignalMapperTest {

    @Test
    fun gear_parkIsPark_forwardGearsAreDrive() {
        assertEquals(DriveEvent.GearChanged(Gear.PARK), SignalMapper.map(RawSignal.Vehicle.Gear(VehicleGear.GEAR_PARK)))
        assertEquals(DriveEvent.GearChanged(Gear.REVERSE), SignalMapper.map(RawSignal.Vehicle.Gear(VehicleGear.GEAR_REVERSE)))
        assertEquals(DriveEvent.GearChanged(Gear.NEUTRAL), SignalMapper.map(RawSignal.Vehicle.Gear(VehicleGear.GEAR_NEUTRAL)))
        assertEquals(DriveEvent.GearChanged(Gear.UNKNOWN), SignalMapper.map(RawSignal.Vehicle.Gear(VehicleGear.GEAR_UNKNOWN)))
        listOf(
            VehicleGear.GEAR_DRIVE, VehicleGear.GEAR_1, VehicleGear.GEAR_2, VehicleGear.GEAR_3, VehicleGear.GEAR_4,
            VehicleGear.GEAR_5, VehicleGear.GEAR_6, VehicleGear.GEAR_7, VehicleGear.GEAR_8, VehicleGear.GEAR_9,
        ).forEach { assertEquals(it.name, Gear.DRIVE, SignalMapper.gear(it)) }
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
    fun tbt_isSortedByDistance_andDirectionsMapped() {
        // SDK가 가까운 순으로 주지 않는 경우
        val event = SignalMapper.map(
            RawSignal.Navi.Tbt(
                listOf(
                    TBTInfo(TurnType.TURN_LEFT, 900.4, "a"),
                    TBTInfo(TurnType.TURN_SHARP_R, 120.6, "b"),
                    TBTInfo(TurnType.TURN_STRAIGHT, 1500.0, "c"),
                    TBTInfo(TurnType.TURN_SHARP_L, 40.0, "d"),
                ),
            ),
        )
        assertEquals(
            DriveEvent.TurnInstructionsUpdated(
                listOf(
                    TurnInstruction(TurnDirection.LEFT, 40),
                    TurnInstruction(TurnDirection.RIGHT, 121),
                    TurnInstruction(TurnDirection.LEFT, 900),
                    TurnInstruction(TurnDirection.OTHER, 1500),
                ),
            ),
            event,
        )
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
