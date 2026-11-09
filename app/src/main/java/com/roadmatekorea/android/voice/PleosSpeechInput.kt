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
 * - **엔진을 질문마다 해제하지 않는다.** SDK의 `stop()`은 종료 작업을 따로 예약하고 바로 돌아오는데, 그 직후 `release()`하면
 *   늦게 실행된 종료 작업이 해제한 엔진을 서비스에 다시 연결한다. 그래서 한 엔진을 계속 쓴다.
 * - **결과를 받으면 `stop()`으로 스트림을 닫는다.**
 * - **도중에 멈추면(취소, 시간 초과, 오류) [SETTLE_MS] 동안 다음 질문을 시작하지 않고 그 사이 신호를 버린다.** 결과에는 요청
 *   번호가 없고, 도중에 멈춘 뒤에는 "인식 끝" 신호도 오지 않으며, 약 2.2초 뒤 늦은 신호가 왔다(에뮬레이터 실측). 다음 질문은
 *   화면에서는 바로 "듣는 중"이 되고, 실제 듣기는 기다림이 끝난 뒤 시작한다.
 * - **언어가 바뀌면 새 엔진을 만든다.** 한 엔진에서 언어를 바꾸면 서비스가 처음 언어 모델로 계속 들었다. 이전 엔진은 위 경합을
 *   피하려고 해제하지 않고 [release]까지 둔다(언어 변경은 드물다).
 * - 인식한 문장은 로그에 남기지 않는다(SDK 자체 로그는 release 빌드에서 제거한다. proguard-rules.pro).
 */
class PleosSpeechInput(context: Context) : SpeechInput {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    /** 엔진 하나. 콜백은 이 객체에 묶여 있어 다른(이전) 엔진의 신호를 구분할 수 있다. */
    private inner class Engine {
        val stt: SpeechToText = SpeechToText(appContext, Mode.ON_DEVICE)
        var ready = false
        var failed = false

        /** 마지막으로 setLocale한 언어. null이면 아직 듣지 않았다. */
        var locale: Locale? = null

        val listener = object : ResultListener {
            override fun onUpdated(text: String, isFinal: Boolean) {
                main.post {
                    val owner = ownerFor(this@Engine) ?: return@post
                    if (isFinal) {
                        end(stopEngine = true, settle = false)
                        owner.onFinal(text)
                    } else {
                        owner.onPartial(text)
                    }
                }
            }

            override fun onError(error: ErrorType) {
                main.post {
                    Log.w(TAG, "stt error $error")
                    val owner = ownerFor(this@Engine) ?: return@post
                    end(stopEngine = true, settle = true)
                    owner.onError()
                }
            }

            override fun onUpdatedEpdData(start: Long, end: Long) = Unit
            override fun onStartedRecognition() = Unit
            override fun onEndedRecognition() = Unit
            override fun onReady() = Unit
            override fun onUpdatedRms(rms: Double) = Unit
        }
    }

    private var engine: Engine? = null

    /** 언어가 바뀌어 더 쓰지 않는 엔진. 해제 경합을 피하려고 [release]까지 둔다. */
    private val oldEngines = mutableListOf<Engine>()

    /** 지금 듣는 질문(엔진에서 실제로 듣기 시작한 것). */
    private var listening: SpeechInput.Listener? = null

    /** 시작을 기다리는 질문(엔진 준비나 멈춘 뒤 기다림이 끝나면 시작). */
    private var waiting: Pending? = null

    private class Pending(val listener: SpeechInput.Listener, val locale: Locale, val audio: ByteArray?)

    /** 이 시각(elapsedRealtime)까지는 새 질문을 시작하지 않는다. */
    private var settleUntil = 0L

    /** 녹음 PCM을 보내는 중인 질문. 끝나면 null이 되어 남은 음성을 더 보내지 않는다. */
    @Volatile
    private var sending: SpeechInput.Listener? = null

    override fun prepare() {
        if (engine == null) engine = createEngine()
    }

    override fun listen(locale: Locale, listener: SpeechInput.Listener, audio: ByteArray?) {
        // 앞 질문이 남아 있으면 그만둔다(그 결과는 버려진다)
        if (listening != null) stop()
        waiting = Pending(listener, locale, audio)
        tryStart()
    }

    private fun tryStart() {
        val next = waiting ?: return
        var current = engine
        if (current == null || current.failed || (current.locale != null && current.locale != next.locale)) {
            current?.let { if (!it.failed) oldEngines += it }
            current = createEngine() ?: return fail(next.listener)
            engine = current
        }
        if (!current.ready) return // 준비되면 다시 부른다
        val wait = settleUntil - SystemClock.elapsedRealtime()
        if (wait > 0) {
            main.postDelayed(::tryStart, wait)
            return
        }
        waiting = null
        begin(current, next)
    }

    private fun createEngine(): Engine? {
        val created = runCatching { Engine() }.getOrElse {
            Log.w(TAG, "stt create failed", it)
            return null
        }
        runCatching {
            created.stt.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    main.post {
                        created.ready = true
                        // 리스너는 초기화가 끝난 뒤에 붙인다(먼저 붙이면 앱이 죽는다는 다른 팀 기록)
                        runCatching { created.stt.addListener(created.listener) }
                        if (engine === created) tryStart()
                    }
                }

                override fun onFailure(error: ErrorType) {
                    main.post {
                        Log.w(TAG, "stt init failed $error")
                        created.failed = true
                        if (engine === created) waiting?.let { waiting = null; it.listener.onError() }
                    }
                }
            })
        }.onFailure {
            Log.w(TAG, "stt init threw", it)
            return null
        }
        return created
    }

    private fun begin(current: Engine, next: Pending) {
        listening = next.listener
        runCatching {
            if (current.locale != next.locale) {
                current.stt.setLocale(next.locale)
                current.locale = next.locale
            }
            if (next.audio == null) {
                current.stt.request()
            } else {
                sending = next.listener
                Thread {
                    runCatching {
                        for (chunk in next.audio.asList().chunked(CHUNK_BYTES)) {
                            if (sending !== next.listener) return@runCatching
                            current.stt.sendAudio(chunk.toByteArray())
                            Thread.sleep(CHUNK_MS)
                        }
                        if (sending === next.listener) current.stt.completeAudioSend()
                    }.onFailure { Log.w(TAG, "stt sendAudio failed", it) }
                }.start()
            }
            next.listener.onListening()
        }.onFailure {
            Log.w(TAG, "stt start failed", it)
            end(stopEngine = true, settle = true)
            next.listener.onError()
        }
    }

    /** 지금 엔진에서 지금 질문을 듣는 중일 때만 그 질문을 돌려준다. 그 밖의 신호(이전 엔진, 멈춘 뒤의 늦은 신호)는 버린다. */
    private fun ownerFor(source: Engine): SpeechInput.Listener? = listening?.takeIf { source === engine }

    /** 지금 질문을 끝낸다. [settle]이면 늦은 신호가 다음 질문에 섞이지 않게 잠시 기다린다. */
    private fun end(stopEngine: Boolean, settle: Boolean) {
        listening = null
        sending = null
        val current = engine
        if (stopEngine && current != null && current.ready) runCatching { current.stt.stop() }
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
        (oldEngines + listOfNotNull(engine)).forEach { e ->
            runCatching { e.stt.removeListener(e.listener) }
            runCatching { e.stt.release() }
        }
        oldEngines.clear()
        engine = null
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
