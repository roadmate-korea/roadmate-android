package com.roadmatekorea.android.drive

import ai.pleos.playground.navi.constants.CameraType
import ai.pleos.playground.navi.constants.CautionPointType
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import com.roadmatekorea.android.profile.model.Personas
import com.roadmatekorea.android.rules.Clock
import com.roadmatekorea.android.rules.GuidanceId
import com.roadmatekorea.android.rules.Gear
import com.roadmatekorea.android.voice.GuidanceSpeaker
import com.roadmatekorea.android.voice.TtsEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ai.pleos.playground.navi.data.CameraAlert as SdkCameraAlert
import java.util.Locale
import ai.pleos.playground.navi.data.CautionPoint as SdkCautionPoint

/** 실제 SDK 형식 신호 → 매퍼 → 엔진 → 문장 → 스피커까지 한 번에 확인한다. */
@OptIn(ExperimentalCoroutinesApi::class)
class DriveSessionTest {

    /** 말하자마자 끝나는 가짜 TTS. 말한 문장을 기록한다. */
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

    private fun TestScope.session(profile: com.roadmatekorea.android.profile.model.DriverProfile): Pair<DriveSession, InstantTts> {
        val tts = InstantTts()
        val clock = Clock { testScheduler.currentTime }
        val speaker = GuidanceSpeaker(tts, backgroundScope, clock).also { it.start() }
        // 문장 대신 ID 이름과 인자를 써서 무엇을 말했는지 바로 보이게 한다
        val s = DriveSession(profile, speaker, { g -> (listOf(g.id.name) + g.args).joinToString(":") }, Locale.US, clock)
        return s to tts
    }

    /** 끝내라고 할 때까지 말하는 중인 가짜 TTS(앞 문장이 길게 이어지는 상황). */
    private class HoldTts : TtsEngine {
        val spoken = mutableListOf<String>()
        private var listener: TtsEngine.Listener? = null
        override fun initialize(onResult: (ready: Boolean) -> Unit) = onResult(true)
        override fun setListener(listener: TtsEngine.Listener?) {
            this.listener = listener
        }
        override fun speak(text: String, locale: Locale) {
            spoken += text
            listener?.onStart()
        }
        fun finish() = listener?.onDone()
        override fun stop() = Unit
        override fun release() = Unit
    }

    @Test
    fun pendingOverSpeedWarning_isDropped_whenTheDriverSlowsDownBeforeItIsSpoken() = runTest {
        val tts = HoldTts()
        val clock = Clock { testScheduler.currentTime }
        val speaker = GuidanceSpeaker(tts, backgroundScope, clock).also { it.start() }
        val s = DriveSession(Personas.CARLOS, speaker, { g -> (listOf(g.id.name) + g.args).joinToString(":") }, Locale.US, clock)
        fun kmh(v: Int) = RawSignal.Vehicle.Speed(v / 3.6f)
        s.onSignal(RawSignal.Vehicle.Gear(VehicleGear.GEAR_DRIVE)) // 시작 인사가 재생 중(끝나지 않음)
        s.onSignal(RawSignal.Navi.SpeedLimit(50))
        s.onSignal(kmh(25))
        s.onSignal(RawSignal.Navi.CautionPoints(listOf(SdkCautionPoint("sz", CautionPointType.SCHOOL_ZONE, 300))))
        s.onSignal(RawSignal.Navi.SpeedLimit(30))
        s.onSignal(kmh(45)) // 과속 경고가 대기열에 들어감
        s.onSignal(kmh(25)) // 말하기 전에 감속
        repeat(6) { tts.finish() }
        assertTrue(tts.spoken.none { it.startsWith("SCHOOL_ZONE_OVER_SPEED") })
        assertTrue(tts.spoken.contains("SCHOOL_ZONE_SPEED_LIMIT:30"))
    }

