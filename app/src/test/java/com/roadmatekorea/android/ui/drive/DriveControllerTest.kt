package com.roadmatekorea.android.ui.drive

import ai.pleos.playground.navi.constants.CautionPointType
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import com.roadmatekorea.android.demo.DemoPersona
import com.roadmatekorea.android.drive.DriveSignalHub
import com.roadmatekorea.android.drive.DriveSignalSource
import com.roadmatekorea.android.drive.RawSignal
import com.roadmatekorea.android.profile.model.Personas
import com.roadmatekorea.android.voice.TtsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale
import ai.pleos.playground.navi.data.CautionPoint as SdkCautionPoint

/** 실제 신호·시연·설정 화면 사이의 세션 전환을 JVM에서 확인한다(TTS와 문장은 가짜). */
@OptIn(ExperimentalCoroutinesApi::class)
class DriveControllerTest {

    private val dispatcher = StandardTestDispatcher()

    private class InstantTts : TtsEngine {
        val spoken = mutableListOf<String>()
        private var listener: TtsEngine.Listener? = null
        override fun initialize(onResult: (ready: Boolean) -> Unit) = onResult(true)
        override fun setListener(listener: TtsEngine.Listener?) {
            this.listener = listener
        }
        override fun speak(text: String, locale: Locale) {
            spoken += text
            listener?.onStart()
            listener?.onDone()
        }
        override fun stop() = Unit
        override fun release() = Unit
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class Rig(val vm: DriveController, val upstream: MutableSharedFlow<RawSignal>, val tts: InstantTts)

    private fun TestScope.rig(): Rig {
        val upstream = MutableSharedFlow<RawSignal>()
        val hub = DriveSignalHub(DriveSignalSource { upstream }, backgroundScope).also { it.start() }
        val tts = InstantTts()
        val vm = DriveController(hub, tts, { profile -> { g -> (listOf("${profile.language}:${g.id.name}") + g.args).joinToString(":") } }, backgroundScope)
        runCurrent()
        return Rig(vm, upstream, tts)
    }

    private suspend fun Rig.real(signal: RawSignal) = upstream.emit(signal)

    private fun schoolZone(id: String) =
        RawSignal.Navi.CautionPoints(listOf(SdkCautionPoint(id, CautionPointType.SCHOOL_ZONE, 300)))

    @Test
    fun realDriveDuringDemo_endsDemo_andRealSafetyWarningIsSpoken() = runTest(dispatcher) {
        val r = rig()
        r.real(RawSignal.Vehicle.Gear(VehicleGear.GEAR_PARK))
        runCurrent()
        r.vm.ensureSession(Personas.CARLOS) // 저장된 프로필
        r.vm.startDemo(DemoPersona.NAKAMURA)
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue("시연이 시작됨", r.tts.spoken.contains("EN:WELCOME_RIGHT_SIDE"))
        assertEquals(DemoPersona.NAKAMURA, r.vm.uiState.value.demo?.persona)

        // 시연 중 실제로 출발
        r.real(RawSignal.Vehicle.Gear(VehicleGear.GEAR_DRIVE))
        runCurrent()
        assertNull("시연이 자동으로 끝남", r.vm.uiState.value.demo)
        assertEquals(Personas.CARLOS, r.vm.uiState.value.profile)

        r.real(schoolZone("real-sz"))
        runCurrent()
        assertTrue("실제 어린이보호구역 경고", r.tts.spoken.contains("ES:SCHOOL_ZONE_AHEAD:300"))
    }

    @Test
    fun realDriveAfterDemoFinished_alsoReturnsToRealSession() = runTest(dispatcher) {
        val r = rig()
        r.real(RawSignal.Vehicle.Gear(VehicleGear.GEAR_PARK))
        runCurrent()
        r.vm.ensureSession(Personas.CARLOS)
        r.vm.startDemo(DemoPersona.NAKAMURA)
        advanceTimeBy(10 * 60_000L)
        runCurrent()
        assertEquals(true, r.vm.uiState.value.demo?.finished)

        r.real(RawSignal.Vehicle.Gear(VehicleGear.GEAR_DRIVE))
        r.real(schoolZone("real-sz"))
        runCurrent()
        assertNull(r.vm.uiState.value.demo)
        assertTrue(r.tts.spoken.contains("ES:SCHOOL_ZONE_AHEAD:300"))
    }

