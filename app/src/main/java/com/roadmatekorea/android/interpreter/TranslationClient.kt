package com.roadmatekorea.android.interpreter

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * 통역 번역. 서버(서버·AI 팀)가 맡고, 앱은 구현 계획서의 API 제안대로 부른다.
 *
 * `POST {baseUrl}/translate` `{"text", "from", "to", "situation"}` → `{"text"}`. 언어는 BCP 47 태그(예: fr-CA, ko-KR).
 * 차 밖으로 나가는 것은 번역할 문장, 언어, 상황뿐이다(위치·주행 기록 없음).
 */
interface TranslationClient {
    /** 번역 결과. 실패(연결 없음, 시간 초과, 서버 오류)면 null. */
    suspend fun translate(text: String, from: Locale, to: Locale, situation: Situation): String?
}

/** 실제 서버. 3초 안에 답이 없으면 실패로 본다(구현 계획서의 앱 쪽 기대). */
class HttpTranslationClient(private val baseUrl: String, private val timeoutMs: Int = 3_000) : TranslationClient {

    override suspend fun translate(text: String, from: Locale, to: Locale, situation: Situation): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL(baseUrl.trimEnd('/') + "/translate").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = timeoutMs
                    readTimeout = timeoutMs
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                try {
                    val body = JSONObject()
                        .put("text", text)
                        .put("from", from.toLanguageTag())
                        .put("to", to.toLanguageTag())
                        .put("situation", situation.name.lowercase(Locale.ROOT))
                    connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                    if (connection.responseCode !in 200..299) return@runCatching null
                    val response = connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                    JSONObject(response).optString("text").takeIf { it.isNotBlank() }
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }
}

/** 서버 주소가 없을 때(release): 번역하지 않는다. 화면은 미리 번역해 둔 문장 카드로 대신한다. */
object NoTranslationClient : TranslationClient {
    override suspend fun translate(text: String, from: Locale, to: Locale, situation: Situation): String? = null
}
