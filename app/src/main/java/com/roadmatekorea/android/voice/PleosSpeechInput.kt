package com.roadmatekorea.android.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.util.Locale

/**
 * Pleos STT(ON_DEVICE)로 질문을 듣는다. 에뮬레이터 2.1.0에서 en-US, fr-CA, es-US 인식을 확인했다.
 *
 * SDK의 수명 제약(2026-11-09 확인)에 맞춘 규칙:
 * - **엔진은 앱에 하나만 두고 해제하지 않는다.** SDK의 `stop()`은 종료 작업을 따로 예약하고 바로 돌아오는데, 그 직후
 *   `release()`하면 늦은 종료 작업이 해제한 엔진을 서비스에 다시 연결한다. 또 인식 서비스는 결과를
 *   앱(패키지)에서 마지막으로 등록한 엔진에만 보낸다(에뮬레이터: FR 엔진의 질문 결과가 나중에 만든 ES 엔진으로 갔다).
 * - **질문마다 `setLocale`을 부른다.** 앞 질문의 음성 전송과 스트림을 끝낸 뒤에는 같은 엔진에서도 언어가 바뀐다
 *   (FR → ES → FR 확인).
 * - **결과를 받으면 `stop()`으로 스트림을 닫는다.**
 * - **결과의 주인은 받는 순간 정한다.** SDK는 결과를 메인 스레드로 준다. 다시 미뤄 처리하면 그 사이 시작한 다음 질문에
 *   붙을 수 있다.
 * - **새 질문은 자기 "인식 시작" 신호를 받은 뒤의 결과만 받는다.** 결과에는 요청 번호가 없다.
 * - **도중에 멈추면(취소, 시간 초과, 오류) [SETTLE_MS] 동안 다음 질문을 시작하지 않고 그 사이 신호를 버린다.** 도중 stop 뒤에는
 *   "인식 끝"이 오지 않고 약 2.2초 뒤 늦은 신호가 왔다(에뮬레이터 실측). 그동안 화면은 "잠시만요"를 보여 주고, 실제로 듣기
 *   시작하면 [SpeechInput.Listener.onListening]으로 알린다.
 * - 인식한 문장은 로그에 남기지 않는다(SDK 자체 로그는 release 빌드에서 제거한다. proguard-rules.pro).
 */
class PleosSpeechInput(context: Context) : SpeechInput {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    /** 음성 인식 엔진(앱에 하나). */
    private inner class Engine {
        val stt: SpeechToText = SpeechToText(appContext, Mode.ON_DEVICE)
        var ready = false
        var failed = false

        val listener = object : ResultListener {
            override fun onStartedRecognition() = onMain {
                if (this@Engine === active && listening != null) heardStart = true
            }

            override fun onUpdated(text: String, isFinal: Boolean) {
                // 받는 순간의 주인을 정한다(다시 미루면 다음 질문에 붙을 수 있다)
                val owner = ownerFor(this@Engine) ?: return
                onMain {
                    if (listening !== owner) return@onMain
                    if (isFinal) {
                        end(stopEngine = true, settle = false)
                        owner.onFinal(text)
                    } else {
                        owner.onPartial(text)
                    }
                }
            }

            override fun onError(error: ErrorType) {
                Log.w(TAG, "stt error $error")
                // 오류는 인식 시작 전에도 올 수 있으므로 시작 신호를 기다리지 않는다(지금 엔진의 지금 질문이면 끝낸다)
                val owner = listening?.takeIf { this@Engine === active } ?: return
                onMain {
                    if (listening !== owner) return@onMain
                    end(stopEngine = true, settle = true)
                    owner.onError()
                }
            }

            override fun onUpdatedEpdData(start: Long, end: Long) = Unit
            override fun onEndedRecognition() = Unit
            override fun onReady() = Unit
            override fun onUpdatedRms(rms: Double) = Unit
        }
    }

    /** 엔진. 해제 경합을 피하려고 [release]까지 둔다. 초기화에 실패하면 다음 질문에서 다시 만든다. */
    private var engine: Engine? = null

    /** 지금 질문을 듣는 엔진. */
    @Volatile
    private var active: Engine? = null

    /** 지금 듣는 질문(엔진에서 실제로 듣기 시작한 것). */
    @Volatile
    private var listening: SpeechInput.Listener? = null

    /** 지금 질문의 "인식 시작" 신호를 받았는지. 받기 전의 결과는 앞 질문의 늦은 결과로 보고 버린다. */
    @Volatile
    private var heardStart = false

    /** 시작을 기다리는 질문(엔진 준비나 멈춘 뒤 기다림이 끝나면 시작). */
    private var waiting: Pending? = null

    private class Pending(val listener: SpeechInput.Listener, val locale: Locale, val audio: ByteArray?)

    /** 이 시각(elapsedRealtime)까지는 새 질문을 시작하지 않는다. */
    private var settleUntil = 0L

