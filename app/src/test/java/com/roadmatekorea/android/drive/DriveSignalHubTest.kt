package com.roadmatekorea.android.drive

import ai.pleos.playground.vehicle.constant.control.VehicleGear
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DriveSignalHubTest {

    @Test
    fun collectsSourceOnce_andKeepsLastGearForLateSubscribers() = runTest(UnconfinedTestDispatcher()) {
        val upstream = MutableSharedFlow<RawSignal>()
        var collections = 0
        val source = DriveSignalSource { collections++; upstream }
        val hub = DriveSignalHub(source, backgroundScope)
        hub.start()
        hub.start()
        assertEquals(1, collections)

        val seen = mutableListOf<RawSignal>()
        backgroundScope.launch { hub.signals.collect { seen += it } }
        upstream.emit(RawSignal.Vehicle.Gear(VehicleGear.GEAR_DRIVE))
        upstream.emit(RawSignal.Navi.SpeedLimit(30))
        assertEquals(2, seen.size)
        assertEquals(RawSignal.Vehicle.Gear(VehicleGear.GEAR_DRIVE), hub.lastGear.value)

        upstream.emit(RawSignal.Vehicle.Unavailable("gear: x"))
        assertTrue(hub.vehicleUnavailable.value)
        assertTrue(hub.vehicleUnavailableSinceLastGear())
        // 마지막 기어는 그대로
        assertEquals(RawSignal.Vehicle.Gear(VehicleGear.GEAR_DRIVE), hub.lastGear.value)
        upstream.emit(RawSignal.Vehicle.Gear(VehicleGear.GEAR_PARK))
        assertEquals(false, hub.vehicleUnavailableSinceLastGear())
    }

    @Test
    fun parkedConfirmed_onlyForANormalParkGear_andClearedByVehicleError() = runTest(UnconfinedTestDispatcher()) {
        val upstream = MutableSharedFlow<RawSignal>()
        val hub = DriveSignalHub(DriveSignalSource { upstream }, backgroundScope).also { it.start() }
        assertEquals(false, hub.parkedConfirmed.value)
        upstream.emit(RawSignal.Vehicle.Gear(VehicleGear.GEAR_PARK))
        assertEquals(true, hub.parkedConfirmed.value)
        // 오류 뒤에는 마지막 P를 믿지 않는다
        upstream.emit(RawSignal.Vehicle.Unavailable("gear: x"))
        assertEquals(false, hub.parkedConfirmed.value)
        upstream.emit(RawSignal.Vehicle.Gear(VehicleGear.GEAR_DRIVE))
        assertEquals(false, hub.parkedConfirmed.value)
        upstream.emit(RawSignal.Vehicle.Gear(VehicleGear.GEAR_PARK))
        assertEquals(true, hub.parkedConfirmed.value)
    }
}
