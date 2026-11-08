package com.roadmatekorea.android.ui.drive

import android.app.Application
import android.util.Log
import com.roadmatekorea.android.BuildConfig
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.roadmatekorea.android.drive.DriveSession
import com.roadmatekorea.android.drive.DriveSignalHub
import com.roadmatekorea.android.drive.VehicleStatus
import com.roadmatekorea.android.demo.DemoPersona
import com.roadmatekorea.android.demo.DemoSignalSource
import com.roadmatekorea.android.i18n.AppLocales
import com.roadmatekorea.android.i18n.GuidanceStrings
import com.roadmatekorea.android.i18n.withLanguage
import com.roadmatekorea.android.profile.model.DriverProfile
import com.roadmatekorea.android.voice.GuidanceSpeaker
import com.roadmatekorea.android.voice.PleosTtsEngine
import com.roadmatekorea.android.voice.TtsEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 시연 진행 상태. */
data class DemoStatus(val persona: DemoPersona, val finished: Boolean = false)

/** 주행 화면 상태. */
data class DriveUiState(
    /** 지금 세션의 프로필. 시연 중이면 시연 페르소나의 프로필. */
    val profile: DriverProfile? = null,
    val demo: DemoStatus? = null,
    val vehicle: VehicleStatus = VehicleStatus(),
    val caption: String? = null,
    val history: List<String> = emptyList(),
    val speedLimitKmh: Int? = null,
    val voiceUnavailable: Boolean = false,
)

/**
 * 앱 화면(Activity) 하나에 하나. [DriveSignalHub]의 실제 차량·내비 신호를 받아
 * ① 화면 잠금용 실제 기어 상태를 갱신하고 ② 주행 화면이 열려 있으면 세션에 넣는다.
 * 앱이 뒤로 가도(다른 앱이 앞에 있어도) 화면이 살아 있는 동안 안내는 계속된다.
 *
 * TTS 엔진은 여기서 한 번 만들고 [onCleared]에서 한 번 해제한다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DriveViewModel(
    private val app: Application,
    private val hub: DriveSignalHub,
    ttsEngine: TtsEngine = PleosTtsEngine(app),
) : AndroidViewModel(app) {

    private val speaker = GuidanceSpeaker(ttsEngine, viewModelScope).also { it.start() }

    // 허브가 보관한 마지막 기어와 사용 불가 여부로 시작한다(이 화면보다 먼저 들어온 신호)
    private val _vehicle = MutableStateFlow(
        // 마지막 기어가 있으면 그 값으로 시작한다. 그 뒤에 오류가 있었다면 잠근다(정상 기어를 다시 받을 때까지).
        (hub.lastGear.value?.let(VehicleStatus()::on) ?: VehicleStatus())
            .let { if (hub.vehicleUnavailableSinceLastGear()) it.copy(error = true) else it },
    )

    /** 실제 차량 상태(잠금 판단용). 시연 신호는 여기에 들어오지 않는다. */
    val vehicle: StateFlow<VehicleStatus> = _vehicle.asStateFlow()

    private val session = MutableStateFlow<DriveSession?>(null)

    private val _demo = MutableStateFlow<DemoStatus?>(null)
    private var demoJob: Job? = null

    init {
        viewModelScope.launch {
            hub.signals.collect { signal ->
                // 잠금은 시연 중에도 항상 실제 차량을 따른다
                _vehicle.value = _vehicle.value.on(signal)
                // 시연 중에는 실제 신호를 세션에 넣지 않는다(입력원은 한 번에 하나)
                if (_demo.value == null) session.value?.onSignal(signal)
            }
        }
    }

    val uiState: StateFlow<DriveUiState> = combine(session, _demo) { s, d -> s to d }.flatMapLatest { (s, demo) ->
        combine(
            _vehicle,
            speaker.current,
            s?.history ?: flowOf(emptyList()),
            s?.speedLimitKmh ?: flowOf(null),
            combine(speaker.unavailableLocales, speaker.engineFailed) { locales, failed ->
                failed || (s != null && AppLocales.localeOf(s.profile.language) in locales)
            },
        ) { vehicle, caption, history, limit, voiceUnavailable ->
            DriveUiState(s?.profile, demo, vehicle, caption?.text, history, limit, voiceUnavailable)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DriveUiState())

    /**
     * 주행 화면에 들어올 때 부른다. 프로필이 바뀌었으면 새 세션을 연다.
     * [announceNow]: 온보딩 START 직후처럼 바로 시작 안내를 낼지(같은 프로필이어도 새 세션). 아니면 기어 D가 될 때 엔진이 낸다.
     *
     * 설정 화면에 가도 세션은 닫지 않는다. 설정을 고치는 중에 출발해도 저장된 프로필로 안전 경고가 계속 나가야 한다.
     */
    fun enterDrive(profile: DriverProfile, announceNow: Boolean) {
        if (_demo.value != null) return // 시연 중에는 시연 세션을 유지한다
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
     * 시연 시작(실제 기어가 P일 때만). [persona] 프로필로 새 세션을 열고 [DemoRoute]를 재생한다.
     * 시연 세션에는 실제 신호를 넣지 않으며, 판단 로직은 실제 주행과 같다.
     */
    fun startDemo(persona: DemoPersona) {
        if (_vehicle.value.locked) return
        cancelDemo()
        speaker.stopAll()
        val demoSession = newSession(persona.profile)
        _demo.value = DemoStatus(persona)
        session.value = demoSession
        demoJob = viewModelScope.launch {
            DemoSignalSource(awaitIdle = { speaker.idle.first { it } }).signals().collect(demoSession::onSignal)
            speaker.idle.first { it }
            _demo.update { it?.copy(finished = true) }
        }
    }

    /** 시연을 끝내고 저장된 프로필의 실제 주행 세션으로 돌아간다(실제 기어가 P일 때만). */
    fun stopDemo(saved: DriverProfile) {
        if (_vehicle.value.locked || _demo.value == null) return
        cancelDemo()
        speaker.stopAll()
        session.value = null
        enterDrive(saved, announceNow = false)
    }

    private fun cancelDemo() {
        demoJob?.cancel()
        demoJob = null
        _demo.value = null
    }

    private fun newSession(profile: DriverProfile): DriveSession {
        val resources = app.withLanguage(profile.language).resources
        return DriveSession(
            profile = profile,
            speaker = speaker,
            textOf = { guidance ->
                // 디버그 빌드에서만 어떤 안내를 냈는지 남긴다(위치·경로 정보는 없음)
                if (BuildConfig.DEBUG) Log.d(TAG, "${profile.language} ${guidance.id} ${guidance.args}")
                GuidanceStrings.text(resources, guidance)
            },
            locale = AppLocales.localeOf(profile.language),
        )
    }

    private companion object {
        const val TAG = "RoadMateGuidance"
    }

    override fun onCleared() {
        speaker.release()
    }
}
