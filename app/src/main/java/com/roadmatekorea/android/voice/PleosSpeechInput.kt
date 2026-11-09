package com.roadmatekorea.android.voice

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Locale

/**
 * Pleos STT(ON_DEVICE)로 질문을 듣는다. 에뮬레이터 2.1.0에서 en-US, fr-CA, es-US 인식을 확인했다.
 *
 * - 처음 들을 때 엔진을 초기화한다(앱을 켤 때 마이크 엔진을 미리 붙잡지 않는다).
 * - 엔진은 말 사이 쉼마다 끝난 조각을 보낼 수 있다. 질문은 짧으므로 처음 끝난 조각을 질문으로 쓴다.
 * - 멈춘 뒤에 늦게 오는 결과(앞 요청의 결과가 다음 세션에 섞여 오는 경우가 있다)는 버린다.
 * - 인식한 문장은 로그에 남기지 않는다.
 * - 한 엔진에서 언어를 바꾸면(setLocale) 인식 서비스가 처음 쓴 언어 모델로 계속 듣는다(2026-11-09 에뮬레이터:
 *   영어로 쓴 뒤 fr-CA로 바꾸자 프랑스어 질문을 영어로 인식). 그래서 언어가 바뀌면 엔진을 새로 만든다.
 */
class PleosSpeechInput(context: Context) : SpeechInput {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var stt: SpeechToText? = null
    private var ready = false
    private var initializing = false
    private var pending: (() -> Unit)? = null

    /** 지금 엔진이 듣는 언어. 다른 언어로 들으려면 엔진을 새로 만든다. */
    private var engineLocale: Locale? = null

    /** 지금 듣는 요청. null이면 결과를 버린다. */
    private var active: SpeechInput.Listener? = null

    /** 녹음 PCM을 보내는 중인 요청. 결과가 나오거나 멈추면 null이 되어 남은 음성을 더 보내지 않는다. */
    @Volatile
    private var sending: SpeechInput.Listener? = null

    private val resultListener = object : ResultListener {
        override fun onUpdated(text: String, isFinal: Boolean) {
            main.post {
                val listener = active ?: return@post
                if (isFinal) {
                    active = null
                    sending = null
                    listener.onFinal(text)
                } else {
                    listener.onPartial(text)
                }
            }
        }

        override fun onError(error: ErrorType) {
            main.post {
                Log.w(TAG, "stt error $error")
                val listener = active ?: return@post
                active = null
                sending = null
                listener.onError()
            }
        }

        override fun onUpdatedEpdData(start: Long, end: Long) = Unit
        override fun onStartedRecognition() = Unit
        override fun onEndedRecognition() = Unit
        override fun onReady() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
    }

    override fun prepare() = ensureInitialized()

    override fun listen(locale: Locale, listener: SpeechInput.Listener, audio: ByteArray?) {
        active = listener
        val start = { begin(locale, listener, audio) }
        if (ready && engineLocale != null && engineLocale != locale) releaseEngine()
        if (ready) start() else {
            pending = start
            ensureInitialized()
        }
    }

    private fun ensureInitialized() {
        if (initializing || ready) return
        initializing = true
        val engine = runCatching { SpeechToText(appContext, Mode.ON_DEVICE) }.getOrElse {
            Log.w(TAG, "stt create failed", it)
            initializing = false
            failActive()
            return
        }
        stt = engine
        runCatching {
            engine.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    main.post {
                        initializing = false
                        ready = true
                        // 리스너는 초기화가 끝난 뒤에 붙인다(먼저 붙이면 앱이 죽는다는 다른 팀 기록)
                        runCatching { engine.addListener(resultListener) }
                        val start = pending
                        pending = null
                        start?.invoke()
                    }
                }

                override fun onFailure(error: ErrorType) {
                    main.post {
                        Log.w(TAG, "stt init failed $error")
                        initializing = false
                        pending = null
                        failActive()
                    }
                }
            })
        }.onFailure {
            Log.w(TAG, "stt init threw", it)
            initializing = false
            pending = null
            failActive()
        }
    }

    private fun begin(locale: Locale, listener: SpeechInput.Listener, audio: ByteArray?) {
        if (active !== listener) return // 그 사이 멈춤
        val engine = stt ?: return failActive()
        runCatching {
            engine.setLocale(locale)
            engineLocale = locale
            if (audio == null) {
                engine.request()
            } else {
                sending = listener
                Thread {
                    runCatching {
                        for (chunk in audio.asList().chunked(CHUNK_BYTES)) {
                            if (sending !== listener) return@runCatching
                            engine.sendAudio(chunk.toByteArray())
                            Thread.sleep(CHUNK_MS)
                        }
                        if (sending === listener) engine.completeAudioSend()
                    }.onFailure { Log.w(TAG, "stt sendAudio failed", it) }
                }.start()
            }
            listener.onListening()
        }.onFailure {
            Log.w(TAG, "stt start failed", it)
            failActive()
        }
    }

    private fun failActive() {
        val listener = active ?: return
        active = null
        listener.onError()
    }

    override fun stop() {
        active = null
        sending = null
        pending = null
        if (ready) runCatching { stt?.stop() }
    }

    override fun release() {
        stop()
        releaseEngine()
    }

    private fun releaseEngine() {
        runCatching { stt?.removeListener(resultListener) }
        runCatching { stt?.release() }
        stt = null
        ready = false
        engineLocale = null
    }

    private companion object {
        const val TAG = "RoadMateStt"

        /** 16kHz 16bit mono 100ms = 3,200바이트. 실시간 속도로 넣는다. */
        const val CHUNK_BYTES = 3_200
        const val CHUNK_MS = 100L
    }
}
