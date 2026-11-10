package com.roadmatekorea.android.interpreter

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class DemoTranslationClientTest {

    private val client = DemoTranslationClient(mapOf("Le plein d'essence, s'il vous plaît." to "가득 넣어 주세요."))

    @Test
    fun driverPhrase_matchesIgnoringCaseAccentsAndPunctuation() = runTest {
        // 음성 인식은 문장부호와 악센트를 다르게 줄 수 있다
        assertEquals("가득 넣어 주세요.", client.translate("le plein dessence sil vous plait", Locale.CANADA_FRENCH, Locale.KOREA, Situation.GAS_STATION).text)
    }

    @Test
    fun staffLine_isTranslatedToTheDriverLanguage() = runTest {
        assertEquals("¿Va a pagar con tarjeta?", client.translate("카드로 결제하시겠어요", Locale.KOREA, Locale("es", "US"), Situation.GAS_STATION).text)
        assertEquals("Will you pay by card?", client.translate("카드로 결제하시겠어요?", Locale.KOREA, Locale.US, Situation.GAS_STATION).text)
    }

    @Test
    fun aDroppedLastLetterFromSpeechRecognition_stillMatches() = runTest {
        // 에뮬레이터 Pleos STT 실측 결과
        assertEquals("가득 넣어 주세요.", client.translate("le plein d'essence s'il vous plaî", Locale.CANADA_FRENCH, Locale.KOREA, Situation.GAS_STATION).text)
        assertEquals("Will you pay by card?", client.translate("카드로 결제하시겠어", Locale.KOREA, Locale.US, Situation.GAS_STATION).text)
    }

    @Test
    fun aDifferentPhraseOfTheSameShape_doesNotMatch() = runTest {
        // 휘발유와 경유처럼 한 단어만 다른 문장을 섞지 않는다
        val two = DemoTranslationClient(
            mapOf("Le plein d'essence, s'il vous plaît." to "휘발유", "Le plein de gazole, s'il vous plaît." to "경유"),
        )
        assertEquals("경유", two.translate("le plein de gazole s'il vous plai", Locale.CANADA_FRENCH, Locale.KOREA, Situation.GAS_STATION).text)
        assertEquals("휘발유", two.translate("le plein d'essence s'il vous plai", Locale.CANADA_FRENCH, Locale.KOREA, Situation.GAS_STATION).text)
    }

    @Test
    fun differentAmountsOrTimes_areNotTranslatedAsTheNearestPhrase() = runTest {
        // 비슷한 문장을 고르면 금액·시간이 바뀐다
        val amount = DemoTranslationClient(mapOf("50,000 won of fuel, please." to "5만 원어치 넣어 주세요."))
        assertNull(amount.translate("10,000 won of fuel, please.", Locale.US, Locale.KOREA, Situation.GAS_STATION).text)
        assertNull(client.translate("내일 오후 8시까지 반납해 주세요.", Locale.KOREA, Locale.US, Situation.RENTAL).text)
        assertNull(client.translate("카드로 결제하시겠어요 아니면 현금", Locale.KOREA, Locale.US, Situation.GAS_STATION).text)
        assertNull("너무 많이 빠진 것은 맞추지 않음", client.translate("카드로 결제", Locale.KOREA, Locale.US, Situation.GAS_STATION).text)
    }

    @Test
    fun anythingElse_fails() = runTest {
        assertNull(client.translate("Où sont les toilettes ?", Locale.CANADA_FRENCH, Locale.KOREA, Situation.GAS_STATION).text)
    }
}