    /** 녹음 PCM을 보내는 중인 질문. 끝나면 null이 되어 남은 음성을 더 보내지 않는다. */
    @Volatile
    private var sending: SpeechInput.Listener? = null

    override fun prepare(locale: Locale) {
        ensureEngine()
    }

    override fun listen(locale: Locale, listener: SpeechInput.Listener, audio: ByteArray?) {
        // 앞 질문이 남아 있으면 그만둔다(그 결과는 버려진다)
        if (listening != null) stop()
        waiting = Pending(listener, locale, audio)
        tryStart()
    }

    private fun ensureEngine(): Engine? {
        engine?.takeIf { !it.failed }?.let { return it }
        val created = createEngine() ?: return null
        engine = created
        return created
    }

    private fun tryStart() {
        val next = waiting ?: return
        val engine = ensureEngine() ?: return fail(next.listener)
        if (!engine.ready) return // 준비되면 다시 부른다
        val wait = settleUntil - SystemClock.elapsedRealtime()
        if (wait > 0) {
            main.postDelayed(::tryStart, wait)
            return
        }
        waiting = null
        begin(engine, next)
    }

    private fun createEngine(): Engine? {
        val created = runCatching { Engine() }.getOrElse {
            Log.w(TAG, "stt create failed", it)
            return null
        }
        runCatching {
            created.stt.initialize(object : OnInitializeListener {
                override fun onSuccess() = onMain {
                    created.ready = true
                    // 리스너는 초기화가 끝난 뒤에 붙인다(먼저 붙이면 앱이 죽는다는 다른 팀 기록)
                    runCatching { created.stt.addListener(created.listener) }
                    if (engine === created) tryStart()
                }

                override fun onFailure(error: ErrorType) = onMain {
                    Log.w(TAG, "stt init failed $error")
                    created.failed = true
                    if (engine === created) waiting?.let { waiting = null; it.listener.onError() }
                }
            })
        }.onFailure {
            Log.w(TAG, "stt init threw", it)
            return null
        }
        return created
    }

    private fun begin(engine: Engine, next: Pending) {
        active = engine
        heardStart = false
        listening = next.listener
        runCatching {
            engine.stt.setLocale(next.locale)
            if (next.audio == null) {
                engine.stt.request()
            } else {
                sending = next.listener
                Thread {
                    runCatching {
                        for (chunk in next.audio.asList().chunked(CHUNK_BYTES)) {
                            if (sending !== next.listener) return@runCatching
                            engine.stt.sendAudio(chunk.toByteArray())
                            Thread.sleep(CHUNK_MS)
                        }
                        if (sending === next.listener) engine.stt.completeAudioSend()
                    }.onFailure { Log.w(TAG, "stt sendAudio failed", it) }
                }.start()
            }
            // 요청이 엔진에 들어갔다. 이제부터 말하면 된다(앞의 기다림이 끝났다)
            next.listener.onListening()
        }.onFailure {
            Log.w(TAG, "stt start failed", it)
            end(stopEngine = true, settle = true)
            next.listener.onError()
        }
    }

    /** 지금 엔진에서 지금 질문을 듣고 있고, 그 질문의 인식이 시작된 뒤일 때만 그 질문을 돌려준다. */
    private fun ownerFor(source: Engine): SpeechInput.Listener? =
        listening?.takeIf { source === active && heardStart }

    /** 지금 질문을 끝낸다. [settle]이면 늦은 신호가 다음 질문에 섞이지 않게 잠시 기다린다. */
    private fun end(stopEngine: Boolean, settle: Boolean) {
        listening = null
        heardStart = false
        sending = null
        val engine = active
        if (stopEngine && engine != null && engine.ready) runCatching { engine.stt.stop() }
        if (settle) settleUntil = SystemClock.elapsedRealtime() + SETTLE_MS
    }

    private fun fail(listener: SpeechInput.Listener) {
        waiting = null
        main.post { listener.onError() }
    }

    override fun stop() {
        waiting = null
        if (listening != null) end(stopEngine = true, settle = true)
    }

    /** 앱이 끝날 때. 해제 직전에는 stop을 부르지 않는다(위 경합). */
    override fun release() {
        waiting = null
        listening = null
        sending = null
        active = null
        engine?.let { e ->
            runCatching { e.stt.removeListener(e.listener) }
            runCatching { e.stt.release() }
        }
        engine = null
    }

    /** SDK 콜백은 메인 스레드로 온다. 혹시 다른 스레드면 메인으로 넘긴다. */
    private inline fun onMain(crossinline block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post { block() }
    }

    private companion object {
        const val TAG = "RoadMateStt"

        /** 도중에 멈춘 뒤 다음 질문까지 기다리는 시간. 실측한 늦은 신호(약 2.2초)보다 길게. */
        const val SETTLE_MS = 3_000L

        /** 16kHz 16bit mono 100ms = 3,200바이트. 실시간 속도로 넣는다. */
        const val CHUNK_BYTES = 3_200
        const val CHUNK_MS = 100L
    }
}
