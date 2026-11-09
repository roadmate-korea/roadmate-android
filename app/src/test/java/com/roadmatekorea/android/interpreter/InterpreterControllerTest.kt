package com.roadmatekorea.android.interpreter

import com.roadmatekorea.android.voice.SpeechInput
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** 통역 흐름(듣기 → 번역 → 읽기)을 가짜 마이크, 가짜 서버, 가짜 음성 큐로 확인한다. */
@OptIn(ExperimentalCoroutinesApi::class)
class InterpreterControllerTest {

    private class FakeSpeech : SpeechInput {
        var listener: SpeechInput.Listener? = null
        var locale: Locale? = null
        var listens = 0
        var stops = 0
        override fun listen(locale: Locale, listener: SpeechInput.Listener, audio: ByteArray?) {
            this.locale = locale
            this.listener = listener
            listens++
        }
        override fun stop() {
            stops++
        }
        override fun release() = Unit
    }

    /** 테스트가 답을 줄 때까지 기다리는 서버. 취소되면 기록한다. */
    private class FakeClient : TranslationClient {
        val requests = mutableListOf<Triple<String, Locale, Locale>>()
        val replies = mutableListOf<CompletableDeferred<String?>>()
        var cancelled = 0
        override suspend fun translate(text: String, from: Locale, to: Locale, situation: Situation): String? {
            requests += Triple(text, from, to)
            val reply = CompletableDeferred<String?>().also { replies += it }
            try {
                return reply.await()
            } catch (e: CancellationException) {
                cancelled++
                throw e
            }
        }
    }

    /** 앱의 음성 큐와 기어 상태 대신. */
    private class FakeVoice : InterpreterVoice {
        var parked = true
        var safetyPending = false
        var holding = false
        var interrupt: (() -> Unit)? = null
        var stoppedOthers = 0
        val said = mutableListOf<Pair<String, Locale>>()
        override fun parked() = parked
        override fun stopOthers() {
            stoppedOthers++
        }
        override fun hold(onSafetyInterrupt: () -> Unit): Boolean {
            if (safetyPending) return false
            holding = true
            interrupt = onSafetyInterrupt
            return true
        }
        override fun release() {
            holding = false
            interrupt = null
        }
        override fun say(text: String, locale: Locale) {
            said += text to locale
        }

        /** 안전 경고가 들어옴(실제 음성 큐처럼 hold를 풀고 알린다). */
        fun safetyArrives() {
            val callback = interrupt ?: return
            release()
            callback()
        }
    }

    private class Rig(val controller: InterpreterController, val speech: FakeSpeech, val client: FakeClient, val voice: FakeVoice) {
        val said get() = voice.said
    }

    private val french = Locale.CANADA_FRENCH

    private fun TestScope.rig(): Rig {
        val speech = FakeSpeech()
        val client = FakeClient()
        val voice = FakeVoice()
        val controller = InterpreterController(
            speech = speech,
            client = client,
            voice = voice,
            driverLocale = { french },
            scope = backgroundScope,
            listenTimeoutMs = 10_000,
        )
        return Rig(controller, speech, client, voice)
    }

    @Test
    fun driverSpeech_isHeardInTheirLanguage_translatedToKorean_andSpoken() = runTest {
        val r = rig()
        r.controller.toggleListen(Side.DRIVER)
        assertEquals(french, r.speech.locale)
        assertEquals("마이크를 쓰기 전에 음성 질문과 통역 음성을 멈춤", 1, r.voice.stoppedOthers)
        assertTrue("듣는 동안 음성 큐를 멈춤", r.voice.holding)
        assertTrue(r.controller.state.value.preparing)
        r.speech.listener!!.onListening()
        assertEquals(Side.DRIVER, r.controller.state.value.listening)

        r.speech.listener!!.onFinal("Le plein, s'il vous plaît.")
        runCurrent()
        assertFalse("결과를 받으면 음성 큐를 풂", r.voice.holding)
        assertEquals(Side.DRIVER, r.controller.state.value.translating)
        assertEquals(Triple("Le plein, s'il vous plaît.", french, Locale.KOREA), r.client.requests.single())

        r.client.replies.single().complete("가득 넣어 주세요.")
        runCurrent()
        val state = r.controller.state.value
        assertEquals(Exchange("Le plein, s'il vous plaît.", "가득 넣어 주세요."), state.mine)
        assertNull(state.translating)
        assertEquals(listOf("가득 넣어 주세요." to Locale.KOREA), r.said)
    }

