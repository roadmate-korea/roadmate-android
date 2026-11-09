package com.roadmatekorea.android.ui.drive

import com.roadmatekorea.android.demo.DemoPersona
import com.roadmatekorea.android.demo.DemoSignalSource
import com.roadmatekorea.android.drive.DriveSession
import com.roadmatekorea.android.drive.DriveSignalHub
import com.roadmatekorea.android.drive.VehicleStatus
import com.roadmatekorea.android.i18n.AppLocales
import com.roadmatekorea.android.profile.model.DriverProfile
import com.roadmatekorea.android.rules.Guidance
import com.roadmatekorea.android.voice.GuidanceSpeaker
import com.roadmatekorea.android.voice.SpeechInput
import com.roadmatekorea.android.voice.TtsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 시연 진행 상태. */
data class DemoStatus(val persona: DemoPersona, val finished: Boolean = false)

/** 주행 화면 상태. */
data class DriveUiState(
    /** 지금 세션의 프로필. 시연 중이면 시연 페르소나의 프로필, 아니면 저장된 프로필. 세션이 없으면 null. */
    val profile: DriverProfile? = null,
    val demo: DemoStatus? = null,
    val vehicle: VehicleStatus = VehicleStatus(),
    val caption: String? = null,
    /** 지금 말하는(또는 자막으로 보여 주는) 안내. 카드의 그림과 요약 문구를 고르는 데 쓴다. */
    val guidance: Guidance? = null,
    /** false면 음성 없이 자막만 나가는 중이다. 카드는 요약 대신 전체 문장을 보여 준다. */
    val guidanceVoiced: Boolean = true,
    val history: List<String> = emptyList(),
    val speedLimitKmh: Int? = null,
    /** 차량 속도(km/h). 읽을 수 없으면 null이고 화면에 보이지 않는다. */
    val speedKmh: Int? = null,
    val voiceUnavailable: Boolean = false,
    /** 음성 질문을 받을 수 있음(마이크 권한과 세션이 있음). false면 마이크 버튼을 숨긴다. */
    val micAvailable: Boolean = false,
    val question: QuestionUi = QuestionUi(),
)

/** 음성 질문 상태. */
data class QuestionUi(
    val listening: Boolean = false,
    /** 마이크를 눌렀지만 아직 실제로 듣기 전(엔진 준비, 앞 질문 정리). 화면은 "잠시만요"를 보여 준다. */
    val preparing: Boolean = false,
    /** 듣는 중의 중간 결과. */
    val partial: String = "",
    /** 마지막 질문과 그 답. 지금 말하는 안내가 [answer]이면 화면에 질문을 함께 보여 준다. */
    val asked: String? = null,
    val answer: Guidance? = null,
)

