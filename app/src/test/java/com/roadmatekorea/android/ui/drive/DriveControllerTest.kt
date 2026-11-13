package com.roadmatekorea.android.ui.drive

import ai.pleos.playground.navi.constants.CautionPointType
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import android.car.VehicleGear
import com.roadmatekorea.android.demo.DemoPersona
import com.roadmatekorea.android.drive.DriveSignalHub
import com.roadmatekorea.android.drive.DriveSignalSource
import com.roadmatekorea.android.drive.RawSignal
import com.roadmatekorea.android.profile.model.Personas
import com.roadmatekorea.android.rules.Guidance
import com.roadmatekorea.android.rules.GuidanceId
import com.roadmatekorea.android.voice.SpeechInput
import com.roadmatekorea.android.voice.TtsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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

    /** 질문을 받으면 기다렸다가 테스트가 [answer]나 [fail]로 결과를 준다. */
    private class FakeSpeech : SpeechInput {
        var listener: SpeechInput.Listener? = null
        var locale: Locale? = null
        var stops = 0
        val prepared = mutableListOf<Locale>()
        override fun prepare(locale: Locale) {
            prepared += locale
        }
        val all = mutableListOf<SpeechInput.Listener>()
        var autoListening = true
        override fun listen(locale: Locale, listener: SpeechInput.Listener, audio: ByteArray?) {
            this.locale = locale
            this.listener = listener
            all += listener
            if (autoListening) listener.onListening()
        }
        override fun stop() {
            stops++
            listener = null
        }
        override fun release() = Unit
        fun answer(text: String) = listener!!.onFinal(text)
        fun fail() = listener!!.onError()
    }

    private class Rig(val vm: DriveController, val upstream: MutableSharedFlow<RawSignal>, val tts: InstantTts, val speech: FakeSpeech, val mic: MutableStateFlow<Boolean>)

    private fun TestScope.rig(): Rig {
        val upstream = MutableSharedFlow<RawSignal>()
        val hub = DriveSignalHub(DriveSignalSource { upstream }, backgroundScope).also { it.start() }
        val tts = InstantTts()
        val speech = FakeSpeech()
        val mic = MutableStateFlow(true)
        val vm = DriveController(
            hub, tts, { profile -> { g -> (listOf("${profile.language}:${g.id.name}") + g.args).joinToString(":") } }, backgroundScope,
            speechInput = speech, micPermission = mic, listenTimeoutMs = 10_000,
        )
        runCurrent()
        return Rig(vm, upstream, tts, speech, mic)
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
    // ---- 음성 질문 ----

    private suspend fun TestScope.drivingCarlos(r: Rig) {
        r.real(RawSignal.Vehicle.Gear(VehicleGear.GEAR_PARK))
        runCurrent()
        r.vm.ensureSession(Personas.CARLOS)
        r.real(RawSignal.Vehicle.Gear(VehicleGear.GEAR_DRIVE))
        runCurrent()
    }

    @Test
    fun questionWhileDriving_isAnsweredInTheProfileLanguage() = runTest(dispatcher) {
        val r = rig()
        drivingCarlos(r)
        assertTrue("주행 중 잠금", r.vm.vehicle.value.locked)
        assertTrue(r.vm.uiState.value.micAvailable)
        r.vm.toggleQuestion()
        runCurrent()
        assertTrue(r.vm.uiState.value.question.listening)
        assertEquals("es-US", r.speech.locale?.toLanguageTag())
        r.speech.answer("¿Puedo girar a la derecha en rojo?")
        runCurrent()
        assertTrue(r.tts.spoken.contains("ES:RIGHT_TURN_RULE"))
        val q = r.vm.uiState.value.question
        assertFalse(q.listening)
        assertEquals("¿Puedo girar a la derecha en rojo?", q.asked)
        assertEquals(Guidance(GuidanceId.RIGHT_TURN_RULE), q.answer)
    }

    @Test
    fun speedLimitQuestion_usesTheNaviLimit() = runTest(dispatcher) {
        val r = rig()
        drivingCarlos(r)
        r.real(RawSignal.Navi.SpeedLimit(50))
        runCurrent()
        r.vm.toggleQuestion()
        r.speech.answer("¿cuál es el límite de velocidad?")
        runCurrent()
        assertTrue(r.tts.spoken.contains("ES:ANSWER_SPEED_LIMIT:50"))
    }

    @Test
    fun silenceTimeoutOrError_answersNotHeard() = runTest(dispatcher) {
        val r = rig()
        drivingCarlos(r)
        r.vm.toggleQuestion()
        runCurrent()
        advanceTimeBy(10_001)
        runCurrent()
        assertEquals(1, r.speech.stops)
        assertTrue(r.tts.spoken.contains("ES:ANSWER_NOT_HEARD"))
        assertFalse(r.vm.uiState.value.question.listening)
        r.tts.spoken.clear()
        r.vm.toggleQuestion()
        r.speech.fail()
        runCurrent()
        assertEquals(listOf("ES:ANSWER_NOT_HEARD"), r.tts.spoken)
    }

    @Test
    fun guidanceDuringListening_waits_butSafetyWarningEndsTheQuestion() = runTest(dispatcher) {
        val r = rig()
        drivingCarlos(r)
        r.tts.spoken.clear()
        r.vm.toggleQuestion()
        runCurrent()
        // 규칙 안내(우회전)는 듣는 동안 기다린다
        r.real(RawSignal.Vehicle.TurnSignal(TurnSignalValue.RIGHT))
        runCurrent()
        assertEquals(emptyList<String>(), r.tts.spoken)
        // 안전 경고는 듣기를 끝내고 바로 말한다. 질문에는 답하지 않는다
        r.real(schoolZone("sz"))
        runCurrent()
        assertFalse(r.vm.uiState.value.question.listening)
        assertEquals(1, r.speech.stops)
        assertEquals("ES:SCHOOL_ZONE_AHEAD:300", r.tts.spoken.first())
        assertTrue(r.tts.spoken.contains("ES:RIGHT_TURN_RULE"))
        assertFalse(r.tts.spoken.any { it.contains("ANSWER") })
    }

    @Test
    fun answer_comesBefore_lessUrgentGuidanceQueuedWhileListening() = runTest(dispatcher) {
        val r = rig()
        r.real(RawSignal.Vehicle.Gear(VehicleGear.GEAR_PARK))
        runCurrent()
        r.vm.ensureSession(Personas.NAKAMURA)
        r.vm.toggleQuestion()
        runCurrent()
        // 듣는 동안 출발: 시작 인사와 적응 안내가 쌓인다(규칙보다 덜 급함)
        r.real(RawSignal.Vehicle.Gear(VehicleGear.GEAR_DRIVE))
        runCurrent()
        assertEquals(emptyList<String>(), r.tts.spoken)
        r.speech.answer("what about bus lanes")
        runCurrent()
        assertEquals("EN:ANSWER_BUS_LANE", r.tts.spoken.first())
        assertTrue(r.tts.spoken.contains("EN:WELCOME_RIGHT_SIDE"))
    }

    @Test
    fun tappingAgainWhileListening_cancelsWithoutAnswer() = runTest(dispatcher) {
        val r = rig()
        drivingCarlos(r)
        r.tts.spoken.clear()
        r.vm.toggleQuestion()
        r.vm.toggleQuestion()
        runCurrent()
        assertFalse(r.vm.uiState.value.question.listening)
        assertEquals(1, r.speech.stops)
        advanceTimeBy(20_000)
        runCurrent()
        assertEquals(emptyList<String>(), r.tts.spoken)
    }

    @Test
    fun engineIsPrepared_forTheSessionLanguage_onceTheMicIsAllowed() = runTest(dispatcher) {
        val r = rig()
        r.mic.value = false
        runCurrent()
        assertEquals("세션이 없으면 준비하지 않음", emptyList<Locale>(), r.speech.prepared)
        r.real(RawSignal.Vehicle.Gear(VehicleGear.GEAR_PARK))
        runCurrent()
        r.vm.ensureSession(Personas.CARLOS)
        runCurrent()
        assertEquals("권한이 없으면 준비하지 않음", emptyList<Locale>(), r.speech.prepared)
        r.mic.value = true
        runCurrent()
        assertEquals(listOf("es-US"), r.speech.prepared.map { it.toLanguageTag() })
    }

    @Test
    fun screenSaysOneMoment_untilTheEngineIsReallyListening() = runTest(dispatcher) {
        val r = rig()
        drivingCarlos(r)
        r.speech.autoListening = false
        r.vm.toggleQuestion()
        runCurrent()
        assertTrue(r.vm.uiState.value.question.listening)
        assertTrue("아직 듣기 전", r.vm.uiState.value.question.preparing)
        r.speech.listener!!.onListening()
        runCurrent()
        assertFalse("이제 듣는 중", r.vm.uiState.value.question.preparing)
        // 제한 시간은 실제로 듣기 시작한 때부터 잰다
        advanceTimeBy(9_000)
        runCurrent()
        assertTrue(r.vm.uiState.value.question.listening)
    }

    @Test
    fun lateSignalsFromACancelledQuestion_doNotTouchTheNextQuestion() = runTest(dispatcher) {
        val r = rig()
        drivingCarlos(r)
        r.tts.spoken.clear()
        r.vm.toggleQuestion()
        r.vm.toggleQuestion() // 취소
        r.vm.toggleQuestion() // 다시 질문
        runCurrent()
        val (old, new) = r.speech.all
        // 앞 질문의 늦은 신호
        old.onPartial("¿puedo")
        old.onFinal("¿hay carril bus?")
        runCurrent()
        assertTrue("새 질문은 계속 듣는 중", r.vm.uiState.value.question.listening)
        assertEquals("", r.vm.uiState.value.question.partial)
        assertEquals(emptyList<String>(), r.tts.spoken)
        // 앞 질문의 늦은 시작 신호가 새 질문의 제한 시간을 바꾸지 않는다
        advanceTimeBy(5_000)
        old.onListening()
        advanceTimeBy(5_001)
        runCurrent()
        assertTrue("새 질문은 자기 제한 시간(10초)에 끝남", r.tts.spoken.contains("ES:ANSWER_NOT_HEARD"))
        assertFalse(r.vm.uiState.value.question.listening)
        new.onFinal("¿puedo girar a la derecha?") // 이미 끝난 질문의 결과도 무시
        runCurrent()
        assertFalse(r.tts.spoken.contains("ES:RIGHT_TURN_RULE"))
    }

    @Test
    fun micIsRefused_whileASafetyWarningIsWaiting() = runTest(dispatcher) {
        val upstream = MutableSharedFlow<RawSignal>()
        val hub = DriveSignalHub(DriveSignalSource { upstream }, backgroundScope).also { it.start() }
        // 말을 끝내지 않는 TTS: 첫 안내가 재생 중인 동안 안전 경고가 뒤에서 기다린다
        val tts = object : TtsEngine {
            val spoken = mutableListOf<String>()
            override fun initialize(onResult: (ready: Boolean) -> Unit) = onResult(true)
            override fun setListener(listener: TtsEngine.Listener?) = Unit
            override fun speak(text: String, locale: Locale) { spoken += text }
            override fun stop() = Unit
            override fun release() = Unit
        }
        val speech = FakeSpeech()
        val vm = DriveController(
            hub, tts, { profile -> { g -> (listOf("${profile.language}:${g.id.name}") + g.args).joinToString(":") } }, backgroundScope,
            speechInput = speech, micPermission = MutableStateFlow(true),
        )
        runCurrent()
        upstream.emit(RawSignal.Vehicle.Gear(VehicleGear.GEAR_PARK))
        runCurrent()
        vm.ensureSession(Personas.CARLOS)
        upstream.emit(RawSignal.Vehicle.Gear(VehicleGear.GEAR_DRIVE)) // 시작 인사 재생 중
        upstream.emit(schoolZone("sz")) // 경고는 대기
        runCurrent()
        vm.toggleQuestion()
        runCurrent()
        assertFalse(vm.uiState.value.question.listening)
        assertEquals(0, speech.all.size)
    }

    @Test
    fun helpSpeech_usesTheProfileLanguage_andKoreanForThePhraseCard() = runTest(dispatcher) {
        val r = rig()
        r.real(RawSignal.Vehicle.Gear(VehicleGear.GEAR_PARK))
        runCurrent()
        r.vm.ensureSession(Personas.CARLOS)
        r.tts.spoken.clear()
        r.vm.sayHelp(Guidance(GuidanceId.HELP_ACCIDENT_STEPS))
        r.vm.sayHelp(Guidance(GuidanceId.PHRASE_ACCIDENT_HELP), inKorean = true)
        runCurrent()
        assertEquals(listOf("ES:HELP_ACCIDENT_STEPS", "KO:PHRASE_ACCIDENT_HELP"), r.tts.spoken)
    }

    @Test
    fun leavingPark_stopsHelpSpeech_evenWithoutTheScreen() = runTest(dispatcher) {
        val upstream = MutableSharedFlow<RawSignal>()
        val hub = DriveSignalHub(DriveSignalSource { upstream }, backgroundScope).also { it.start() }
        // 말을 끝내지 않는 TTS: 도움 음성이 재생 중인 상태를 만든다
        val tts = object : TtsEngine {
            val spoken = mutableListOf<String>()
            var stops = 0
            override fun initialize(onResult: (ready: Boolean) -> Unit) = onResult(true)
            override fun setListener(listener: TtsEngine.Listener?) = Unit
            override fun speak(text: String, locale: Locale) { spoken += text }
            override fun stop() { stops++ }
            override fun release() = Unit
        }
        val vm = DriveController(hub, tts, { profile -> { g -> "${profile.language}:${g.id.name}" } }, backgroundScope)
        runCurrent()
        upstream.emit(RawSignal.Vehicle.Gear(VehicleGear.GEAR_PARK))
        runCurrent()
        vm.ensureSession(Personas.CARLOS)
        vm.sayHelp(Guidance(GuidanceId.HELP_ACCIDENT_STEPS))
        runCurrent()
        assertEquals(listOf("ES:HELP_ACCIDENT_STEPS"), tts.spoken)
        // 화면은 백그라운드(내비를 앞에 띄움). 기어만 D로
        upstream.emit(RawSignal.Vehicle.Gear(VehicleGear.GEAR_DRIVE))
        runCurrent()
        assertEquals("도움 음성을 멈춤", 1, tts.stops)
        assertEquals("그다음은 주행 시작 안내", "ES:WELCOME_FIRST_TIME", tts.spoken.last())
    }

    @Test
    fun micIsHidden_withoutPermission_orWithoutSession() = runTest(dispatcher) {
        val r = rig()
        assertFalse("세션 없음", r.vm.uiState.value.micAvailable)
        drivingCarlos(r)
        assertTrue(r.vm.uiState.value.micAvailable)
        r.mic.value = false
        runCurrent()
        assertFalse(r.vm.uiState.value.micAvailable)
        r.vm.toggleQuestion() // 버튼이 없지만 불려도 세션 기준으로만 동작
    }

    @Test
    fun repeatQuestion_saysTheLastDrivingGuidanceAgain() = runTest(dispatcher) {
        val r = rig()
        drivingCarlos(r)
        r.real(schoolZone("sz"))
        runCurrent()
        r.tts.spoken.clear()
        r.vm.toggleQuestion()
        r.speech.answer("repite por favor")
        runCurrent()
        // 어린이보호구역 경고 묶음의 마지막 안내(횡단보도 일시정지)를 다시 말한다
        assertEquals(listOf("ES:SCHOOL_ZONE_CROSSWALK"), r.tts.spoken)
    }

    @Test
    fun helpAndInterpreterSpeech_areIgnored_whenNotInPark() = runTest(dispatcher) {
        val r = rig()
        drivingCarlos(r)
        r.tts.spoken.clear()
        // 화면보다 늦게 온 요청(기어가 이미 D)
        r.vm.sayHelp(Guidance(GuidanceId.HELP_ACCIDENT_STEPS))
        r.vm.sayInterpreter("가득 넣어 주세요.", Locale.KOREA)
        runCurrent()
        assertTrue(r.tts.spoken.isEmpty())
    }

    @Test
    fun interpreterSpeech_isSpokenAsIs_inParkWithASession() = runTest(dispatcher) {
        val r = rig()
        r.real(RawSignal.Vehicle.Gear(VehicleGear.GEAR_PARK))
        runCurrent()
        r.vm.ensureSession(Personas.CARLOS)
        runCurrent()
        r.tts.spoken.clear()
        r.vm.sayInterpreter("가득 넣어 주세요.", Locale.KOREA)
        runCurrent()
        assertEquals(listOf("가득 넣어 주세요."), r.tts.spoken)
    }

    @Test
    fun interpreterHold_queuesOtherSpeech_untilReleased_andCancellingNoQuestionKeepsIt() = runTest(dispatcher) {
        val r = rig()
        r.real(RawSignal.Vehicle.Gear(VehicleGear.GEAR_PARK))
        runCurrent()
        r.vm.ensureSession(Personas.CARLOS)
        runCurrent()
        r.tts.spoken.clear()
        assertTrue(r.vm.holdForInterpreter {})
        // 통역 화면에서 다시 부르는 정리(음성 질문이 없음)가 통역의 멈춤을 풀면 안 된다
        r.vm.prepareForInterpreter()
        r.vm.sayHelp(Guidance(GuidanceId.HELP_ACCIDENT_STEPS))
        runCurrent()
        assertTrue("통역이 듣는 동안 쌓아 둠", r.tts.spoken.isEmpty())
        r.vm.releaseInterpreterHold()
        runCurrent()
        assertEquals(listOf("ES:HELP_ACCIDENT_STEPS"), r.tts.spoken)
    }
}