    @Test
    fun pendingOverSpeedWarning_isUpdatedToTheCurrentSpeedAndLimit_whileStillOver() = runTest {
        val tts = HoldTts()
        val clock = Clock { testScheduler.currentTime }
        val speaker = GuidanceSpeaker(tts, backgroundScope, clock).also { it.start() }
        val s = DriveSession(Personas.CARLOS, speaker, { g -> (listOf(g.id.name) + g.args).joinToString(":") }, Locale.US, clock)
        fun kmh(v: Int) = RawSignal.Vehicle.Speed(v / 3.6f)
        s.onSignal(RawSignal.Vehicle.Gear(VehicleGear.GEAR_DRIVE)) // 시작 인사가 재생 중(끝나지 않음)
        s.onSignal(RawSignal.Navi.SpeedLimit(50))
        s.onSignal(kmh(25))
        s.onSignal(RawSignal.Navi.CautionPoints(listOf(SdkCautionPoint("sz", CautionPointType.SCHOOL_ZONE, 300))))
        s.onSignal(RawSignal.Navi.SpeedLimit(40))
        s.onSignal(kmh(45)) // "45, 제한 40"이 대기열에
        s.onSignal(RawSignal.Navi.SpeedLimit(30))
        s.onSignal(kmh(43)) // 아직 말하기 전: 지금은 "43, 제한 30"
        repeat(8) { tts.finish() }
        val over = tts.spoken.filter { it.startsWith("SCHOOL_ZONE_OVER_SPEED") }
        assertEquals(listOf("SCHOOL_ZONE_OVER_SPEED:43:30"), over)
    }

    private fun schoolCamera(limit: Int, remain: Int) =
        RawSignal.Navi.CameraAlerts(listOf(SdkCameraAlert("cam", CameraType.SPEED_CAMERA_SCHOOL_ZONE, true, limit, remain, 0, false, false)))

    @Test
    fun pendingApproachWarning_isDroppedWhenSlowingDown_andReplacedByTheInZoneWarningOnEntry() = runTest {
        // 기다리는 동안 감속·진입하면 옛 "곧 어린이보호구역, 지금 45"를 말하지 않는다
        val tts = HoldTts()
        val clock = Clock { testScheduler.currentTime }
        val speaker = GuidanceSpeaker(tts, backgroundScope, clock).also { it.start() }
        val s = DriveSession(Personas.CARLOS, speaker, { g -> (listOf(g.id.name) + g.args).joinToString(":") }, Locale.US, clock)
        fun kmh(v: Int) = RawSignal.Vehicle.Speed(v / 3.6f)
        s.onSignal(RawSignal.Vehicle.Gear(VehicleGear.GEAR_DRIVE)) // 시작 인사가 재생 중(끝나지 않음)
        s.onSignal(RawSignal.Navi.SpeedLimit(50))
        s.onSignal(kmh(45))
        s.onSignal(schoolCamera(30, 250)) // "곧 어린이보호구역, 45/30"이 대기열에
        s.onSignal(kmh(25)) // 말하기 전에 감속
        repeat(6) { tts.finish() }
        assertTrue("감속하면 버림", tts.spoken.none { it.startsWith("SCHOOL_ZONE_APPROACH_FAST") })

        val tts2 = HoldTts()
        val speaker2 = GuidanceSpeaker(tts2, backgroundScope, clock).also { it.start() }
        val s2 = DriveSession(Personas.CARLOS, speaker2, { g -> (listOf(g.id.name) + g.args).joinToString(":") }, Locale.US, clock)
        s2.onSignal(RawSignal.Vehicle.Gear(VehicleGear.GEAR_DRIVE))
        s2.onSignal(RawSignal.Navi.SpeedLimit(50))
        s2.onSignal(kmh(45))
        s2.onSignal(schoolCamera(30, 250))
        s2.onSignal(kmh(43))
        s2.onSignal(RawSignal.Navi.SpeedLimit(30)) // 말하기 전에 구역 진입이 확인됨
        repeat(8) { tts2.finish() }
        assertTrue("진입하면 구역 앞 경고는 버림", tts2.spoken.none { it.startsWith("SCHOOL_ZONE_APPROACH_FAST") })
        assertTrue("구역 안 과속 경고로 넘어감", tts2.spoken.contains("SCHOOL_ZONE_OVER_SPEED:43:30"))
    }

