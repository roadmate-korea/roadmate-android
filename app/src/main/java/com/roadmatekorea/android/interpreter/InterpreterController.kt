package com.roadmatekorea.android.interpreter

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

/** 고른 문장 카드: 운전자 언어 문장과 직원에게 보여 줄 한국어. */
data class ShownPhrase(val driverText: String, val korean: String)

data class InterpreterUi(
    val situation: Situation = Situation.GAS_STATION,
    /** 마지막으로 고른 문장 카드. 고르기 전에는 null. */
    val shown: ShownPhrase? = null,
)

/** 통역이 앱의 음성 큐와 기어 상태를 쓰는 길. 안내와 같은 스피커를 나눠 쓰므로 순서를 맞춘다. */
interface InterpreterVoice {
    /** 기어가 P인지. P가 아니면 카드를 보여 주지도, 읽지도 않는다(정차 중 기능). */
    fun parked(): Boolean

    /** 통역 화면에 들어오거나 문장 카드를 고를 때: 듣던 음성 질문과 읽던 통역 음성을 멈춘다. */
    fun stopOthers()

    /** 문장을 그 언어로 읽는다(같은 음성 큐, 안전 경고가 오면 끊긴다). */
    fun say(text: String, locale: Locale)
}

/**
 * 정차 중 통역(Figma 10). 상황별로 미리 번역해 둔 문장 카드를 고르면 한국어로 크게 보여 주고 한국어 음성으로 읽는다.
 * 번역 서버를 쓰지 않으므로 연결 없이 동작한다(공모전 규칙: 공개된 Pleos SDK 범위, 서버 사용 금지).
 */
class InterpreterController(private val voice: InterpreterVoice) {
    private val _state = MutableStateFlow(InterpreterUi())
    val state: StateFlow<InterpreterUi> = _state.asStateFlow()

    fun select(situation: Situation) = _state.update { it.copy(situation = situation) }

    /** 통역 화면에 들어올 때. 보이지 않는 음성 질문이 마이크를 계속 쓰지 않게 멈춘다. */
    fun enter() = voice.stopOthers()

    /** 문장 카드: 한국어로 크게 보여 주고 읽는다. */
    fun showPhrase(driverText: String, korean: String) {
        if (!voice.parked()) return
        voice.stopOthers()
        _state.update { it.copy(shown = ShownPhrase(driverText, korean)) }
        voice.say(korean, KOREAN)
    }

    /**
     * 통역 화면을 떠날 때(뒤로, 기어가 P를 벗어남). 고른 카드를 지운다(다음 정차 때 이전 문장이 보이지 않게).
     * 상황 선택은 남긴다. 화면이 백그라운드여도 기어가 P를 벗어나면 앱이 부른다.
     */
    fun leave() = _state.update { InterpreterUi(situation = it.situation) }

    companion object {
        val KOREAN: Locale = Locale.KOREA
    }
}
