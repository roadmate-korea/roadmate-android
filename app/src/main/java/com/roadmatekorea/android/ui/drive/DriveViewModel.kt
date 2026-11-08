package com.roadmatekorea.android.ui.drive

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.roadmatekorea.android.drive.DriveSession
import com.roadmatekorea.android.drive.DriveSignalHub
import com.roadmatekorea.android.drive.VehicleStatus
import com.roadmatekorea.android.i18n.AppLocales
import com.roadmatekorea.android.i18n.GuidanceStrings
import com.roadmatekorea.android.i18n.withLanguage
import com.roadmatekorea.android.profile.model.DriverProfile
import com.roadmatekorea.android.voice.GuidanceSpeaker
import com.roadmatekorea.android.voice.PleosTtsEngine
import com.roadmatekorea.android.voice.TtsEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 주행 화면 상태. */
data class DriveUiState(
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
        VehicleStatus(unavailable = hub.vehicleUnavailable.value).let { s -> hub.lastGear.value?.let(s::on) ?: s },
    )

    /** 실제 차량 상태(잠금 판단용). 시연 신호는 여기에 들어오지 않는다. */
    val vehicle: StateFlow<VehicleStatus> = _vehicle.asStateFlow()

    private val session = MutableStateFlow<DriveSession?>(null)

    init {
        viewModelScope.launch {
            hub.signals.collect { signal ->
                _vehicle.value = _vehicle.value.on(signal)
                session.value?.onSignal(signal)
            }
        }
    }

    val uiState: StateFlow<DriveUiState> = session.flatMapLatest { s ->
        combine(
            _vehicle,
            speaker.current,
            s?.history ?: flowOf(emptyList()),
            s?.speedLimitKmh ?: flowOf(null),
            combine(speaker.unavailableLocales, speaker.engineFailed) { locales, failed ->
                failed || (s != null && AppLocales.localeOf(s.profile.language) in locales)
            },
        ) { vehicle, caption, history, limit, voiceUnavailable ->
            DriveUiState(vehicle, caption?.text, history, limit, voiceUnavailable)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DriveUiState())

    /**
     * 주행 화면에 들어올 때 부른다. 프로필이 바뀌었으면 새 세션을 연다.
     * [announceNow]: 온보딩 START 직후처럼 바로 시작 안내를 낼지. 아니면 기어 D가 될 때 엔진이 낸다.
     */
    fun enterDrive(profile: DriverProfile, announceNow: Boolean) {
        val current = session.value
        if (current != null && current.profile == profile) return
        speaker.stopAll()
        val resources = app.withLanguage(profile.language).resources
        val next = DriveSession(
            profile = profile,
            speaker = speaker,
            textOf = { GuidanceStrings.text(resources, it) },
            locale = AppLocales.localeOf(profile.language),
        )
        session.value = next
        // 엔진이 지금 기어를 알게 한다. 이미 D라면 이것이 주행 시작이 되어 시작 안내가 나간다.
        hub.lastGear.value?.let(next::onSignal)
        if (announceNow) next.announceStart()
    }

    /** 설정 화면으로 갈 때 부른다(정차 중에만 가능). 말하던 안내를 멈추고 세션을 닫는다. */
    fun leaveDrive() {
        speaker.stopAll()
        session.value = null
    }

    override fun onCleared() {
        speaker.release()
    }
}
