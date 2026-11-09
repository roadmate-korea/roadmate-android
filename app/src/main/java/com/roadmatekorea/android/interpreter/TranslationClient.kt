package com.roadmatekorea.android.interpreter

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume

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

/**
 * 실제 서버. 요청 전체(연결, 보내기, 받기)를 [timeoutMs] 안에 끝내지 못하면 실패로 본다(구현 계획서의 앱 쪽 기대).
 * 기한이 지나거나 호출한 쪽이 취소하면 연결을 끊어 막혀 있던 읽기·쓰기를 바로 끝낸다. 응답은 [maxResponseBytes]까지만 읽는다.
 */
class HttpTranslationClient(
    private val baseUrl: String,
    private val timeoutMs: Long = 3_000,
    private val maxResponseBytes: Int = 64 * 1024,
) : TranslationClient {

    override suspend fun translate(text: String, from: Locale, to: Locale, situation: Situation): String? =
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val connection = runCatching {
                    URL(baseUrl.trimEnd('/') + "/translate").openConnection() as HttpURLConnection
                }.getOrElse {
                    cont.resume(null)
                    return@suspendCancellableCoroutine
                }
                // 기한 초과나 취소: 연결을 끊으면 막혀 있던 입출력이 예외로 끝난다
                cont.invokeOnCancellation { connection.disconnect() }
                Dispatchers.IO.asExecutor().execute {
                    val result = runCatching { exchange(connection, text, from, to, situation) }.getOrNull()
                    connection.disconnect()
                    if (cont.isActive) cont.resume(result)
                }
            }
        }

    private fun exchange(connection: HttpURLConnection, text: String, from: Locale, to: Locale, situation: Situation): String? {
        connection.apply {
            requestMethod = "POST"
            connectTimeout = timeoutMs.toInt()
            readTimeout = timeoutMs.toInt()
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        val body = JSONObject()
            .put("text", text)
            .put("from", from.toLanguageTag())
            .put("to", to.toLanguageTag())
            .put("situation", situation.name.lowercase(Locale.ROOT))
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        if (connection.responseCode !in 200..299) return null
        val bytes = connection.inputStream.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                if (out.size() > maxResponseBytes) return null
            }
            out.toByteArray()
        }
        return JSONObject(bytes.toString(Charsets.UTF_8)).optString("text").takeIf { it.isNotBlank() }
    }
}

/** 서버 주소가 없을 때(release): 번역하지 않는다. 화면은 미리 번역해 둔 문장 카드로 대신한다. */
object NoTranslationClient : TranslationClient {
    override suspend fun translate(text: String, from: Locale, to: Locale, situation: Situation): String? = null
}
