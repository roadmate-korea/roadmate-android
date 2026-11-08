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
 */
class PleosTtsEngine(context: Context) : TtsEngine {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var listener: TtsEngine.Listener? = null
    private var currentLocale: Locale? = null

    private val eventListener = object : EventListener {
        override fun onStart() = post { listener?.onStart() }
        override fun onReady() = Unit
        override fun onDone() = post { listener?.onDone() }
        override fun onStop() = post { listener?.onDone() }
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onError(type: ErrorType) {
            Log.w(TAG, "TTS error: $type (locale=$currentLocale)")
            post { listener?.onError(if (type == ErrorType.UNAVAILABLE_LANGUAGE) TtsError.UNAVAILABLE_LANGUAGE else TtsError.OTHER) }
        }
    }

    override fun initialize(onResult: (ready: Boolean) -> Unit) {
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
        runCatching { tts?.stop() }
    }

    override fun release() {
        val engine = tts ?: return
        tts = null
        listener = null
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