    @Test
    fun staffSpeech_isHeardInKorean_andSpokenInTheDriverLanguage() = runTest {
        val r = rig()
        r.controller.toggleListen(Side.STAFF)
        assertEquals(Locale.KOREA, r.speech.locale)
        r.speech.listener!!.onFinal("카드로 결제하시겠어요?")
        runCurrent()
        assertEquals(Triple("카드로 결제하시겠어요?", Locale.KOREA, french), r.client.requests.single())
        r.client.replies.single().complete("Vous payez par carte ?")
        runCurrent()
        assertEquals(listOf("Vous payez par carte ?" to french), r.said)
        assertEquals(Exchange("카드로 결제하시겠어요?", "Vous payez par carte ?"), r.controller.state.value.staff)
    }

    @Test
    fun failedTranslation_pointsDriverToPhraseCards_andStaffToConnection() = runTest {
        val r = rig()
        r.controller.toggleListen(Side.DRIVER)
        r.speech.listener!!.onFinal("Bonjour")
        runCurrent()
        r.client.replies.last().complete(null)
        runCurrent()
        assertEquals(InterpreterNotice.NO_CONNECTION, r.controller.state.value.notice)
        assertEquals(Exchange("Bonjour", null), r.controller.state.value.mine)

        r.controller.toggleListen(Side.STAFF)
        r.speech.listener!!.onFinal("안녕하세요")
        runCurrent()
        r.client.replies.last().complete(null)
        runCurrent()
        assertEquals(InterpreterNotice.STAFF_NEEDS_CONNECTION, r.controller.state.value.notice)
        assertTrue("실패는 읽지 않음", r.said.isEmpty())
    }

    @Test
    fun phraseCard_showsAndSpeaksKorean_withoutTheServer_andStopsAVoiceQuestion() = runTest {
        val r = rig()
        r.controller.showPhrase("Le plein, s'il vous plaît.", "가득 넣어 주세요.")
        assertEquals(Exchange("Le plein, s'il vous plaît.", "가득 넣어 주세요."), r.controller.state.value.mine)
        assertEquals(listOf("가득 넣어 주세요." to Locale.KOREA), r.said)
        assertTrue(r.client.requests.isEmpty())
        assertEquals("듣던 음성 질문을 멈춤", 1, r.voice.stoppedOthers)
    }

    @Test
    fun enteringTheScreen_stopsAVoiceQuestion() = runTest {
        val r = rig()
        r.controller.enter()
        assertEquals(1, r.voice.stoppedOthers)
    }

    @Test
    fun nothingHeard_orTimeout_showsNotHeard() = runTest {
        val r = rig()
        r.controller.toggleListen(Side.DRIVER)
        r.speech.listener!!.onError()
        assertEquals(InterpreterNotice.NOT_HEARD, r.controller.state.value.notice)
        assertNull(r.controller.state.value.listening)
        assertFalse(r.voice.holding)

        r.controller.toggleListen(Side.STAFF)
        r.speech.listener!!.onListening()
        advanceTimeBy(10_001)
        runCurrent()
        assertEquals(InterpreterNotice.NOT_HEARD, r.controller.state.value.notice)
        assertNull(r.controller.state.value.listening)
        assertTrue("시간 초과면 마이크를 닫음", r.speech.stops >= 1)
        assertFalse(r.voice.holding)
        assertTrue(r.client.requests.isEmpty())
    }

    @Test
    fun tappingTheSameMicAgain_stopsListening_andALateResultIsIgnored() = runTest {
        val r = rig()
        r.controller.toggleListen(Side.DRIVER)
        val first = r.speech.listener!!
        r.controller.toggleListen(Side.DRIVER)
        assertNull(r.controller.state.value.listening)
        assertEquals(1, r.speech.stops)
        assertFalse(r.voice.holding)
        first.onFinal("Bonjour")
        runCurrent()
        assertTrue("멈춘 뒤 온 결과는 번역하지 않음", r.client.requests.isEmpty())
    }

    @Test
    fun notInPark_nothingStarts_evenFromALateTap() = runTest {
        val r = rig()
        r.voice.parked = false
        r.controller.toggleListen(Side.STAFF)
        r.controller.showPhrase("Le plein, s'il vous plaît.", "가득 넣어 주세요.")
        assertEquals(0, r.speech.listens)
        assertNull(r.controller.state.value.listening)
        assertTrue(r.said.isEmpty())
        assertFalse(r.voice.holding)
    }

    @Test
    fun aResultArrivingAfterLeavingPark_isNotTranslated() = runTest {
        val r = rig()
        r.controller.toggleListen(Side.STAFF)
        r.voice.parked = false // 기어가 D로 바뀌었고 앱의 leave()보다 인식 결과가 먼저 옴
        r.speech.listener!!.onFinal("카드로 결제하시겠어요?")
        runCurrent()
        assertTrue(r.client.requests.isEmpty())
        assertFalse(r.voice.holding)
    }

