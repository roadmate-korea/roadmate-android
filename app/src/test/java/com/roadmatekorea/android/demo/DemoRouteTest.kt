package com.roadmatekorea.android.demo

import com.roadmatekorea.android.drive.DriveSession
import com.roadmatekorea.android.drive.RawSignal
import com.roadmatekorea.android.profile.model.DriverProfile
import com.roadmatekorea.android.rules.Clock
import com.roadmatekorea.android.voice.GuidanceSpeaker
import com.roadmatekorea.android.voice.TtsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * Same Road, Different Guidance: 같은 DemoRoute를 두 페르소나로 재생하면 서로 다른 안내가 나온다.
 * 실제 앱과 같은 DemoSignalSource → DriveSession → GuidanceEngine → GuidanceSpeaker 경로를 쓴다.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DemoRouteTest {

    /** 문장 길이에 비례해 말하는 가짜 TTS(에뮬레이터 실측 약 67ms/글자). 0이면 바로 끝난다. */
    private class TimedTts(private val scope: CoroutineScope, private val msPerChar: Long) : TtsEngine {
        val spoken = mutableListOf<String>()
        private var listener: TtsEngine.Listener? = null
        override fun initialize(onResult: (ready: Boolean) -> Unit) = onResult(true)
        override fun setListener(listener: TtsEngine.Listener?) {
            this.listener = listener
        }
        override fun speak(text: String, locale: Locale) {
            spoken += text
            listener?.onStart()
            if (msPerChar == 0L) {
                listener?.onDone()
            } else {
                scope.launch {
                    delay(text.length * msPerChar)
                    listener?.onDone()
                }
            }
        }
        override fun stop() = Unit
        override fun release() = Unit
    }

    /** 실제 영어/스페인어 문장 길이로 재생 시간을 흉내 내려고, 문장 대신 ID를 말하되 길이는 실제 문장을 따른다. */
    private val englishLengths = loadLengths("values")
    private val spanishLengths = loadLengths("values-es")

    private fun loadLengths(folder: String): Map<String, Int> {
        val xml = File("src/main/res/$folder/guidance_strings.xml").readText()
        return Regex("""<string name="guidance_([a-z_]+)">(.*?)</string>""").findAll(xml)
            .associate { it.groupValues[1].uppercase() to it.groupValues[2].length }
    }

    private fun TestScope.play(persona: DemoPersona, msPerChar: Long): List<String> {
        val tts = TimedTts(backgroundScope, msPerChar)
        val clock = Clock { testScheduler.currentTime }
        val speaker = GuidanceSpeaker(tts, backgroundScope, clock).also { it.start() }
        val lengths = if (persona.profile.language.name == "ES") spanishLengths else englishLengths
        // 말한 ID를 기록하되, 재생 시간은 실제 문장 길이만큼 걸리게 한다
        val session = DriveSession(
            profile = persona.profile,
            speaker = speaker,
            textOf = { g -> (listOf(g.id.name) + g.args).joinToString(":").padEnd(lengths.getValue(g.id.name)) },
            locale = Locale.US,
            clock = clock,
        )
        backgroundScope.launch {
            DemoSignalSource(awaitIdle = { speaker.idle.first { it } }).signals().collect(session::onSignal)
        }
        // backgroundScope 작업은 advanceUntilIdle이 기다리지 않으므로 가상 시간을 충분히 앞당긴다
        advanceTimeBy(10 * 60_000L)
        runCurrent()
        return tts.spoken.map { it.trim() }
    }

    private val nakamuraExpected = listOf(
        "WELCOME_RIGHT_SIDE", "LHD_INTRO",
        "SCHOOL_ZONE_AHEAD:300", "SCHOOL_ZONE_CROSSWALK",
        "SCHOOL_ZONE_SPEED_LIMIT:30",
        "BUS_LANE_AHEAD:250",
        "LHD_MIRROR_CHECK",
        "RIGHT_TURN_RULE",
        "LEFT_TURN_RULE", "RIGHT_SIDE_LEFT_TURN",
        "SECTION_START:80",
        "ARRIVED",
    )

    private val carlosExpected = listOf(
        "WELCOME_FIRST_TIME",
        "SCHOOL_ZONE_AHEAD:300", "SCHOOL_ZONE_CROSSWALK",
        "SCHOOL_ZONE_SPEED_LIMIT:30",
        "BUS_LANE_AHEAD:250",
        "RIGHT_TURN_RULE",
        "LEFT_TURN_RULE",
        "SECTION_START:80",
        "ARRIVED",
    )

    @Test
    fun nakamura_instantSpeech() = runTest { assertEquals(nakamuraExpected, play(DemoPersona.NAKAMURA, 0)) }

    @Test
    fun carlos_instantSpeech() = runTest { assertEquals(carlosExpected, play(DemoPersona.CARLOS, 0)) }

    @Test
    fun nakamura_realisticSpeechDuration_nothingDropped() = runTest {
        assertEquals(nakamuraExpected, play(DemoPersona.NAKAMURA, 67))
    }

    @Test
    fun carlos_realisticSpeechDuration_nothingDropped() = runTest {
        assertEquals(carlosExpected, play(DemoPersona.CARLOS, 67))
    }

    @Test
    fun sameRoad_differentGuidance() = runTest {
        val nakamura = play(DemoPersona.NAKAMURA, 0)
        val carlos = play(DemoPersona.CARLOS, 0)
        assertNotEquals(nakamura, carlos)
        // 필수 안전 경고는 둘 다
        listOf("SCHOOL_ZONE_AHEAD:300", "SCHOOL_ZONE_SPEED_LIMIT:30").forEach {
            assertTrue(it in nakamura && it in carlos)
        }
        // 카를로스에게는 통행 방향 비교, 좌핸들 적응 안내가 없다
        assertTrue(carlos.none { it in setOf("WELCOME_RIGHT_SIDE", "RIGHT_SIDE_LEFT_TURN", "LHD_INTRO", "LHD_MIRROR_CHECK") })
    }

    @Test
    fun personas_matchScenario() {
        assertEquals(com.roadmatekorea.android.profile.model.Personas.NAKAMURA, DemoPersona.NAKAMURA.profile)
        assertEquals(com.roadmatekorea.android.profile.model.Personas.CARLOS, DemoPersona.CARLOS.profile)
    }

    @Test
    fun demoRoute_usesOnlySdkShapedSignals_andEndsParked() {
        val all = DemoRoute.DEFAULT.flatMap { it.signals }
        assertTrue(all.none { it is RawSignal.Vehicle.Unavailable || it is RawSignal.Navi.Unavailable })
        assertEquals(RawSignal.Vehicle.Gear(ai.pleos.playground.vehicle.constant.control.VehicleGear.GEAR_PARK), all.last())
    }

    @Suppress("unused")
    private fun unused(p: DriverProfile) = p
}