    @Test
    fun approachWarning_droppedWhileWaiting_comesBackWhenTheDriverSpeedsUpAgain_andIsSpokenOnce() = runTest {
        val tts = HoldTts()
        val clock = Clock { testScheduler.currentTime }
        val speaker = GuidanceSpeaker(tts, backgroundScope, clock).also { it.start() }
        val s = DriveSession(Personas.CARLOS, speaker, { g -> (listOf(g.id.name) + g.args).joinToString(":") }, Locale.US, clock)
        fun kmh(v: Int) = RawSignal.Vehicle.Speed(v / 3.6f)
        s.onSignal(RawSignal.Vehicle.Gear(VehicleGear.GEAR_DRIVE)) // 시작 인사가 재생 중
        s.onSignal(RawSignal.Navi.SpeedLimit(50))
        s.onSignal(kmh(45))
        s.onSignal(schoolCamera(30, 250))
        s.onSignal(kmh(25)) // 기다리는 중에 감속: 버림
        s.onSignal(kmh(46)) // 다시 가속: 다시 기다림
        s.onSignal(schoolCamera(30, 200))
        repeat(6) { tts.finish() }
        assertEquals(listOf("SCHOOL_ZONE_APPROACH_FAST:46:30"), tts.spoken.filter { it.startsWith("SCHOOL_ZONE_APPROACH_FAST") })
        s.onSignal(kmh(47))
        s.onSignal(schoolCamera(30, 150))
        repeat(3) { tts.finish() }
        assertEquals("말한 뒤에는 다시 말하지 않음", 1, tts.spoken.count { it.startsWith("SCHOOL_ZONE_APPROACH_FAST") })
    }

    @Test
    fun approachWarning_isRecheckedRightBeforePlaying_evenWithoutNewSignals() = runTest {
        // 신호가 끊긴 채 6초 뒤에 차례가 오면 낡은 카메라 거리로 말하지 않는다
        val tts = HoldTts()
        val clock = Clock { testScheduler.currentTime }
        val speaker = GuidanceSpeaker(tts, backgroundScope, clock).also { it.start() }
        val s = DriveSession(Personas.CARLOS, speaker, { g -> (listOf(g.id.name) + g.args).joinToString(":") }, Locale.US, clock)
        fun kmh(v: Int) = RawSignal.Vehicle.Speed(v / 3.6f)
        s.onSignal(RawSignal.Vehicle.Gear(VehicleGear.GEAR_DRIVE))
        s.onSignal(RawSignal.Navi.SpeedLimit(50))
        s.onSignal(kmh(45))
        s.onSignal(schoolCamera(30, 250))
        advanceTimeBy(6_000)
        repeat(6) { tts.finish() }
        assertTrue(tts.spoken.none { it.startsWith("SCHOOL_ZONE_APPROACH_FAST") })
    }

    @Test
    fun approachWarning_waitingWhenTheCameraDisappears_isDropped() = runTest {
        val tts = HoldTts()
        val clock = Clock { testScheduler.currentTime }
        val speaker = GuidanceSpeaker(tts, backgroundScope, clock).also { it.start() }
        val s = DriveSession(Personas.CARLOS, speaker, { g -> (listOf(g.id.name) + g.args).joinToString(":") }, Locale.US, clock)
        fun kmh(v: Int) = RawSignal.Vehicle.Speed(v / 3.6f)
        s.onSignal(RawSignal.Vehicle.Gear(VehicleGear.GEAR_DRIVE))
        s.onSignal(RawSignal.Navi.SpeedLimit(50))
        s.onSignal(kmh(45))
        s.onSignal(schoolCamera(30, 250))
        s.onSignal(RawSignal.Navi.CameraAlerts(emptyList()))
        s.onSignal(kmh(46))
        repeat(6) { tts.finish() }
        assertTrue(tts.spoken.none { it.startsWith("SCHOOL_ZONE_APPROACH_FAST") })
    }

    @Test
    fun nakamura_afterStart_hearsRightSideGreetingThenLhdIntro() = runTest {
        val (s, tts) = session(Personas.NAKAMURA)
        s.announceStart()
        assertEquals(listOf("WELCOME_RIGHT_SIDE", "LHD_INTRO"), tts.spoken)
        assertEquals(listOf("LHD_INTRO", "WELCOME_RIGHT_SIDE"), s.history.value)
    }

    @Test
    fun carlos_realSignals_driveGreetingThenRightTurnRule() = runTest {
        val (s, tts) = session(Personas.CARLOS)
        s.onSignal(RawSignal.Vehicle.Gear(VehicleGear.GEAR_PARK))
        s.onSignal(RawSignal.Vehicle.TurnSignal(TurnSignalValue.RIGHT))
        assertTrue("정차 중에는 회전 안내 없음", tts.spoken.isEmpty())
        s.onSignal(RawSignal.Vehicle.TurnSignal(TurnSignalValue.OFF))
        s.onSignal(RawSignal.Vehicle.Gear(VehicleGear.GEAR_DRIVE))
        s.onSignal(RawSignal.Vehicle.TurnSignal(TurnSignalValue.RIGHT))
        assertEquals(listOf("WELCOME_FIRST_TIME", "RIGHT_TURN_RULE"), tts.spoken)
    }

