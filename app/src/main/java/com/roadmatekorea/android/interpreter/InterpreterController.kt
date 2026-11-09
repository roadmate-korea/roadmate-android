package com.roadmatekorea.android.interpreter

import com.roadmatekorea.android.voice.SpeechInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/** 누가 말하는지. */
enum class Side { DRIVER, STAFF }

/** 화면에 알릴 상황. */
enum class InterpreterNotice { NO_CONNECTION, STAFF_NEEDS_CONNECTION, NOT_HEARD }

/** 한 사람이 한 말과 번역. 번역 중이거나 실패하면 [translated]는 null. */
data class Exchange(val original: String, val translated: String?)

data class InterpreterUi(
    val situation: Situation = Situation.GAS_STATION,
    /** 운전자가 한 말(운전자 언어)과 한국어. */
    val mine: Exchange? = null,
    /** 직원이 한 말(한국어)과 운전자 언어. */
    val staff: Exchange? = null,
    val listening: Side? = null,
    /** 마이크를 눌렀지만 아직 실제로 듣기 전. */
    val preparing: Boolean = false,
    val translating: Side? = null,
    val notice: InterpreterNotice? = null,
)

/**
 * 통역이 앱의 음성(마이크, 음성 큐)과 기어 상태를 쓰는 길. 음성 질문·안내와 같은 마이크와 스피커를 나눠 쓰므로 순서를 맞춘다.
 */
interface InterpreterVoice {
    /** 기어가 P인지. P가 아니면 통역을 시작하지도, 결과를 읽지도 않는다(정차 중 기능). */
    fun parked(): Boolean

    /** 통역 화면에 들어오거나 문장 카드를 고를 때: 듣던 음성 질문과 읽던 통역 음성을 멈춘다. */
    fun stopOthers()

    /**
     * 마이크를 열기 전에 음성 큐를 멈춘다(스피커 소리가 인식에 섞이지 않게). 안전 경고를 말하거나 기다리는 중이면 false.
     * 듣는 중에 안전 경고가 오면 [onSafetyInterrupt]를 부르고 경고를 먼저 말한다.
     */
    fun hold(onSafetyInterrupt: () -> Unit): Boolean

    /** [hold]를 풀고 쌓인 안내를 이어서 말한다. */
    fun release()

    /** 문장을 그 언어로 읽는다(같은 음성 큐, 안전 경고가 오면 끊긴다). */
    fun say(text: String, locale: Locale)
}

/**
 * 정차 중 통역(Figma 10). 운전자 말 → 한국어(음성과 큰 글자), 직원 말 → 운전자 언어(음성).
 *
 * 번역은 서버가 한다([TranslationClient]). 서버가 없거나 실패하면:
 * - 운전자 쪽: 미리 번역해 둔 문장 카드를 쓰라고 알린다(카드는 연결 없이 동작)
 * - 직원 쪽: 연결이 필요하다고 알린다
 *
 * 늦은 결과: 듣기 요청마다 표시를 두어 멈춘 요청의 인식 결과를 버리고, 쪽마다 번역 작업 하나만 둔다.
 * 같은 쪽에서 새로 말하거나, 문장 카드를 고르거나, 화면을 떠나면 그 쪽의 번역 작업을 취소한다.
 * 다른 쪽을 듣는 중에 도착한 번역은 화면에만 보이고 읽지 않는다(마이크에 섞이지 않게).
 *
 * @param driverLocale 운전자 언어(지금 세션 프로필).
 */
