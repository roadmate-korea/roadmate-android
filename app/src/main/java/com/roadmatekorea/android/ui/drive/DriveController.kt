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
import com.roadmatekorea.android.voice.TtsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
    }

    val uiState: StateFlow<DriveUiState> = combine(session, _demo) { s, d -> s to d }.flatMapLatest { (s, demo) ->
        combine(
            _vehicle,
            speaker.current,
            s?.history ?: flowOf(emptyList()),
            combine(s?.speedLimitKmh ?: flowOf(null), s?.speedKmh ?: flowOf(null)) { limit, speed -> limit to speed },
            combine(speaker.unavailableLocales, speaker.engineFailed) { locales, failed ->
                failed || (s != null && AppLocales.localeOf(s.profile.language) in locales)
            },
        ) { vehicle, caption, history, (limit, speed), voiceUnavailable ->
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

    /** 프로세스가 끝날 때까지 보통 부르지 않는다(테스트와 정리용). */
    fun release() {
        speaker.release()
    }
}