    @Test
    fun schoolZone_usesSdkDistance_andSpeedLimitIsShown() = runTest {
        val (s, tts) = session(Personas.CARLOS)
        s.onSignal(RawSignal.Navi.SpeedLimit(0))
        assertNull(s.speedLimitKmh.value)
        s.onSignal(RawSignal.Navi.SpeedLimit(50))
        s.onSignal(RawSignal.Navi.CautionPoints(listOf(SdkCautionPoint("sz", CautionPointType.SCHOOL_ZONE, 280))))
        s.onSignal(RawSignal.Navi.SpeedLimit(30))
        assertEquals(30, s.speedLimitKmh.value)
        assertEquals(listOf("SCHOOL_ZONE_AHEAD:300", "SCHOOL_ZONE_CROSSWALK", "SCHOOL_ZONE_SPEED_LIMIT:30"), tts.spoken)
    }

    @Test
    fun unavailableSignals_doNotReachEngine() = runTest {
        val (s, tts) = session(Personas.CARLOS)
        s.onSignal(RawSignal.Vehicle.Unavailable("x"))
        s.onSignal(RawSignal.Navi.Unavailable("x"))
        assertTrue(tts.spoken.isEmpty())
    }

    @Test
    fun history_keepsLatestFive() = runTest {
        val (s, _) = session(Personas.NAKAMURA)
        s.announceStart()
        s.onSignal(RawSignal.Vehicle.Gear(VehicleGear.GEAR_DRIVE))
        s.onSignal(RawSignal.Navi.CautionPoints(listOf(SdkCautionPoint("a", CautionPointType.SCHOOL_ZONE, 100))))
        s.onSignal(RawSignal.Vehicle.TurnSignal(TurnSignalValue.LEFT))
        assertEquals(DriveSession.HISTORY_SIZE, s.history.value.size)
        assertEquals("LHD_MIRROR_CHECK", s.history.value.first())
    }

    // ---- 잠금 상태: 정상적으로 확인된 P에서만 조작 허용 ----

    @Test
    fun vehicleStatus_unlockedOnlyOnConfirmedPark() {
        var v = VehicleStatus()
        assertTrue("첫 기어 전에는 잠금", v.locked)
        v = v.on(RawSignal.Vehicle.Gear(VehicleGear.GEAR_PARK))
        assertFalse(v.locked)
        v = v.on(RawSignal.Vehicle.Gear(VehicleGear.GEAR_DRIVE))
        assertTrue(v.locked)
        v = v.on(RawSignal.Vehicle.Gear(VehicleGear.GEAR_REVERSE))
        assertTrue(v.locked)
        // 시연 신호나 내비 신호는 잠금에 영향 없음
        assertEquals(Gear.REVERSE, v.on(RawSignal.Navi.SpeedLimit(30)).gear)
    }

    @Test
    fun vehicleStatus_errorBeforeAnyGear_locksWithBanner() {
        val v = VehicleStatus().on(RawSignal.Vehicle.Unavailable("init"))
        assertTrue(v.locked)
        assertTrue(v.showUnavailableBanner)
    }

    @Test
    fun vehicleStatus_errorAfterPark_locks_untilValidGearArrives() {
        var v = VehicleStatus().on(RawSignal.Vehicle.Gear(VehicleGear.GEAR_PARK))
        v = v.on(RawSignal.Vehicle.Unavailable("gear: x"))
        assertTrue("P 뒤 기어 오류면 잠금", v.locked)
        assertTrue(v.showUnavailableBanner)
        v = v.on(RawSignal.Vehicle.Gear(VehicleGear.GEAR_DRIVE))
        assertTrue(v.locked)
        assertFalse("정상 기어를 받으면 오류 해제", v.showUnavailableBanner)
        v = v.on(RawSignal.Vehicle.Gear(VehicleGear.GEAR_PARK))
        assertFalse(v.locked)
    }

    @Test
    fun guidanceIdsUsed_areKnown() {
        // 테스트가 쓰는 ID 이름이 실제 enum과 맞는지(이름 변경 시 테스트가 조용히 통과하지 않게)
        listOf("WELCOME_RIGHT_SIDE", "LHD_INTRO", "WELCOME_FIRST_TIME", "RIGHT_TURN_RULE", "LHD_MIRROR_CHECK")
            .forEach { GuidanceId.valueOf(it) }
    }
}
