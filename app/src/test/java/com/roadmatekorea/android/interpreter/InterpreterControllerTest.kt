package com.roadmatekorea.android.interpreter

import com.roadmatekorea.android.voice.SpeechInput
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/** 통역 흐름(듣기 → 번역 → 읽기)을 가짜 마이크와 가짜 서버로 확인한다. */
@OptIn(ExperimentalCoroutinesApi::class)
class InterpreterControllerTest {

    private class FakeSpeech : SpeechInput {
        var listener: SpeechInput.Listener? = null
        var locale: Locale? = null
        var stops = 0
        override fun listen(locale: Locale, listener: SpeechInput.Listener, audio: ByteArray?) {
            this.locale = locale
            this.listener = listener
        }
        override fun stop() {
            stops++
        }
        override fun release() = Unit
    }

    /** 테스트가 답을 줄 때까지 기다리는 서버. */
    private class FakeClient : TranslationClient {
        val requests = mutableListOf<Triple<String, Locale, Locale>>()
        val replies = mutableListOf<CompletableDeferred<String?>>()
        override suspend fun translate(text: String, from: Locale, to: Locale, situation: Situation): String? {
            requests += Triple(text, from, to)
            return CompletableDeferred<String?>().also { replies += it }.await()
        }
    }

    private class Rig(
        val controller: InterpreterController,
        val speech: FakeSpeech,
        val client: FakeClient,
        val said: MutableList<Pair<String, Locale>>,
        var prepared: Int = 0,
    )

    private val french = Locale.CANADA_FRENCH

    private fun TestScope.rig(): Rig {
        val speech = FakeSpeech()
        val client = FakeClient()
        val said = mutableListOf<Pair<String, Locale>>()
        lateinit var rig: Rig
        val controller = InterpreterController(
            speech = speech,
            client = client,
            say = { text, locale -> said += text to locale },
            prepareToListen = { rig.prepared++ },
            driverLocale = { french },
            scope = backgroundScope,
            listenTimeoutMs = 10_000,
        )
        rig = Rig(controller, speech, client, said)
        return rig
    }

    @Test
    fun driverSpeech_isHeardInTheirLanguage_translatedToKorean_andSpoken() = runTest {
        val r = rig()
        r.controller.toggleListen(Side.DRIVER)
        assertEquals(french, r.speech.locale)
        assertEquals("마이크를 쓰기 전에 음성 질문과 통역 음성을 멈춤", 1, r.prepared)
        assertTrue(r.controller.state.value.preparing)
        r.speech.listener!!.onListening()
        assertEquals(Side.DRIVER, r.controller.state.value.listening)

        r.speech.listener!!.onFinal("Le plein, s'il vous plaît.")
        runCurrent()
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
    fun phraseCard_showsAndSpeaksKorean_withoutTheServer() = runTest {
        val r = rig()
        r.controller.showPhrase("Le plein, s'il vous plaît.", "가득 넣어 주세요.")
        assertEquals(Exchange("Le plein, s'il vous plaît.", "가득 넣어 주세요."), r.controller.state.value.mine)
        assertEquals(listOf("가득 넣어 주세요." to Locale.KOREA), r.said)
        assertTrue(r.client.requests.isEmpty())
    }

    @Test
    fun nothingHeard_orTimeout_showsNotHeard() = runTest {
        val r = rig()
        r.controller.toggleListen(Side.DRIVER)
        r.speech.listener!!.onError()
        assertEquals(InterpreterNotice.NOT_HEARD, r.controller.state.value.notice)
        assertNull(r.controller.state.value.listening)

        r.controller.toggleListen(Side.STAFF)
        r.speech.listener!!.onListening()
        advanceTimeBy(10_001)
        runCurrent()
        assertEquals(InterpreterNotice.NOT_HEARD, r.controller.state.value.notice)
        assertNull(r.controller.state.value.listening)
        assertTrue("시간 초과면 마이크를 닫음", r.speech.stops >= 1)
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
        first.onFinal("Bonjour")
        runCurrent()
        assertTrue("멈춘 뒤 온 결과는 번역하지 않음", r.client.requests.isEmpty())
    }

    @Test
    fun leaving_dropsAPendingTranslation_soNothingIsSpokenAfterwards() = runTest {
        val r = rig()
        r.controller.toggleListen(Side.STAFF)
        r.speech.listener!!.onFinal("카드로 결제하시겠어요?")
        runCurrent()
        r.controller.leave() // 뒤로 가거나 기어가 P를 벗어남
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
    fun aNewerUtterance_winsOverAnOlderTranslation() = runTest {
        val r = rig()
        r.controller.toggleListen(Side.DRIVER)
        r.speech.listener!!.onFinal("Bonjour")
        runCurrent()
        r.controller.toggleListen(Side.DRIVER)
        r.speech.listener!!.onFinal("Le plein, s'il vous plaît.")
        runCurrent()
        r.client.replies[1].complete("가득 넣어 주세요.")
        runCurrent()
        r.client.replies[0].complete("안녕하세요")
        runCurrent()
        assertEquals(listOf("가득 넣어 주세요." to Locale.KOREA), r.said)
        assertEquals("가득 넣어 주세요.", r.controller.state.value.mine?.translated)
    }

    @Test
    fun phraseCard_dropsAPendingDriverTranslation() = runTest {
        val r = rig()
        r.controller.toggleListen(Side.DRIVER)
        r.speech.listener!!.onFinal("Bonjour")
        runCurrent()
        r.controller.showPhrase("Le plein, s'il vous plaît.", "가득 넣어 주세요.")
        r.client.replies.single().complete("안녕하세요")
        runCurrent()
        assertEquals(listOf("가득 넣어 주세요." to Locale.KOREA), r.said)
        assertEquals("가득 넣어 주세요.", r.controller.state.value.mine?.translated)
    }
}
