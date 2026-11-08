package com.roadmatekorea.android.voice

import com.roadmatekorea.android.rules.Clock
import com.roadmatekorea.android.rules.Guidance
import com.roadmatekorea.android.rules.GuidanceId
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

@OptIn(ExperimentalCoroutinesApi::class)
class GuidanceSpeakerTest {

    private class FakeEngine(private val ready: Boolean = true) : TtsEngine {
        var registered: TtsEngine.Listener? = null
        val spoken = mutableListOf<String>()
        var stops = 0
        var released = false
        private var pendingInit: ((Boolean) -> Unit)? = null

        override fun initialize(onResult: (ready: Boolean) -> Unit) {
            pendingInit = onResult
        }

        fun finishInit() = pendingInit!!.invoke(ready)

        override fun setListener(listener: TtsEngine.Listener?) {
            registered = listener
        }

        override fun speak(text: String, locale: Locale) {
            spoken += text
        }

        override fun stop() {
            stops++
        }

        override fun release() {
            released = true
        }

        /** 지금 문장을 끝까지 말한 것처럼 신호를 보낸다. */
        fun complete() {
            registered!!.onStart()
            registered!!.onDone()
        }
    }

    private val fr = Locale.FRANCE
    private val en = Locale.US

    private fun TestScope.speaker(engine: FakeEngine): GuidanceSpeaker {
        val clock = Clock { testScheduler.currentTime }
        return GuidanceSpeaker(engine, backgroundScope, clock).also {
            it.start()
            engine.finishInit()
        }
    }

    private fun g(id: GuidanceId) = Guidance(id)

    @Test
    fun speaksOneAtATime_inOrder() = runTest {
        val engine = FakeEngine()
        val s = speaker(engine)
        s.say(g(GuidanceId.WELCOME_FIRST_TIME), "A", en)
        s.say(g(GuidanceId.LHD_INTRO), "B", en)
        assertEquals(listOf("A"), engine.spoken)
        assertEquals("A", s.current.value?.text)
        engine.complete()
        assertEquals(listOf("A", "B"), engine.spoken)
        engine.complete()
        assertNull(s.current.value)
        assertTrue(s.idle.value)
    }

    @Test
    fun higherPriorityWaitingItem_goesNext_withoutInterruptingCurrent() = runTest {
        val engine = FakeEngine()
        val s = speaker(engine)
        s.say(g(GuidanceId.WELCOME_FIRST_TIME), "welcome", en)
        s.say(g(GuidanceId.ARRIVED), "arrived", en)
        s.say(g(GuidanceId.SCHOOL_ZONE_CROSSWALK), "school", en)
        assertEquals(0, engine.stops)
        engine.complete()
        engine.complete()
        assertEquals(listOf("welcome", "school", "arrived"), engine.spoken)
    }

    @Test
    fun itemsQueuedBeforeEngineReady_areSpokenAfterInit() = runTest {
        val engine = FakeEngine()
        val s = GuidanceSpeaker(engine, backgroundScope, Clock { testScheduler.currentTime })
        s.start()
        s.say(g(GuidanceId.WELCOME_FIRST_TIME), "A", en)
        assertTrue(engine.spoken.isEmpty())
        assertFalse(s.idle.value)
        engine.finishInit()
        assertEquals(listOf("A"), engine.spoken)
    }

    @Test
    fun unavailableLanguage_fallsBackToCaption_andIsRemembered() = runTest {
        val engine = FakeEngine()
        val s = speaker(engine)
        s.say(g(GuidanceId.WELCOME_FIRST_TIME), "bonjour", fr)
        engine.registered!!.onError(TtsError.UNAVAILABLE_LANGUAGE)
        assertEquals(setOf(fr), s.unavailableLocales.value)
        assertEquals(GuidanceSpeaker.Caption(g(GuidanceId.WELCOME_FIRST_TIME), "bonjour", voiced = false), s.current.value)

        s.say(g(GuidanceId.LHD_INTRO), "volant", fr)
        advanceTimeBy(GuidanceSpeaker.captionOnlyMs("bonjour") + 1)
        // 두 번째 프랑스어 문장은 엔진에 보내지 않고 자막으로만 보여 준다
        assertEquals(listOf("bonjour"), engine.spoken)
        assertEquals(GuidanceSpeaker.Caption(g(GuidanceId.LHD_INTRO), "volant", voiced = false), s.current.value)
        advanceTimeBy(GuidanceSpeaker.captionOnlyMs("volant") + 1)
        assertTrue(s.idle.value)
    }

    @Test
    fun engineInitFailure_usesCaptionsOnly() = runTest {
        val engine = FakeEngine(ready = false)
        val s = speaker(engine)
        s.say(g(GuidanceId.WELCOME_FIRST_TIME), "A", en)
        assertTrue(engine.spoken.isEmpty())
        assertEquals(false, s.current.value?.voiced)
        advanceTimeBy(GuidanceSpeaker.captionOnlyMs("A") + 1)
        assertTrue(s.idle.value)
    }

    @Test
    fun otherError_showsCaptionThenContinues() = runTest {
        val engine = FakeEngine()
        val s = speaker(engine)
        s.say(g(GuidanceId.WELCOME_FIRST_TIME), "A", en)
        s.say(g(GuidanceId.LHD_INTRO), "B", en)
        engine.registered!!.onError(TtsError.OTHER)
        assertEquals(emptySet<Locale>(), s.unavailableLocales.value)
        advanceTimeBy(GuidanceSpeaker.captionOnlyMs("A") + 1)
        assertEquals(listOf("A", "B"), engine.spoken)
    }

    @Test
    fun missingDoneSignal_timesOutAndMovesOn() = runTest {
        val engine = FakeEngine()
        val s = speaker(engine)
        s.say(g(GuidanceId.WELCOME_FIRST_TIME), "A", en)
        s.say(g(GuidanceId.LHD_INTRO), "B", en)
        advanceTimeBy(GuidanceSpeaker.speechTimeoutMs("A") + 1)
        runCurrent()
        assertEquals(listOf("A", "B"), engine.spoken)
        assertEquals(1, engine.stops)
    }

    @Test
    fun lateDoneBeforeStart_isIgnored() = runTest {
        val engine = FakeEngine()
        val s = speaker(engine)
        s.say(g(GuidanceId.WELCOME_FIRST_TIME), "A", en)
        s.say(g(GuidanceId.LHD_INTRO), "B", en)
        engine.registered!!.onDone() // onStart 없이 온 완료 신호
        assertEquals(listOf("A"), engine.spoken)
        engine.complete()
        assertEquals(listOf("A", "B"), engine.spoken)
    }

    @Test
    fun stopAll_clearsQueueAndStopsVoice() = runTest {
        val engine = FakeEngine()
        val s = speaker(engine)
        s.say(g(GuidanceId.WELCOME_FIRST_TIME), "A", en)
        s.say(g(GuidanceId.LHD_INTRO), "B", en)
        s.stopAll()
        assertEquals(1, engine.stops)
        assertNull(s.current.value)
        assertTrue(s.idle.value)
        engine.complete()
        assertEquals(listOf("A"), engine.spoken)
    }

    @Test
    fun release_releasesEngine() = runTest {
        val engine = FakeEngine()
        val s = speaker(engine)
        s.release()
        assertTrue(engine.released)
        assertNull(engine.registered)
    }
}
