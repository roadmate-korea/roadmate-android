package com.roadmatekorea.android.interpreter

import com.roadmatekorea.android.voice.SpeechInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
 * 정차 중 통역(Figma 10). 운전자 말 → 한국어(음성과 큰 글자), 직원 말 → 운전자 언어(음성).
 *
 * 번역은 서버가 한다([TranslationClient]). 서버가 없거나 실패하면:
 * - 운전자 쪽: 미리 번역해 둔 문장 카드를 쓰라고 알린다(카드는 연결 없이 동작)
 * - 직원 쪽: 연결이 필요하다고 알린다
 *
 * @param prepareToListen 듣기 전에 부른다. 음성 질문을 멈추고 읽던 통역 음성을 멈춘다(같은 마이크와 스피커).
 * @param say 문장을 그 언어로 읽는다.
 * @param driverLocale 운전자 언어(지금 세션 프로필).
 */
class InterpreterController(
    private val speech: SpeechInput,
    private val client: TranslationClient,
    private val say: (String, Locale) -> Unit,
    private val prepareToListen: () -> Unit,
    private val driverLocale: () -> Locale,
    private val scope: CoroutineScope,
    private val listenTimeoutMs: Long = 10_000,
) {
    private val _state = MutableStateFlow(InterpreterUi())
    val state: StateFlow<InterpreterUi> = _state.asStateFlow()

    /** 지금 듣는 요청의 표시. 앞 요청의 늦은 신호를 무시한다. */
    private var request: Any? = null
    private var timeout: Job? = null

    /** 쪽마다 기다리는 번역의 표시. 그 사이 새로 말하거나, 문장 카드를 고르거나, 화면을 떠나면 늦은 번역을 버린다. */
    private val pending = mutableMapOf<Side, Any>()

    fun select(situation: Situation) = _state.update { it.copy(situation = situation, notice = null) }

    /** 미리 번역해 둔 문장 카드: 한국어로 크게 보여 주고 읽는다(연결 없이 동작). */
    fun showPhrase(driverText: String, korean: String) {
        stopListening()
        pending.remove(Side.DRIVER)
        _state.update { it.copy(mine = Exchange(driverText, korean), notice = null, translating = it.translating.takeIf { t -> t != Side.DRIVER }) }
        say(korean, KOREAN)
    }

    /** 마이크 버튼. 듣는 중인 쪽을 다시 누르면 멈춘다. [audio]는 디버그 빌드에서 마이크 대신 넣는 녹음(PCM)이다. */
    fun toggleListen(side: Side, audio: ByteArray? = null) {
        if (_state.value.listening == side) {
            stopListening()
            return
        }
        stopListening()
        prepareToListen()
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
     * 통역 화면을 떠날 때(뒤로, 기어가 P를 벗어남). 듣기를 멈추고 기다리던 번역은 읽지 않는다.
     * 나눈 대화도 지운다(다음 정차 때 이전 대화가 보이지 않게, 인식한 문장을 들고 있지 않게). 상황 선택은 남긴다.
     * 화면이 백그라운드여도 기어가 P를 벗어나면 앱이 부른다.
     */
    fun leave() {
        stopListening()
        pending.clear()
        _state.update { InterpreterUi(situation = it.situation) }
    }

    /** 듣기만 멈춘다. */
    fun stopListening() {
        timeout?.cancel()
        if (request != null) speech.stop()
        request = null
        _state.update { it.copy(listening = null, preparing = false) }
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
        _state.update { it.copy(listening = null, preparing = false) }
        if (text.isNullOrBlank()) {
            _state.update { it.copy(notice = InterpreterNotice.NOT_HEARD) }
            return
        }
        val from = if (side == Side.DRIVER) driverLocale() else KOREAN
        val to = if (side == Side.DRIVER) KOREAN else driverLocale()
        _state.update {
            val exchange = Exchange(text, null)
            if (side == Side.DRIVER) it.copy(mine = exchange, translating = side) else it.copy(staff = exchange, translating = side)
        }
        val situation = _state.value.situation
        val token = Any()
        pending[side] = token
        scope.launch {
            val translated = client.translate(text, from, to, situation)
            if (pending[side] !== token) return@launch
            pending.remove(side)
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
            if (translated != null) say(translated, to)
        }
    }

    companion object {
        val KOREAN: Locale = Locale.KOREA
    }
}