/**
 * 앱 프로세스에 하나. [DriveSignalHub]의 실제 차량·내비 신호를 받아
 * ① 화면 잠금용 실제 기어 상태를 갱신하고 ② 안내 세션에 넣는다.
 *
 * 화면(Activity)과 수명이 따로다. 내비를 앞에 띄우거나 화면을 닫아도 세션과 음성은 계속되며,
 * 프로세스가 얼지 않게 DriveGuidanceService(포그라운드 서비스)가 붙잡아 둔다. 화면은 다시 열리면 이 상태를 그대로 그린다.
 *
 * TTS 엔진은 여기서 한 번 쓰고 [release]에서 한 번 해제한다.
 * Android 의존성(TTS 엔진, 언어별 문장)은 주입받아 JVM에서 테스트할 수 있다.
 *
 * @param textFor 프로필 언어로 안내 문장을 만드는 함수를 돌려준다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DriveController(
    private val hub: DriveSignalHub,
    ttsEngine: TtsEngine,
    private val textFor: (DriverProfile) -> (Guidance) -> String,
    private val scope: CoroutineScope,
    private val speechInput: SpeechInput? = null,
    micPermission: StateFlow<Boolean> = MutableStateFlow(false),
    private val listenTimeoutMs: Long = LISTEN_TIMEOUT_MS,
) {

    private val speaker = GuidanceSpeaker(ttsEngine, scope).also { it.start() }

    // 허브가 보관한 마지막 기어로 시작한다(이 화면보다 먼저 들어온 신호). 그 뒤 오류가 있었다면 잠근다.
    private val _vehicle = MutableStateFlow(
        (hub.lastGear.value?.let(VehicleStatus()::on) ?: VehicleStatus())
            .let { if (hub.vehicleUnavailableSinceLastGear()) it.copy(error = true) else it },
    )

    /** 실제 차량 상태(잠금 판단용). 시연 신호는 여기에 들어오지 않는다. */
    val vehicle: StateFlow<VehicleStatus> = _vehicle.asStateFlow()

    private val session = MutableStateFlow<DriveSession?>(null)

    private val _demo = MutableStateFlow<DemoStatus?>(null)
    private var demoJob: Job? = null

    private val _question = MutableStateFlow(QuestionUi())
    private var listenTimeout: Job? = null

    /** 지금 듣고 있는 질문의 표시. null이면 듣고 있지 않다. */
    private var question: Any? = null

    /** 마지막으로 받은 저장된 프로필. 시연이 끝나면 이 프로필의 실제 세션으로 돌아간다. */
    private var savedProfile: DriverProfile? = null

    init {
        scope.launch {
            hub.signals.collect { signal ->
                // 잠금은 시연 중에도 항상 실제 차량을 따른다
                _vehicle.value = _vehicle.value.on(signal)
                // 시연 중에 실제로 출발하면(P를 벗어남) 시연을 바로 끝내고 실제 세션으로 돌아간다.
                // 실제 안전 경고가 시연 때문에 막히면 안 된다.
                if (_demo.value != null && _vehicle.value.locked) exitDemoToRealSession()
                // 시연 중(정차)에는 실제 신호를 세션에 넣지 않는다(입력원은 한 번에 하나)
                if (_demo.value == null) session.value?.onSignal(signal)
            }
        }
        // 마이크 권한이 있으면 지금 세션 언어의 음성 엔진을 미리 준비해 첫 질문이 늦지 않게 한다
        if (speechInput != null) {
            scope.launch {
                combine(micPermission, session) { mic, s -> if (mic && s != null) AppLocales.localeOf(s.profile.language) else null }
                    .collect { locale -> locale?.let(speechInput::prepare) }
            }
        }
    }

    val uiState: StateFlow<DriveUiState> = combine(session, _demo) { s, d -> s to d }.flatMapLatest { (s, demo) ->
        combine(
            _vehicle,
            speaker.current,
            s?.history ?: flowOf(emptyList()),
            combine(s?.speedLimitKmh ?: flowOf(null), s?.speedKmh ?: flowOf(null)) { limit, speed -> limit to speed },
            combine(speaker.unavailableLocales, speaker.engineFailed, _question, micPermission) { locales, failed, question, mic ->
                Triple(failed || (s != null && AppLocales.localeOf(s.profile.language) in locales), question, mic && s != null && speechInput != null)
            },
        ) { vehicle, caption, history, (limit, speed), (voiceUnavailable, question, micAvailable) ->
            DriveUiState(
                profile = s?.profile,
                demo = demo,
                vehicle = vehicle,
                caption = caption?.text,
                guidance = caption?.guidance,
                guidanceVoiced = caption?.voiced ?: true,
                history = history,
                speedLimitKmh = limit,
                speedKmh = speed,
                voiceUnavailable = voiceUnavailable,
                micAvailable = micAvailable,
                question = question,
            )
        }
    }.stateIn(scope, SharingStarted.Eagerly, DriveUiState())

    /**
     * 주행 화면에 들어올 때 부른다. 프로필이 바뀌었으면 새 세션을 연다.
     * [announceNow]: 온보딩 START 직후처럼 바로 시작 안내를 낼지(같은 프로필이어도 새 세션). 아니면 기어 D가 될 때 엔진이 낸다.
     *
     * 설정 화면에 가도 세션은 닫지 않는다. 설정을 고치는 중에 출발해도 저장된 프로필로 안전 경고가 계속 나가야 한다.
     */
    fun enterDrive(profile: DriverProfile, announceNow: Boolean) {
        savedProfile = profile
        if (_demo.value != null) return // 시연 중(정차)에는 시연 세션을 유지한다
        val current = session.value
        if (!announceNow && current != null && current.profile == profile) return
        cancelQuestion()
        speaker.stopAll()
        val next = newSession(profile)
        session.value = next
        // 엔진이 지금 기어를 알게 한다. 이미 D라면 이것이 주행 시작이 되어 시작 안내가 나간다.
        hub.lastGear.value?.let(next::onSignal)
        if (announceNow) next.announceStart()
    }

    /**
     * 저장된 프로필의 세션이 없으면 연다(시작 안내 없이). 설정 화면에 있거나 프로세스가 설정 화면으로
     * 복원된 경우에도 저장된 프로필로 안전 경고가 나가게 하려는 것이다. 편집 중인 값은 쓰지 않는다.
     */
    fun ensureSession(saved: DriverProfile) {
        savedProfile = saved
        if (session.value == null) enterDrive(saved, announceNow = false)
    }

    /**
     * 시연 시작(실제 기어가 P일 때만). [persona] 프로필로 새 세션을 열고 DemoRoute를 재생한다.
     * 시연 세션에는 실제 신호를 넣지 않으며, 판단 로직은 실제 주행과 같다.
     */
    fun startDemo(persona: DemoPersona) {
        if (_vehicle.value.locked) return
        cancelDemo()
        cancelQuestion()
        speaker.stopAll()
        val demoSession = newSession(persona.profile)
        _demo.value = DemoStatus(persona)
        session.value = demoSession
        demoJob = scope.launch {
            DemoSignalSource(awaitIdle = { speaker.idle.first { it } }).signals().collect(demoSession::onSignal)
            speaker.idle.first { it }
            _demo.update { it?.copy(finished = true) }
        }
    }

    /** 시연을 끝내고 저장된 프로필의 실제 주행 세션으로 돌아간다(실제 기어가 P일 때만, 화면 버튼). */
    fun stopDemo(saved: DriverProfile) {
        if (_vehicle.value.locked || _demo.value == null) return
        savedProfile = saved
        exitDemoToRealSession()
    }

    private fun exitDemoToRealSession() {
        cancelDemo()
        cancelQuestion()
        speaker.stopAll()
        session.value = null
        savedProfile?.let { enterDrive(it, announceNow = false) }
    }

    private fun cancelDemo() {
        demoJob?.cancel()
        demoJob = null
        _demo.value = null
    }

    private fun newSession(profile: DriverProfile) = DriveSession(
        profile = profile,
        speaker = speaker,
        textOf = textFor(profile),
        locale = AppLocales.localeOf(profile.language),
    )

    /**
     * 사용자가 최근 앱에서 RoadMate를 지웠을 때: 시연과 안내 세션을 끝내고 음성을 멈춘다.
     * 신호 수집과 TTS 엔진은 그대로 두어, 앱을 다시 열면 화면이 저장된 프로필로 새 세션을 연다.
     */
    fun endSession() {
        cancelDemo()
        cancelQuestion()
        speaker.stopAll()
        session.value = null
        savedProfile = null
    }

    /**
     * 마이크 버튼(주행 중에도 받는 유일한 버튼). 듣는 중이면 멈춘다(답하지 않음).
     * 안전 경고를 말하는 중이면 받지 않는다. 듣는 동안 다른 안내는 쌓아 두었다가 답 뒤에 말한다.
     * [audio]: 디버그 빌드의 시연용 PCM(마이크 대신 실제 STT에 넣는다).
     */
    fun toggleQuestion(audio: ByteArray? = null) {
        val input = speechInput ?: return
        val s = session.value ?: return
        if (_question.value.listening) {
            cancelQuestion()
            return
        }
        if (!speaker.hold(onSafetyInterrupt = ::cancelQuestion)) return
        // 질문마다 새 표시를 둔다. 앞 질문에서 늦게 온 신호가 이 질문의 화면, 제한 시간, 답을 바꾸지 못하게
        val q = Any()
        question = q
        _question.value = QuestionUi(listening = true, preparing = true)
        // 엔진이 준비될 때까지 넉넉히 기다리고, 실제로 듣기 시작하면 그때부터 제한 시간을 잰다
        startListenTimeout(q, s, input, listenTimeoutMs * 2)
        input.listen(
            AppLocales.localeOf(s.profile.language),
            object : SpeechInput.Listener {
                override fun onListening() {
                    if (question !== q) return
                    _question.update { it.copy(preparing = false) }
                    startListenTimeout(q, s, input, listenTimeoutMs)
                }

                override fun onPartial(text: String) {
                    if (question === q) _question.update { it.copy(partial = text) }
                }

                override fun onFinal(text: String) = finishQuestion(q, s, text)
                override fun onError() = finishQuestion(q, s, null)
            },
            audio,
        )
    }

    private fun startListenTimeout(q: Any, asked: DriveSession, input: SpeechInput, timeoutMs: Long) {
        listenTimeout?.cancel()
        listenTimeout = scope.launch {
            delay(timeoutMs)
            if (question !== q) return@launch
            input.stop()
            finishQuestion(q, asked, null)
        }
    }

    private fun finishQuestion(q: Any, asked: DriveSession, text: String?) {
        if (question !== q) return
        question = null
        listenTimeout?.cancel()
        listenTimeout = null
        _question.value = QuestionUi()
        // 듣는 사이 세션이 바뀌었으면 답하지 않는다
        if (session.value !== asked) {
            speaker.resume()
            return
        }
        // 답을 먼저 넣고 나서 멈춤을 푼다. 그래야 듣는 동안 쌓인 덜 급한 안내보다 답이 먼저 나간다
        val answer = asked.ask(text)
        speaker.resume()
        _question.value = QuestionUi(asked = text?.takeIf { it.isNotBlank() }, answer = answer)
    }

    /** 듣기를 그만둔다(답하지 않음). 안전 경고가 오거나 세션이 바뀔 때도 부른다. */
    private fun cancelQuestion() {
        listenTimeout?.cancel()
        listenTimeout = null
        // 듣는 중이었는지는 화면 상태가 아니라 질문 표시로 판단한다
        if (question != null) speechInput?.stop()
        question = null
        _question.value = QuestionUi()
        speaker.resume()
    }

    /** 프로세스가 끝날 때까지 보통 부르지 않는다(테스트와 정리용). */
    fun release() {
        speechInput?.release()
        speaker.release()
    }

    private companion object {
        /** 듣기 제한 시간. 지나면 못 알아들었다고 답한다. */
        const val LISTEN_TIMEOUT_MS = 10_000L
    }
}