class InterpreterController(
    private val speech: SpeechInput,
    private val client: TranslationClient,
    private val voice: InterpreterVoice,
    private val driverLocale: () -> Locale,
    private val scope: CoroutineScope,
    private val listenTimeoutMs: Long = 10_000,
) {
    private val _state = MutableStateFlow(InterpreterUi())
    val state: StateFlow<InterpreterUi> = _state.asStateFlow()

    /** 지금 듣는 요청의 표시. 앞 요청의 늦은 신호를 무시한다. */
    private var request: Any? = null
    private var timeout: Job? = null

    /** 음성 큐를 멈춰 두었는지([InterpreterVoice.hold]). */
    private var held = false

    /** 쪽마다 진행 중인 번역 작업. */
    private val translations = mutableMapOf<Side, Job>()

    fun select(situation: Situation) = _state.update { it.copy(situation = situation, notice = null) }

    /** 통역 화면에 들어올 때. 보이지 않는 음성 질문이 마이크를 계속 쓰지 않게 멈춘다. */
    fun enter() = voice.stopOthers()

    /** 미리 번역해 둔 문장 카드: 한국어로 크게 보여 주고 읽는다(연결 없이 동작). */
    fun showPhrase(driverText: String, korean: String) {
        if (!voice.parked()) return
        stopListening()
        cancelTranslation(Side.DRIVER)
        voice.stopOthers()
        _state.update { it.copy(mine = Exchange(driverText, korean), notice = null) }
        voice.say(korean, KOREAN)
    }

    /** 마이크 버튼. 듣는 중인 쪽을 다시 누르면 멈춘다. [audio]는 디버그 빌드에서 마이크 대신 넣는 녹음(PCM)이다. */
    fun toggleListen(side: Side, audio: ByteArray? = null) {
        if (_state.value.listening == side) {
            stopListening()
            return
        }
        stopListening()
        if (!voice.parked()) return
        voice.stopOthers()
        // 안전 경고를 말하거나 기다리는 중이면 듣지 않는다(음성 질문과 같은 규칙)
        if (!voice.hold(onSafetyInterrupt = ::stopListening)) return
        held = true
        // 같은 쪽의 새 말이 시작되면 앞 번역은 더 읽지 않는다
        cancelTranslation(side)
        val r = Any()
        request = r
        _state.update { it.copy(listening = side, preparing = true, notice = null) }
        startTimeout(r, listenTimeoutMs * 2)
        speech.listen(
            if (side == Side.DRIVER) driverLocale() else KOREAN,
            object : SpeechInput.Listener {
                override fun onListening() {
                    if (request !== r) return
                    _state.update { it.copy(preparing = false) }
                    startTimeout(r, listenTimeoutMs)
                }

                override fun onPartial(text: String) = Unit
                override fun onFinal(text: String) = heard(r, side, text)
                override fun onError() = heard(r, side, null)
            },
            audio,
        )
    }

    /**
     * 통역 화면을 떠날 때(뒤로, 기어가 P를 벗어남). 듣기를 멈추고 번역 작업을 취소한다.
     * 나눈 대화도 지운다(다음 정차 때 이전 대화가 보이지 않게, 인식한 문장을 들고 있지 않게). 상황 선택은 남긴다.
     * 화면이 백그라운드여도 기어가 P를 벗어나면 앱이 부른다.
     */
    fun leave() {
        stopListening()
        Side.entries.forEach(::cancelTranslation)
        _state.update { InterpreterUi(situation = it.situation) }
    }

    /** 듣기만 멈춘다(결과를 기다리지 않는다). 안전 경고가 오면 음성 큐가 부른다. */
    fun stopListening() {
        timeout?.cancel()
        if (request != null) speech.stop()
        request = null
        releaseHold()
        _state.update { it.copy(listening = null, preparing = false) }
    }

    private fun releaseHold() {
        if (!held) return
        held = false
        voice.release()
    }

    private fun cancelTranslation(side: Side) {
        translations.remove(side)?.cancel()
        _state.update { it.copy(translating = it.translating.takeIf { t -> t != side }) }
    }

    private fun startTimeout(r: Any, ms: Long) {
        timeout?.cancel()
        timeout = scope.launch {
            delay(ms)
            if (request !== r) return@launch
            speech.stop()
            heard(r, _state.value.listening ?: return@launch, null)
        }
    }

    private fun heard(r: Any, side: Side, text: String?) {
        if (request !== r) return
        request = null
        timeout?.cancel()
        releaseHold()
        _state.update { it.copy(listening = null, preparing = false) }
        if (text.isNullOrBlank()) {
            _state.update { it.copy(notice = InterpreterNotice.NOT_HEARD) }
            return
        }
        // 기어가 이미 P가 아니면(화면보다 늦게 온 결과) 번역하지 않는다
        if (!voice.parked()) return
        val from = if (side == Side.DRIVER) driverLocale() else KOREAN
        val to = if (side == Side.DRIVER) KOREAN else driverLocale()
        _state.update {
            val exchange = Exchange(text, null)
            if (side == Side.DRIVER) it.copy(mine = exchange, translating = side) else it.copy(staff = exchange, translating = side)
        }
        val situation = _state.value.situation
        // 작업을 먼저 등록하고 시작한다(바로 끝나는 번역도 자기 자리를 확인할 수 있게)
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val translated = client.translate(text, from, to, situation)
            ensureActive()
            if (translations[side] !== coroutineContext[Job]) return@launch
            translations.remove(side)
            _state.update { current ->
                val exchange = Exchange(text, translated)
                val notice = when {
                    translated != null -> null
                    side == Side.DRIVER -> InterpreterNotice.NO_CONNECTION
                    else -> InterpreterNotice.STAFF_NEEDS_CONNECTION
                }
                val stillTranslating = current.translating.takeIf { it != side }
                if (side == Side.DRIVER) {
                    current.copy(mine = exchange, translating = stillTranslating, notice = notice)
                } else {
                    current.copy(staff = exchange, translating = stillTranslating, notice = notice)
                }
            }
            // 다른 쪽을 듣는 중이면 읽지 않는다(화면에는 보인다). P를 벗어났으면 읽지 않는다
            if (translated != null && request == null && voice.parked()) voice.say(translated, to)
        }
        translations.remove(side)?.cancel()
        translations[side] = job
        job.start()
    }

    companion object {
        val KOREAN: Locale = Locale.KOREA
    }
}
