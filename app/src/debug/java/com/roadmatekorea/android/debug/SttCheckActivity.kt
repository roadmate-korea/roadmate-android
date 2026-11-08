package com.roadmatekorea.android.debug

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import java.io.File
import java.util.Locale

/**
 * 디버그 빌드 전용: Pleos STT가 언어별로 실제 알아듣는지 확인한다.
 *
 * adb shell am start -n com.roadmatekorea.android/.debug.SttCheckActivity --es locale fr-CA
 *   [--es mode ON_DEVICE|SERVER|HYBRID]
 *   [--es file /data/local/tmp/x.pcm]   16kHz 16bit mono PCM을 sendAudio로 넣는다(없으면 마이크로 request)
 * 결과: adb logcat -s RoadMateSttCheck
 */
class SttCheckActivity : Activity() {

    private lateinit var stt: SpeechToText
    private lateinit var status: TextView
    private val main = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply { textSize = 28f; setPadding(48, 48, 48, 48) }
        setContentView(status)

        val locale = Locale.forLanguageTag(intent.getStringExtra("locale") ?: "en-US")
        val mode = intent.getStringExtra("mode")?.let { runCatching { Mode.valueOf(it) }.getOrNull() } ?: Mode.ON_DEVICE
        val file = intent.getStringExtra("file")?.let(::File)
        log("locale=${locale.toLanguageTag()} mode=$mode file=$file")

        stt = SpeechToText(this, mode)
        stt.addListener(object : ResultListener {
            override fun onUpdated(text: String, isFinal: Boolean) = log("onUpdated final=$isFinal \"$text\"" + if (isFinal) " RESULT=OK" else "")
            override fun onUpdatedEpdData(start: Long, end: Long) = log("epd $start..$end")
            override fun onStartedRecognition() = log("onStartedRecognition")
            override fun onEndedRecognition() = log("onEndedRecognition")
            override fun onError(error: ErrorType) = log("onError $error RESULT=FAIL")
            override fun onReady() = log("onReady")
            override fun onUpdatedRms(rms: Double) = Unit
        })
        runCatching {
            stt.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    log("initialize ok")
                    main.post { start(locale, file) }
                }
                override fun onFailure(error: ErrorType) = log("initialize fail $error RESULT=INIT_FAIL")
            })
        }.onFailure { log("initialize threw $it RESULT=INIT_FAIL") }
    }

    private fun start(locale: Locale, file: File?) = runCatching {
        stt.setLocale(locale)
        if (file == null) {
            stt.request()
            log("request() 마이크 듣는 중")
        } else {
            val bytes = file.readBytes()
            log("sendAudio ${bytes.size} bytes")
            Thread {
                runCatching {
                    bytes.asList().chunked(3200).forEach { chunk ->
                        stt.sendAudio(chunk.toByteArray())
                        Thread.sleep(100)
                    }
                    stt.completeAudioSend()
                    log("completeAudioSend")
                }.onFailure { log("sendAudio threw $it RESULT=FAIL") }
            }.start()
        }
    }.onFailure { log("start threw $it RESULT=FAIL") }

    override fun onDestroy() {
        runCatching { stt.release() }
        super.onDestroy()
    }

    private fun log(message: String) {
        Log.i(TAG, message)
        main.post { status.append(message + "\n") }
    }

    private companion object {
        const val TAG = "RoadMateSttCheck"
    }
}
