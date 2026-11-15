package com.roadmatekorea.android.interpreter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** 문장 카드 통역(카드 고르기 → 한국어 표시와 음성)을 가짜 음성 큐로 확인한다. */
class InterpreterControllerTest {

    /** 앱의 음성 큐와 기어 상태 대신. */
    private class FakeVoice : InterpreterVoice {
        var parked = true
        var stoppedOthers = 0
        val said = mutableListOf<Pair<String, Locale>>()
        override fun parked() = parked
        override fun stopOthers() {
            stoppedOthers++
        }
        override fun say(text: String, locale: Locale) {
            said += text to locale
        }
    }

    private val voice = FakeVoice()
    private val controller = InterpreterController(voice)

    @Test
    fun phrase_isShownInKorean_andSpokenInKorean_whileParked() {
        controller.showPhrase("Le plein d'essence, s'il vous plaît.", "휘발유 가득 넣어 주세요.")
        assertEquals(ShownPhrase("Le plein d'essence, s'il vous plaît.", "휘발유 가득 넣어 주세요."), controller.state.value.shown)
        assertEquals(listOf("휘발유 가득 넣어 주세요." to Locale.KOREA), voice.said)
        assertEquals("카드를 읽기 전에 음성 질문과 앞 통역 음성을 멈춤", 1, voice.stoppedOthers)
    }

    @Test
    fun phrase_isIgnored_whenNotParked() {
        voice.parked = false
        controller.showPhrase("Please call the police.", "경찰을 불러 주세요.")
        assertNull(controller.state.value.shown)
        assertTrue(voice.said.isEmpty())
    }

    @Test
    fun newPhrase_replacesThePreviousOne() {
        controller.showPhrase("Can I park here?", "여기 주차해도 되나요?")
        controller.showPhrase("How much is parking?", "주차비는 얼마예요?")
        assertEquals("주차비는 얼마예요?", controller.state.value.shown?.korean)
        assertEquals(2, voice.said.size)
    }

    @Test
    fun leave_clearsTheShownPhrase_butKeepsTheSituation() {
        controller.select(Situation.RENTAL)
        controller.showPhrase("I am here to return the car.", "차를 반납하러 왔어요.")
        controller.leave()
        assertNull(controller.state.value.shown)
        assertEquals(Situation.RENTAL, controller.state.value.situation)
    }

    @Test
    fun enter_stopsOtherSpeech() {
        controller.enter()
        assertEquals(1, voice.stoppedOthers)
    }
}
