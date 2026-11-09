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
 * Pleos STT는 끝 글자를 빠뜨리기도 해서(에뮬레이터: "s'il vous plaî", "결제하시겠어") 글자 85% 이상 같으면 같은 문장으로 본다.
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

        const val MIN_SIMILARITY = 0.85

        /** [key]와 가장 비슷하고 [MIN_SIMILARITY] 이상인 항목. */
        fun <T> Iterable<T>.closest(key: String, text: (T) -> String): T? =
            map { it to similarity(key, normalize(text(it))) }
                .filter { it.second >= MIN_SIMILARITY }
                .maxByOrNull { it.second }?.first

        /** 1 - 편집 거리 / 긴 쪽 길이. */
        fun similarity(a: String, b: String): Double {
            if (a.isEmpty() || b.isEmpty()) return if (a == b) 1.0 else 0.0
            var prev = IntArray(b.length + 1) { it }
            for (i in 1..a.length) {
                val cur = IntArray(b.length + 1)
                cur[0] = i
                for (j in 1..b.length) {
                    cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
                }
                prev = cur
            }
            return 1.0 - prev[b.length].toDouble() / maxOf(a.length, b.length)
        }

        /** 악센트, 대소문자, 문장부호, 공백을 무시하고 비교한다. */
        fun normalize(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
                .let { Normalizer.normalize(it, Normalizer.Form.NFC) }
                .lowercase(Locale.ROOT)
                .replace(Regex("[^\\p{L}\\p{Nd}]+"), "")
    }
}
