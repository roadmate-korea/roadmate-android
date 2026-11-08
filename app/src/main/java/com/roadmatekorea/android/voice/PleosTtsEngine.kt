package com.roadmatekorea.android.voice

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Locale

/**
 * Pleos TextToSpeech(ON_DEVICE) 래퍼. 앱 등록과 네트워크 없이 쓴다.
 * SDK 콜백 스레드가 정해져 있지 않으므로 모든 콜백을 메인 스레드로 옮긴다.
 *
 * 해제: SDK의 stop/speak는 내부 코루틴으로 비동기 실행되고 공개 API로 완료를 기다릴 수 없다.
 * 해제 직전에 stop을 부르면 해제 뒤 그 작업이 서비스에 다시 붙을 수 있어(3.3.1 바이트코드 확인),
 * 해제할 때는 stop을 부르지 않는다. 엔진은 화면(Activity) 수명에 맞춰 한 번만 만들고 한 번만 해제한다.
 */
class PleosTtsEngine(context: Context) : TtsEngine {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var listener: TtsEngine.Listener? = null
    private var currentLocale: Locale? = null
    private var released = false

    private val eventListener = object : EventListener {
        override fun onStart() = post { listener?.onStart() }
        override fun onReady() = Unit
        override fun onDone() = post { listener?.onDone() }
        override fun onStop() = post { listener?.onStopped() }
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onError(type: ErrorType) {
            Log.w(TAG, "TTS error: $type (locale=$currentLocale)")
            post { listener?.onError(if (type == ErrorType.UNAVAILABLE_LANGUAGE) TtsError.UNAVAILABLE_LANGUAGE else TtsError.OTHER) }
        }
    }

    override fun initialize(onResult: (ready: Boolean) -> Unit) {
        if (released) return post { onResult(false) }
        val engine = try {
            TextToSpeech(appContext, Mode.ON_DEVICE)
        } catch (e: RuntimeException) {
            Log.w(TAG, "TTS create failed", e)
            post { onResult(false) }
            return
        }
        tts = engine
        try {
            engine.initialize(object : OnInitializeListener {
                override fun onSuccess() = post {
                    if (released) return@post
                    engine.addEventListener(eventListener)
                    onResult(true)
                }

                override fun onFailure(type: ErrorType) {
                    Log.w(TAG, "TTS initialize failed: $type")
                    post { onResult(false) }
                }
            })
        } catch (e: RuntimeException) {
            Log.w(TAG, "TTS initialize threw", e)
            post { onResult(false) }
        }
    }

    override fun setListener(listener: TtsEngine.Listener?) {
        this.listener = listener
    }

    override fun speak(text: String, locale: Locale) {
        if (released) return
        val engine = tts ?: return post { listener?.onError(TtsError.OTHER) }
        try {
            if (locale != currentLocale) {
                engine.setLocale(locale)
                currentLocale = locale
            }
            engine.speak(text, true)
        } catch (e: RuntimeException) {
            Log.w(TAG, "TTS speak failed", e)
            post { listener?.onError(TtsError.OTHER) }
        }
    }

    override fun stop() {
        if (released) return
        runCatching { tts?.stop() }
    }

    override fun release() {
        if (released) return
        released = true
        listener = null
        val engine = tts ?: return
        tts = null
        runCatching {
            engine.removeEventListener(eventListener)
            engine.release()
        }
        main.removeCallbacksAndMessages(null)
    }

    private fun post(block: () -> Unit) {
        main.post(block)
    }

    private companion object {
        const val TAG = "RoadMateTts"
    }
}