    @Test
    fun aSafetyWarningPlayingOrWaiting_blocksTheMic() = runTest {
        val r = rig()
        r.voice.safetyPending = true
        r.controller.toggleListen(Side.DRIVER)
        assertEquals(0, r.speech.listens)
        assertNull(r.controller.state.value.listening)
    }

    @Test
    fun aSafetyWarningWhileListening_closesTheMic() = runTest {
        val r = rig()
        r.controller.toggleListen(Side.STAFF)
        val listener = r.speech.listener!!
        r.voice.safetyArrives()
        assertNull(r.controller.state.value.listening)
        assertEquals(1, r.speech.stops)
        listener.onFinal("카드로 결제하시겠어요?")
        runCurrent()
        assertTrue("끊긴 뒤 온 결과는 번역하지 않음", r.client.requests.isEmpty())
    }

    @Test
    fun leaving_cancelsThePendingTranslation_andNothingIsSpokenAfterwards() = runTest {
        val r = rig()
        r.controller.toggleListen(Side.STAFF)
        r.speech.listener!!.onFinal("카드로 결제하시겠어요?")
        runCurrent()
        r.controller.leave() // 뒤로 가거나 기어가 P를 벗어남
        runCurrent()
        assertEquals("번역 작업을 취소함", 1, r.client.cancelled)
        r.client.replies.single().complete("Vous payez par carte ?")
        runCurrent()
        assertTrue(r.said.isEmpty())
        assertNull(r.controller.state.value.translating)
    }

    @Test
    fun leaving_clearsTheConversation_butKeepsTheSituation() = runTest {
        val r = rig()
        r.controller.select(Situation.RENTAL)
        r.controller.showPhrase("Je rends la voiture.", "차 반납하러 왔어요.")
        r.controller.leave()
        assertEquals(InterpreterUi(situation = Situation.RENTAL), r.controller.state.value)
    }

    @Test
    fun speakingAgainOnTheSameSide_cancelsTheOlderTranslation_beforeTheNewResult() = runTest {
        val r = rig()
        r.controller.toggleListen(Side.DRIVER)
        r.speech.listener!!.onFinal("Bonjour")
        runCurrent()
        r.controller.toggleListen(Side.DRIVER) // 새로 말하기 시작(아직 결과 없음)
        runCurrent()
        assertEquals("앞 번역 작업을 취소함", 1, r.client.cancelled)
        r.client.replies[0].complete("안녕하세요")
        runCurrent()
        assertTrue("녹음 중에 앞 번역을 읽지 않음", r.said.isEmpty())
        assertEquals(Side.DRIVER, r.controller.state.value.listening)

        r.speech.listener!!.onFinal("Le plein, s'il vous plaît.")
        runCurrent()
        r.client.replies[1].complete("가득 넣어 주세요.")
        runCurrent()
        assertEquals(listOf("가득 넣어 주세요." to Locale.KOREA), r.said)
        assertEquals("가득 넣어 주세요.", r.controller.state.value.mine?.translated)
    }

    @Test
    fun aTranslationArrivingWhileTheOtherSideSpeaks_isShownButNotSpoken() = runTest {
        val r = rig()
        r.controller.toggleListen(Side.DRIVER)
        r.speech.listener!!.onFinal("Je vais payer par carte.")
        runCurrent()
        r.controller.toggleListen(Side.STAFF) // 직원이 바로 말하기 시작
        r.client.replies.single().complete("카드로 결제할게요.")
        runCurrent()
        assertEquals("카드로 결제할게요.", r.controller.state.value.mine?.translated)
        assertTrue("직원 말을 듣는 중에는 읽지 않음", r.said.isEmpty())
        assertEquals(Side.STAFF, r.controller.state.value.listening)
    }

    @Test
    fun phraseCard_cancelsAPendingDriverTranslation() = runTest {
        val r = rig()
        r.controller.toggleListen(Side.DRIVER)
        r.speech.listener!!.onFinal("Bonjour")
        runCurrent()
        r.controller.showPhrase("Le plein, s'il vous plaît.", "가득 넣어 주세요.")
        runCurrent()
        assertEquals(1, r.client.cancelled)
        r.client.replies.single().complete("안녕하세요")
        runCurrent()
        assertEquals(listOf("가득 넣어 주세요." to Locale.KOREA), r.said)
        assertEquals("가득 넣어 주세요.", r.controller.state.value.mine?.translated)
    }
}
