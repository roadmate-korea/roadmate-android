package com.roadmatekorea.android.debug

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.TextView
import com.roadmatekorea.android.i18n.AppLocales
import com.roadmatekorea.android.i18n.GuidanceStrings
import com.roadmatekorea.android.i18n.withLanguage
import com.roadmatekorea.android.profile.model.AppLanguage
import com.roadmatekorea.android.rules.Guidance
import com.roadmatekorea.android.rules.GuidanceId
import com.roadmatekorea.android.voice.PleosTtsEngine
import com.roadmatekorea.android.voice.TtsEngine
import com.roadmatekorea.android.voice.TtsError
import java.util.Locale

/**
 * 디버그 빌드 전용: Pleos TTS가 언어별로 실제 말하는지 확인한다.
 *
 * adb shell am start -n com.roadmatekorea.android/.debug.TtsCheckActivity --es lang FR
 *   [--es locale fr-CA]   로케일 변형 시험
 * 결과: adb logcat -s RoadMateTtsCheck RoadMateTts
 */
class TtsCheckActivity : Activity() {

    private lateinit var engine: TtsEngine
    private lateinit var status: TextView
    private var startedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply { textSize = 28f; setPadding(48, 48, 48, 48) }
        setContentView(status)

        val language = intent.getStringExtra("lang")?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() } ?: AppLanguage.EN
        val locale = intent.getStringExtra("locale")?.let(Locale::forLanguageTag) ?: AppLocales.localeOf(language)
        val text = GuidanceStrings.text(withLanguage(language).resources, Guidance(GuidanceId.WELCOME_FIRST_TIME))

        log("lang=$language locale=${locale.toLanguageTag()} text=\"$text\"")
        engine = PleosTtsEngine(this)
        engine.setListener(object : TtsEngine.Listener {
            override fun onStart() = log("onStart +${elapsed()}ms")
            override fun onDone() = log("onDone +${elapsed()}ms RESULT=OK")
            override fun onStopped() = log("onStop +${elapsed()}ms RESULT=STOPPED")
            override fun onError(error: TtsError) = log("onError $error +${elapsed()}ms RESULT=FAIL")
        })
        engine.initialize { ready ->
            log("initialize ready=$ready")
            if (ready) {
                startedAt = SystemClock.elapsedRealtime()
                engine.speak(text, locale)
            } else {
                log("RESULT=INIT_FAIL")
            }
        }
    }

    override fun onDestroy() {
        engine.release()
        super.onDestroy()
    }

    private fun elapsed() = SystemClock.elapsedRealtime() - startedAt

    private fun log(message: String) {
        Log.i(TAG, message)
        status.append(message + "\n")
    }

    private companion object {
        const val TAG = "RoadMateTtsCheck"
    }
}
