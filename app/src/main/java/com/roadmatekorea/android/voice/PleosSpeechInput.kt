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
 * **질문 하나에 엔진 하나.** Pleos STT 결과에는 요청 번호가 없어서, 한 엔진을 다시 쓰면 앞 질문의 늦은 결과가
 * 다음 질문의 결과로 들어올 수 있다(에뮬레이터에서 실제로 섞였다). 한 엔진에서 언어를 바꾸면 서비스가 처음 언어
 * 모델로 계속 듣는 문제도 있었다. 그래서 질문이 끝나면(답, 오류, 취소, 시간 초과 모두) 그 엔진을 멈추고 해제하며,
 * 다음 질문용 새 엔진을 바로 준비해 둔다. 해제한 엔진의 콜백은 자기 엔진에만 묶여 있어 버려진다.
 *
 * - 엔진은 말 사이 쉼마다 끝난 조각을 보낼 수 있다. 질문은 짧으므로 처음 끝난 조각을 질문으로 쓴다.
 * - 인식한 문장은 로그에 남기지 않는다(SDK 자체 로그는 release 빌드에서 제거한다. proguard-rules.pro).
 */
class PleosSpeechInput(context: Context) : SpeechInput {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    /** 엔진 하나와 그 엔진이 맡은 질문. 콜백은 이 객체에만 묶인다. */
    private inner class Engine {
        val stt: SpeechToText = SpeechToText(appContext, Mode.ON_DEVICE)
        var ready = false
        var retired = false

        /** 이 엔진이 듣는 질문. 엔진 하나는 질문 하나만 맡는다. */
        var request: SpeechInput.Listener? = null

        /** 녹음 PCM을 보내는 중이면 true. 질문이 끝나면 false가 되어 남은 음성을 더 보내지 않는다. */
        @Volatile
        var sending = false

        val listener = object : ResultListener {
            override fun onUpdated(text: String, isFinal: Boolean) {
                main.post {
                    val owner = owner() ?: return@post
                    if (isFinal) finish(this@Engine) { owner.onFinal(text) } else owner.onPartial(text)
                }
            }

            override fun onError(error: ErrorType) {
                main.post {
                    Log.w(TAG, "stt error $error")
                    val owner = owner() ?: return@post
                    finish(this@Engine) { owner.onError() }
                }
            }

            override fun onUpdatedEpdData(start: Long, end: Long) = Unit
            override fun onStartedRecognition() = Unit
            override fun onEndedRecognition() = Unit
            override fun onReady() = Unit
            override fun onUpdatedRms(rms: Double) = Unit
        }

        /** 이 엔진의 결과를 받을 질문. 엔진이 해제됐거나 지금 질문이 아니면 null(결과를 버린다). */
        fun owner(): SpeechInput.Listener? = request?.takeIf { !retired && it === active }
    }

    /** 다음 질문에 쓸 엔진(아직 아무 질문도 맡지 않음). */
    private var spare: Engine? = null

    /** 지금 듣고 있는 엔진. */
    private var busy: Engine? = null

    /** 지금 듣는 질문. */
    private var active: SpeechInput.Listener? = null

    /** 엔진이 준비되면 시작할 질문. */
    private var pendingStart: (() -> Unit)? = null

    override fun prepare() {
        if (spare == null) spare = createEngine()
    }

    override fun listen(locale: Locale, listener: SpeechInput.Listener, audio: ByteArray?) {
        // 앞 질문이 남아 있으면 정리한다(그 결과는 이제 버려진다)
        busy?.let { retire(it) }
        active = listener
        val engine = spare ?: createEngine() ?: return failNow(listener)
        spare = null
        busy = engine
        engine.request = listener
        val start = { begin(engine, locale, listener, audio) }
        if (engine.ready) start() else pendingStart = start
    }

    private fun createEngine(): Engine? {
        val engine = runCatching { Engine() }.getOrElse {
            Log.w(TAG, "stt create failed", it)
            return null
        }
        runCatching {
            engine.stt.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    main.post {
                        if (engine.retired) return@post
                        engine.ready = true
                        // 리스너는 초기화가 끝난 뒤에 붙인다(먼저 붙이면 앱이 죽는다는 다른 팀 기록)
                        runCatching { engine.stt.addListener(engine.listener) }
                        if (busy === engine) {
                            val start = pendingStart
                            pendingStart = null
                            start?.invoke()
                        }
                    }
                }

                override fun onFailure(error: ErrorType) {
                    main.post {
                        Log.w(TAG, "stt init failed $error")
                        if (spare === engine) spare = null
                        val owner = engine.owner()
                        if (owner == null) retire(engine) else finish(engine) { owner.onError() }
                    }
                }
            })
        }.onFailure {
            Log.w(TAG, "stt init threw", it)
            engine.retired = true
            return null
        }
        return engine
    }

    private fun begin(engine: Engine, locale: Locale, listener: SpeechInput.Listener, audio: ByteArray?) {
        if (engine.owner() !== listener) return // 그 사이 멈춤
        runCatching {
            engine.stt.setLocale(locale)
            if (audio == null) {
                engine.stt.request()
            } else {
                engine.sending = true
                Thread {
                    runCatching {
                        for (chunk in audio.asList().chunked(CHUNK_BYTES)) {
                            if (!engine.sending) return@runCatching
                            engine.stt.sendAudio(chunk.toByteArray())
                            Thread.sleep(CHUNK_MS)
                        }
                        if (engine.sending) engine.stt.completeAudioSend()
                    }.onFailure { Log.w(TAG, "stt sendAudio failed", it) }
                }.start()
            }
            listener.onListening()
        }.onFailure {
            Log.w(TAG, "stt start failed", it)
            finish(engine) { listener.onError() }
        }
    }

    /** 질문 하나를 끝낸다: 인식을 멈추고 엔진을 해제한 뒤 결과를 전한다. 다음 질문용 엔진을 준비한다. */
    private fun finish(engine: Engine, deliver: () -> Unit) {
        if (active === engine.request) active = null
        retire(engine)
        prepare()
        deliver()
    }

    private fun retire(engine: Engine) {
        if (engine.retired) return
        engine.retired = true
        engine.sending = false
        if (busy === engine) {
            busy = null
            pendingStart = null
        }
        if (engine.ready) runCatching { engine.stt.stop() }
        runCatching { engine.stt.removeListener(engine.listener) }
        runCatching { engine.stt.release() }
    }

    private fun failNow(listener: SpeechInput.Listener) {
        if (active === listener) active = null
        main.post { listener.onError() }
    }

    override fun stop() {
        active = null
        busy?.let {
            retire(it)
            prepare()
        }
    }

    override fun release() {
        active = null
        busy?.let { retire(it) }
        spare?.let { retire(it) }
        spare = null
    }

    private companion object {
        const val TAG = "RoadMateStt"

        /** 16kHz 16bit mono 100ms = 3,200바이트. 실시간 속도로 넣는다. */
        const val CHUNK_BYTES = 3_200
        const val CHUNK_MS = 100L
    }
}
