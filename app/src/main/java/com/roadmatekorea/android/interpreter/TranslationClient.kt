package com.roadmatekorea.android.interpreter

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executor
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
 *
 * 취소와 작업 시작의 경합: 입출력 작업은 [executor]에서 나중에 시작될 수 있다. 그 전에 취소되면 [Call]이
 * 기억해 두었다가 연결을 열지 않는다. 연결하는 도중에 취소되면 끊기가 듣지 않을 수 있어, 연결 직후 본문을 보내기 전에 한 번 더 확인한다.
 */
class HttpTranslationClient(
    private val baseUrl: String,
    private val timeoutMs: Long = 3_000,
    private val maxResponseBytes: Int = 64 * 1024,
    private val executor: Executor = Dispatchers.IO.asExecutor(),
) : TranslationClient {

    override suspend fun translate(text: String, from: Locale, to: Locale, situation: Situation): String? =
        withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine { cont ->
                val call = Call { URL(baseUrl.trimEnd('/') + "/translate").openConnection() as HttpURLConnection }
                // 기한 초과나 취소: 아직 시작 전이면 시작하지 않고, 진행 중이면 연결을 끊어 막혀 있던 입출력을 끝낸다
                cont.invokeOnCancellation { call.cancel() }
                executor.execute {
                    val result = call.run { connection -> exchange(connection, text, from, to, situation, call::cancelled) }
                    if (cont.isActive) cont.resume(result)
                }
            }
        }

    /** 요청 하나. 취소는 시작 전에도 기억한다. */
    private class Call(private val open: () -> HttpURLConnection) {
        private val lock = Any()
        private var cancelled = false
        private var connection: HttpURLConnection? = null

        fun cancelled(): Boolean = synchronized(lock) { cancelled }

        fun cancel() {
            val open = synchronized(lock) {
                cancelled = true
                connection
            }
            open?.disconnect()
        }

        /** 취소되지 않았으면 연결을 만들어 [block]을 돌린다. 실패나 취소면 null. */
        fun <T> run(block: (HttpURLConnection) -> T?): T? {
            val c = synchronized(lock) {
                if (cancelled) return null
                runCatching(open).getOrNull()?.also { connection = it }
            } ?: return null
            return try {
                runCatching { block(c) }.getOrNull()
            } finally {
                c.disconnect()
            }
        }
    }

    private fun exchange(
        connection: HttpURLConnection,
        text: String,
        from: Locale,
        to: Locale,
        situation: Situation,
        cancelled: () -> Boolean,
    ): String? {
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
        connection.connect()
        // 연결하는 도중에 취소됐으면 문장을 보내지 않는다
        if (cancelled()) return null
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
