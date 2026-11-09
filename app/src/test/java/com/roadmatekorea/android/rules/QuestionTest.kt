package com.roadmatekorea.android.rules

import com.roadmatekorea.android.rules.QuestionTopic.BUS_LANE
import com.roadmatekorea.android.rules.QuestionTopic.LEFT_TURN
import com.roadmatekorea.android.rules.QuestionTopic.REPEAT
import com.roadmatekorea.android.rules.QuestionTopic.RIGHT_TURN
import com.roadmatekorea.android.rules.QuestionTopic.SCHOOL_ZONE
import com.roadmatekorea.android.rules.QuestionTopic.SECTION_CHECK
import com.roadmatekorea.android.rules.QuestionTopic.SPEED_LIMIT
import com.roadmatekorea.android.rules.QuestionTopic.UNKNOWN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionTest {

    private fun assertTopic(topic: QuestionTopic, vararg questions: String) =
        questions.forEach { assertEquals(it, topic, QuestionMatcher.match(it)) }

    @Test
    fun english() {
        assertTopic(RIGHT_TURN, "Can I turn right on red?", "what about turning right")
        assertTopic(LEFT_TURN, "How do left turns work here?", "Can I turn left on green")
        assertTopic(SCHOOL_ZONE, "What should I do in a school zone?", "speed limit near schools")
        assertTopic(BUS_LANE, "Can I drive in the bus lane?")
        assertTopic(SECTION_CHECK, "What is an average speed check?", "what is a section camera")
        assertTopic(SPEED_LIMIT, "What's the speed limit here?", "How fast can I go")
        assertTopic(REPEAT, "Can you repeat that?", "say that again")
    }

    @Test
    fun french() {
        assertTopic(RIGHT_TURN, "Est-ce que je peux tourner à droite au feu rouge ?")
        assertTopic(LEFT_TURN, "Comment tourner à gauche ici ?")
        assertTopic(SCHOOL_ZONE, "Que faire dans une zone scolaire ?", "près d'une école")
        assertTopic(BUS_LANE, "Puis-je rouler dans la voie de bus ?")
        assertTopic(SECTION_CHECK, "C'est quoi le contrôle de vitesse moyenne ?")
        assertTopic(SPEED_LIMIT, "Quelle est la limite de vitesse ici ?", "Quelle est la vitesse maximale")
        assertTopic(REPEAT, "Tu peux répéter ?", "redis-le encore")
    }

    @Test
    fun spanish() {
        assertTopic(RIGHT_TURN, "¿Puedo girar a la derecha en rojo?")
        assertTopic(LEFT_TURN, "¿Cómo giro a la izquierda?")
        assertTopic(SCHOOL_ZONE, "¿Qué hago en una zona escolar?", "cerca de una escuela")
        assertTopic(BUS_LANE, "¿Puedo usar el carril bus?", "¿y los autobuses?")
        assertTopic(SECTION_CHECK, "¿Qué es el control de velocidad media?", "¿qué es un tramo?")
        assertTopic(SPEED_LIMIT, "¿Cuál es el límite de velocidad aquí?", "velocidad máxima")
        assertTopic(REPEAT, "¿Puedes repetir?", "otra vez por favor")
    }

    @Test
    fun korean() {
        assertTopic(RIGHT_TURN, "빨간불에 우회전해도 돼?", "오른쪽으로 돌 때")
        assertTopic(LEFT_TURN, "비보호 좌회전은 어떻게 해?")
        assertTopic(SCHOOL_ZONE, "어린이보호구역에서는?", "스쿨존 제한속도")
        assertTopic(BUS_LANE, "버스전용차로 들어가도 돼?")
        assertTopic(SECTION_CHECK, "구간 단속이 뭐야?")
        assertTopic(SPEED_LIMIT, "여기 제한 속도 얼마야?", "제한속도")
        assertTopic(REPEAT, "다시 말해줘")
    }

    @Test
    fun wordBoundaries_andUnknown() {
        // "bright"는 "right"가 아니고 "abuse"는 "bus"가 아니다
        assertTopic(UNKNOWN, "It is very bright today", "that would be abuse", "where is the gas station", "", "   ")
        assertEquals(UNKNOWN, QuestionMatcher.match(null))
    }

    @Test
    fun priority_whenSeveralTopicsAppear() {
        assertTopic(SCHOOL_ZONE, "What is the speed limit in a school zone?")
        assertTopic(SECTION_CHECK, "What is the average speed limit?")
        assertTopic(REPEAT, "repeat the right turn rule")
    }

    @Test
    fun normalize_removesAccentsAndPunctuation_butKeepsHangul() {
        assertEquals("limite de velocidad", QuestionMatcher.normalize("¿Límite de VELOCIDAD?"))
        assertEquals("tourner a droite", QuestionMatcher.normalize("Tourner à droite !"))
        assertEquals("제한 속도", QuestionMatcher.normalize("제한 속도?"))
    }

    @Test
    fun answers_useNaviLimitOnly_andRepeatTheLastDrivingGuidance() {
        assertEquals(Guidance(GuidanceId.ANSWER_SPEED_LIMIT, listOf(50)), QuestionAnswers.answer(SPEED_LIMIT, 50, null))
        // 제한 속도를 모르면 추정하지 않는다
        assertEquals(Guidance(GuidanceId.ANSWER_SPEED_LIMIT_UNKNOWN), QuestionAnswers.answer(SPEED_LIMIT, null, null))
        assertEquals(Guidance(GuidanceId.ANSWER_SPEED_LIMIT_UNKNOWN), QuestionAnswers.answer(SPEED_LIMIT, 0, null))
        assertEquals(Guidance(GuidanceId.RIGHT_TURN_RULE), QuestionAnswers.answer(RIGHT_TURN, null, null))
        assertEquals(Guidance(GuidanceId.LEFT_TURN_RULE), QuestionAnswers.answer(LEFT_TURN, null, null))
        val last = Guidance(GuidanceId.SCHOOL_ZONE_AHEAD, listOf(300))
        assertEquals(last, QuestionAnswers.answer(REPEAT, null, last))
        assertEquals(Guidance(GuidanceId.ANSWER_HELP), QuestionAnswers.answer(REPEAT, null, null))
        assertEquals(Guidance(GuidanceId.ANSWER_HELP), QuestionAnswers.answer(UNKNOWN, 50, last))
    }

    @Test
    fun answers_areNotSafetyWarnings_soRealWarningsStillGoFirst() {
        GuidanceId.entries.filter { QuestionAnswers.isAnswer(it) }.forEach {
            assertEquals(it.name, Priority.RULE, it.priority)
        }
        assertTrue(QuestionAnswers.isAnswer(GuidanceId.ANSWER_HELP))
        assertFalse(QuestionAnswers.isAnswer(GuidanceId.RIGHT_TURN_RULE))
    }
}
