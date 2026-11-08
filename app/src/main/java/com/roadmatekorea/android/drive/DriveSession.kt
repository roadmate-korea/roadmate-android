package com.roadmatekorea.android.drive

import com.roadmatekorea.android.profile.model.DriverProfile
import com.roadmatekorea.android.rules.Clock
import com.roadmatekorea.android.rules.DriveEvent
import com.roadmatekorea.android.rules.Guidance
import com.roadmatekorea.android.rules.GuidanceEngine
import com.roadmatekorea.android.rules.GuidanceId
import com.roadmatekorea.android.voice.GuidanceSpeaker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

/**
 * 주행 세션 하나: 신호 → [SignalMapper] → [GuidanceEngine] → 문장 → [GuidanceSpeaker].
 *
 * 실제 SDK 신호와 시연 모드 신호가 모두 [onSignal]로 들어온다. 판단은 엔진만 한다.
 * 메인 스레드에서만 부른다.
 */
class DriveSession(
    val profile: DriverProfile,
    private val speaker: GuidanceSpeaker,
    private val textOf: (Guidance) -> String,
    private val locale: Locale,
    clock: Clock = Clock.SYSTEM,
) {
    private val engine = GuidanceEngine(profile, clock)

    private val _history = MutableStateFlow<List<String>>(emptyList())

    /** 이 세션에서 낸 안내 문장. 최근 것이 앞. */
    val history: StateFlow<List<String>> = _history.asStateFlow()

    private val _speedLimitKmh = MutableStateFlow<Int?>(null)

    /** NaviHelper가 준 현재 도로 제한 속도. 0이나 값 없음은 null. */
    val speedLimitKmh: StateFlow<Int?> = _speedLimitKmh.asStateFlow()

    private val _speedKmh = MutableStateFlow<Int?>(null)

    /** 차량 속도(km/h). 읽을 수 없으면 null. */
    val speedKmh: StateFlow<Int?> = _speedKmh.asStateFlow()

    /** START 직후처럼 바로 시작 안내를 낼 때 부른다. 주행 시작(기어 D) 때는 엔진이 알아서 낸다. */
    fun announceStart() = onEvent(DriveEvent.SessionStarted)

    fun onSignal(signal: RawSignal) {
        SignalMapper.map(signal)?.let(::onEvent)
    }

    private fun onEvent(event: DriveEvent) {
        if (event is DriveEvent.SpeedLimitChanged) _speedLimitKmh.value = event.kmh.takeIf { it > 0 }
        if (event is DriveEvent.VehicleSpeedChanged) _speedKmh.value = event.kmh
        val out = engine.onEvent(event)
        // 감속하거나 구역을 벗어났으면 아직 말하지 않은 과속 경고를 버린다(지난 속도를 "지금"이라고 말하지 않게)
        if (!engine.overSpeedNow()) speaker.drop(GuidanceId.SCHOOL_ZONE_OVER_SPEED)
        out.forEach { guidance ->
            val text = textOf(guidance)
            speaker.say(guidance, text, locale)
            _history.update { (listOf(text) + it).take(HISTORY_SIZE) }
        }
    }

    companion object {
        const val HISTORY_SIZE = 5
    }
}
