package com.roadmatekorea.android.interpreter

import java.text.Normalizer
import java.util.Locale

/**
 * 시연용 가짜 서버(디버그 빌드에서 서버 주소가 없을 때만). 서버와 같은 형식으로 정해진 문장만 번역한다.
 *
 * - 운전자 말: 미리 번역해 둔 문장([phraseTexts]: 운전자 언어 문장 → 한국어)과 같으면 그 한국어
 * - 직원 말: 아래 시연 대화 몇 개
 * 그 밖의 문장은 실패(null)로 돌려준다. 실제 번역은 서버가 한다.
 *
 * 악센트·대소문자·문장부호·공백을 무시하고 같아야 한다. 단, Pleos STT가 끝 글자를 빠뜨리는 일이 있어(에뮬레이터:
 * "s'il vous plaî", "결제하시겠어") 끝에서 [MAX_DROPPED_TAIL]글자까지 빠진 것은 같은 문장으로 본다.
 * 금액·시간처럼 중간이 다른 문장은 섞지 않는다(비슷하다고 고르면 "1만 원어치"가 "5만 원어치"로 번역된다).
 */
class DemoTranslationClient(private val phraseTexts: Map<String, String>) : TranslationClient {

    override suspend fun translate(text: String, from: Locale, to: Locale, situation: Situation): String? {
        val key = normalize(text)
        if (to.language == "ko") return phraseTexts.entries.closest(key) { it.key }?.value
        val row = STAFF.closest(key) { it.korean } ?: return null
        return when (to.language) {
            "fr" -> row.fr
            "es" -> row.es
            else -> row.en
        }
    }

    private class Row(val korean: String, val en: String, val fr: String, val es: String)

    private companion object {
        /** 시연 대화: 직원이 하는 말(한국어)과 번역. */
        val STAFF = listOf(
            Row("카드로 결제하시겠어요?", "Will you pay by card?", "Vous payez par carte ?", "¿Va a pagar con tarjeta?"),
            Row("주유구 좀 열어 주세요.", "Please open the fuel door.", "Ouvrez la trappe à carburant, s'il vous plaît.", "Abra la tapa del depósito, por favor."),
            Row("내일 오후 6시까지 반납해 주세요.", "Please return it by 6 p.m. tomorrow.", "Rendez-la avant 18 h demain, s'il vous plaît.", "Devuélvalo mañana antes de las 6 de la tarde, por favor."),
            Row("이쪽에 주차하시면 됩니다.", "You can park over here.", "Vous pouvez vous garer ici.", "Puede aparcar aquí."),
        )

        const val MAX_DROPPED_TAIL = 3

        /** [key]와 같거나, [key]가 끝 글자 몇 개만 빠진 항목. */
        fun <T> Iterable<T>.closest(key: String, text: (T) -> String): T? {
            if (key.isEmpty()) return null
            return firstOrNull { normalize(text(it)) == key }
                ?: filter { candidate ->
                    val full = normalize(text(candidate))
                    full.startsWith(key) && full.length - key.length <= MAX_DROPPED_TAIL
                }.singleOrNull()
        }

        /** 악센트, 대소문자, 문장부호, 공백을 무시하고 비교한다. */
        fun normalize(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
                .let { Normalizer.normalize(it, Normalizer.Form.NFC) }
                .lowercase(Locale.ROOT)
                .replace(Regex("[^\\p{L}\\p{Nd}]+"), "")
    }
}