    @Test
    fun endSession_stopsDemoAndGuidance_untilTheScreenOpensASessionAgain() = runTest(dispatcher) {
        val r = rig()
        r.real(RawSignal.Vehicle.Gear(VehicleGear.GEAR_PARK))
        runCurrent()
        r.vm.ensureSession(Personas.CARLOS)
        r.vm.startDemo(DemoPersona.NAKAMURA)
        runCurrent()
        // 최근 앱에서 지움
        r.vm.endSession()
        runCurrent()
        assertNull(r.vm.uiState.value.demo)
        assertNull(r.vm.uiState.value.profile)
        val spokenBefore = r.tts.spoken.size
        r.real(RawSignal.Vehicle.Gear(VehicleGear.GEAR_DRIVE))
        r.real(schoolZone("after-end"))
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals("세션이 없으면 말하지 않음", spokenBefore, r.tts.spoken.size)
        // 다시 열면(화면이 저장된 프로필로) 새 세션
        r.vm.ensureSession(Personas.CARLOS)
        r.real(schoolZone("reopened"))
        runCurrent()
        assertTrue(r.tts.spoken.contains("ES:SCHOOL_ZONE_AHEAD:300"))
    }

    @Test
    fun parkedDemo_ignoresRealNaviSignals() = runTest(dispatcher) {
        val r = rig()
        r.real(RawSignal.Vehicle.Gear(VehicleGear.GEAR_PARK))
        runCurrent()
        r.vm.ensureSession(Personas.CARLOS)
        r.vm.startDemo(DemoPersona.NAKAMURA)
        runCurrent()
        r.real(schoolZone("real-sz"))
        runCurrent()
        assertFalse("정차 중 시연에는 실제 신호를 넣지 않음", r.tts.spoken.any { it.startsWith("ES:") })
        assertFalse("시연 중에도 잠금은 실제 기어(P)를 따름", r.vm.vehicle.value.locked)
    }

    @Test
    fun demoCannotStartWhileDriving() = runTest(dispatcher) {
        val r = rig()
        r.real(RawSignal.Vehicle.Gear(VehicleGear.GEAR_DRIVE))
        runCurrent()
        r.vm.ensureSession(Personas.CARLOS)
        r.vm.startDemo(DemoPersona.NAKAMURA)
        runCurrent()
        assertNull(r.vm.uiState.value.demo)
    }

    @Test
    fun savedProfileSession_existsWithoutDriveScreen_soSettingsDoNotSilenceWarnings() = runTest(dispatcher) {
        val r = rig()
        r.real(RawSignal.Vehicle.Gear(VehicleGear.GEAR_PARK))
        runCurrent()
        // 설정 화면만 열린 상태(주행 화면의 enterDrive 없이)
        r.vm.ensureSession(Personas.CARLOS)
        r.real(RawSignal.Vehicle.Gear(VehicleGear.GEAR_DRIVE))
        r.real(schoolZone("sz"))
        runCurrent()
        assertTrue(r.tts.spoken.contains("ES:WELCOME_FIRST_TIME"))
        assertTrue(r.tts.spoken.contains("ES:SCHOOL_ZONE_AHEAD:300"))
    }

    @Test
    fun startAfterOnboarding_announcesEvenForSameProfile() = runTest(dispatcher) {
        val r = rig()
        r.real(RawSignal.Vehicle.Gear(VehicleGear.GEAR_PARK))
        runCurrent()
        r.vm.ensureSession(Personas.CARLOS)
        r.vm.enterDrive(Personas.CARLOS, announceNow = true)
        runCurrent()
        assertEquals(listOf("ES:WELCOME_FIRST_TIME"), r.tts.spoken)
    }
}
