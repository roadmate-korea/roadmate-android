package com.roadmatekorea.android.interpreter

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** 실제 HTTP(이 컴퓨터 안의 서버)로 요청 형식, 전체 기한, 취소, 응답 크기 상한을 확인한다. */
class HttpTranslationClientTest {

    /** 이 컴퓨터 안의 아주 작은 HTTP 서버(요청 하나에 [handler] 한 번). */
    private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    private val url = "http://127.0.0.1:${server.localPort}"
    private var handler: (body: String, send: (Int, ByteArray) -> OutputStream) -> Unit = { _, send -> send(404, ByteArray(0)) }

    /** 서버가 받은 연결 수. */
    private val accepted = java.util.concurrent.atomic.AtomicInteger()

    init {
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                accepted.incrementAndGet()
                thread(isDaemon = true) { serve(socket) }
            }
        }
    }

    private fun serve(socket: Socket): Unit = socket.use {
        val input = socket.getInputStream()
        // 머리글(빈 줄까지)
        val head = StringBuilder()
        while (!head.endsWith("\r\n\r\n")) {
            val b = input.read()
            if (b < 0) return@use
            head.append(b.toChar())
        }
        val length = Regex("(?i)content-length: *(\\d+)").find(head)?.groupValues?.get(1)?.toInt() ?: 0
        val body = ByteArray(length).also { var read = 0; while (read < length) read += input.read(it, read, length - read) }
        val out = socket.getOutputStream()
        runCatching {
            handler(body.toString(Charsets.UTF_8)) { code, first ->
                out.write("HTTP/1.1 $code X\r\nContent-Type: application/json\r\nConnection: close\r\n\r\n".toByteArray())
                out.write(first)
                out.flush()
                out
            }
        }
    }

    @After
    fun tearDown() = server.close()

    private fun respond(h: (body: String, send: (Int, ByteArray) -> OutputStream) -> Unit) {
        handler = h
    }

    @Test
    fun sendsTextLanguagesAndSituation_andReadsTheTranslation() = runBlocking {
        var received: JSONObject? = null
        respond { body, send ->
            received = JSONObject(body)
            send(200, """{"text":"¿Va a pagar con tarjeta?"}""".toByteArray())
        }
        val result = HttpTranslationClient(url).translate("카드로 결제하시겠어요?", Locale.KOREA, Locale("es", "US"), Situation.GAS_STATION).text
        assertEquals("¿Va a pagar con tarjeta?", result)
        val sent = received!!
        assertEquals("카드로 결제하시겠어요?", sent.getString("text"))
        assertEquals("ko-KR", sent.getString("from"))
        assertEquals("es-US", sent.getString("to"))
        assertEquals("gas_station", sent.getString("situation"))
        assertEquals("문장, 언어, 상황만 보낸다", setOf("text", "from", "to", "situation"), sent.keySet())
    }

    @Test
    fun aClarificationReply_meansAskAgain() = runBlocking {
        respond { _, send -> send(200, """{"clarification":"어느 쪽 말씀이세요?","clarification_locale":"ko-KR"}""".toByteArray()) }
        assertEquals(
            TranslationResult.AskAgain,
            HttpTranslationClient(url).translate("그거요", Locale.KOREA, Locale.US, Situation.PARKING),
        )
    }

    @Test
    fun availability_followsThePairsTheAiServerTranslates() {
        val client = HttpTranslationClient(url)
        val others = listOf(Locale.US, Locale("es", "US"), Locale("es", "MX"))
        // 한국어 ↔ 영어·스페인어 6방향
        for (other in others) {
            assertEquals(TranslationAvailability.READY, client.availability(other, Locale.KOREA))
            assertEquals(TranslationAvailability.READY, client.availability(Locale.KOREA, other))
        }
        val rejected = TranslationAvailability.LANGUAGE_NOT_SUPPORTED
        assertEquals("fr-CA는 서버가 422", rejected, client.availability(Locale.CANADA_FRENCH, Locale.KOREA))
        assertEquals("같은 언어(한국어 프로필)는 서버가 422", rejected, client.availability(Locale.KOREA, Locale.KOREA))
        assertEquals("한국어가 없는 쌍은 서버가 422", rejected, client.availability(Locale.US, Locale("es", "US")))
        assertEquals(TranslationAvailability.NO_SERVER, NoTranslationClient.availability(Locale.US, Locale.KOREA))
    }

    @Test
    fun serverErrors_andUnreachableServers_fail() = runBlocking {
        respond { _, send -> send(500, ByteArray(0)) }
        assertNull(HttpTranslationClient(url).translate("a", Locale.KOREA, Locale.US, Situation.PARKING).text)
        assertNull(HttpTranslationClient("http://127.0.0.1:1").translate("a", Locale.KOREA, Locale.US, Situation.PARKING).text)
    }

    @Test
    fun aServerThatKeepsTrickling_stillFailsWithinTheOverallDeadline() = runBlocking {
        // 읽기 제한(timeout)보다 짧은 간격으로 조금씩 계속 보내는 서버
        respond { _, send ->
            val out = send(200, "{".toByteArray())
            repeat(50) {
                Thread.sleep(100)
                out.write(" ".toByteArray())
                out.flush()
            }
        }
        val started = System.nanoTime()
        val result = HttpTranslationClient(url, timeoutMs = 500).translate("a", Locale.KOREA, Locale.US, Situation.PARKING).text
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertNull(result)
        assertTrue("전체 기한 안에 끝남: ${elapsedMs}ms", elapsedMs < 1_500)
    }

    @Test
    fun cancelling_closesTheConnection() = runBlocking {
        val closed = CountDownLatch(1)
        respond { _, send ->
            val out = send(200, "{".toByteArray())
            // 클라이언트가 연결을 끊으면 쓰기가 실패한다
            try {
                repeat(100) {
                    Thread.sleep(50)
                    out.write(" ".toByteArray())
                    out.flush()
                }
            } catch (e: java.io.IOException) {
                closed.countDown()
            }
        }
        val call = async(Dispatchers.Default) {
            HttpTranslationClient(url, timeoutMs = 10_000).translate("a", Locale.KOREA, Locale.US, Situation.PARKING).text
        }
        delay(300)
        call.cancel()
        val disconnected = withContext(Dispatchers.IO) { closed.await(3, TimeUnit.SECONDS) }
        assertTrue("취소하면 연결을 끊음", disconnected)
    }

    @Test
    fun anOversizedResponse_fails() = runBlocking {
        respond { _, send -> send(200, ("""{"text":"""" + "a".repeat(100_000) + """"}""").toByteArray()) }
        assertNull(HttpTranslationClient(url, maxResponseBytes = 1_024).translate("a", Locale.KOREA, Locale.US, Situation.PARKING).text)
    }

    /** 테스트가 [runAll]을 부를 때까지 작업을 쌓아 두는 실행기(입출력 작업이 늦게 시작되는 상황). */
    private class HeldExecutor : java.util.concurrent.Executor {
        private val queued = mutableListOf<Runnable>()
        override fun execute(command: Runnable) {
            synchronized(queued) { queued += command }
        }
        fun runAll() = synchronized(queued) { queued.toList().also { queued.clear() } }.forEach { it.run() }
    }

    @Test
    fun cancelledBeforeTheWorkStarts_neverConnects() = runBlocking {
        respond { _, send -> send(200, """{"text":"x"}""".toByteArray()) }
        val held = HeldExecutor()
        val call = async(Dispatchers.Default) {
            HttpTranslationClient(url, executor = held).translate("a", Locale.KOREA, Locale.US, Situation.PARKING).text
        }
        delay(100)
        call.cancel()
        call.join()
        held.runAll()
        delay(200)
        assertEquals("취소한 뒤에는 연결하지 않음", 0, accepted.get())
    }

    @Test
    fun deadlinePassedBeforeTheWorkStarts_neverConnects() = runBlocking {
        respond { _, send -> send(200, """{"text":"x"}""".toByteArray()) }
        val held = HeldExecutor()
        val result = HttpTranslationClient(url, timeoutMs = 100, executor = held).translate("a", Locale.KOREA, Locale.US, Situation.PARKING).text
        assertNull(result)
        held.runAll()
        delay(200)
        assertEquals("기한이 지난 뒤에는 연결하지 않음", 0, accepted.get())
    }
}
