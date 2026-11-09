package com.roadmatekorea.android.voice

import com.roadmatekorea.android.rules.Clock
import com.roadmatekorea.android.rules.Guidance
import com.roadmatekorea.android.rules.GuidanceId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * 안내를 한 번에 하나씩 말하고, 지금 말하는 문장을 자막으로 내보낸다.
 *
 * - 음성이 되면 TTS로 말하고, 끝나면 다음 안내로 넘어간다.
 * - 엔진 초기화 실패, 지원하지 않는 언어, 그 밖의 오류가 나면 그 안내는 자막만 일정 시간 보여 준다.
 *   지원하지 않는 언어는 기억해 두고 이후 그 언어는 바로 자막으로 보낸다.
 * - 엔진이 끝 신호를 주지 않아도 멈추지 않도록 감시 시간이 지나면 다음으로 넘어간다.
 *
 * 메인 스레드(또는 [scope]의 단일 스레드)에서만 부른다.
 */
class GuidanceSpeaker(
    private val engine: TtsEngine,
    private val scope: CoroutineScope,
    clock: Clock = Clock.SYSTEM,
) : TtsEngine.Listener {

    data class Caption(val guidance: Guidance, val text: String, val voiced: Boolean)

    private val queue = SpeechQueue(clock)

    private val _current = MutableStateFlow<Caption?>(null)

    /** 지금 말하거나 보여 주는 안내. 없으면 null. */
    val current: StateFlow<Caption?> = _current.asStateFlow()

    private val _unavailableLocales = MutableStateFlow<Set<Locale>>(emptySet())

    /** 음성을 쓸 수 없어 자막만 보여 주는 언어. */
    val unavailableLocales: StateFlow<Set<Locale>> = _unavailableLocales.asStateFlow()

    private val _engineFailed = MutableStateFlow(false)

    /** TTS 엔진을 쓸 수 없음(초기화 실패). 모든 안내를 자막으로만 보여 준다. */
    val engineFailed: StateFlow<Boolean> = _engineFailed.asStateFlow()

    private val _idle = MutableStateFlow(true)

    /** 말하는 것도, 기다리는 것도 없음. */
    val idle: StateFlow<Boolean> = _idle.asStateFlow()

    private var engineState = EngineState.INITIALIZING
    private var playing: SpeechQueue.Item? = null
    private var playingStarted = false
    private var timer: Job? = null

    fun start() {
        engine.setListener(this)
        engine.initialize { ready ->
            engineState = if (ready) EngineState.READY else EngineState.FAILED
            _engineFailed.value = !ready
            if (playing == null) playNext()
        }
    }

    fun say(guidance: Guidance, text: String, locale: Locale) {
        if (!queue.enqueue(guidance, text, locale)) return
        _idle.value = false
        // 엔진 준비 전에는 쌓아 두었다가 준비가 끝나면 말한다.
        if (playing == null && engineState != EngineState.INITIALIZING) playNext()
    }

    /** 기다리는 같은 종류의 안내를 지금 값으로 바꾼다. 지금 말하고 있는 것은 그대로 둔다. */
    fun refreshQueued(guidance: Guidance, text: String) = queue.replace(guidance, text)

    /** 기다리는 [id] 안내를 버린다. 지금 말하고 있는 것은 그대로 둔다. */
    fun drop(id: GuidanceId) {
        queue.remove(id)
        if (playing == null && queue.size == 0) _idle.value = true
    }

    /** 기다리는 안내를 모두 버리고 지금 말하는 것도 멈춘다. */
    fun stopAll() {
        queue.clear()
        timer?.cancel()
        if (playing?.let { _current.value?.voiced } == true) engine.stop()
        playing = null
        _current.value = null
        _idle.value = true
    }

    /** 해제할 때는 엔진 stop을 부르지 않는다([TtsEngine.release] 참고). 재생 중이던 소리는 엔진 해제로 끝난다. */
    fun release() {
        queue.clear()
        timer?.cancel()
        playing = null
        _current.value = null
        _idle.value = true
        engine.setListener(null)
        engine.release()
    }

    // ---- TtsEngine.Listener ----

    override fun onStart() {
        if (playing != null) playingStarted = true
    }

    override fun onDone() {
        // 시작 신호를 받기 전의 완료 신호는 앞 문장의 늦은 신호로 보고 무시한다.
        if (playing != null && playingStarted) finishCurrent()
    }

    override fun onError(error: TtsError) {
        val item = playing ?: return
        if (error == TtsError.UNAVAILABLE_LANGUAGE) {
            _unavailableLocales.value = _unavailableLocales.value + item.locale
        }
        showCaptionOnly(item)
    }

    // ---- 내부 ----

    private fun playNext() {
        timer?.cancel()
        val item = queue.poll()
        playing = item
        playingStarted = false
        if (item == null) {
            _current.value = null
            _idle.value = true
            return
        }
        _idle.value = false
        val canSpeak = engineState == EngineState.READY && item.locale !in _unavailableLocales.value
        if (canSpeak) {
            _current.value = Caption(item.guidance, item.text, voiced = true)
            engine.speak(item.text, item.locale)
            timer = scope.launch {
                delay(speechTimeoutMs(item.text))
                engine.stop()
                finishCurrent()
            }
        } else {
            showCaptionOnly(item)
        }
    }

    private fun showCaptionOnly(item: SpeechQueue.Item) {
        timer?.cancel()
        _current.value = Caption(item.guidance, item.text, voiced = false)
        timer = scope.launch {
            delay(captionOnlyMs(item.text))
            finishCurrent()
        }
    }

    private fun finishCurrent() {
        timer?.cancel()
        playing = null
        playNext()
    }

    private enum class EngineState { INITIALIZING, READY, FAILED }

    companion object {
        /** 자막만 보여 줄 때 읽을 시간. */
        fun captionOnlyMs(text: String): Long = (text.length * 70L).coerceIn(3_000L, 12_000L)

        /** 엔진이 끝 신호를 주지 않을 때 넘어가기까지 기다리는 시간. */
        fun speechTimeoutMs(text: String): Long = (text.length * 150L + 3_000L).coerceIn(6_000L, 40_000L)
    }
}
