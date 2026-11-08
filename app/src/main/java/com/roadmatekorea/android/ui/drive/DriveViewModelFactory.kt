package com.roadmatekorea.android.ui.drive

import android.util.Log
import com.roadmatekorea.android.BuildConfig
import com.roadmatekorea.android.RoadMateApplication
import com.roadmatekorea.android.i18n.GuidanceStrings
import com.roadmatekorea.android.i18n.withLanguage
import com.roadmatekorea.android.voice.PleosTtsEngine

private const val TAG = "RoadMateGuidance"

/** 실제 앱의 [DriveViewModel]: Pleos TTS와 프로필 언어 리소스를 붙인다. */
fun createDriveViewModel(app: RoadMateApplication) = DriveViewModel(
    hub = app.signalHub,
    ttsEngine = PleosTtsEngine(app),
    textFor = { profile ->
        val resources = app.withLanguage(profile.language).resources
        val text: (com.roadmatekorea.android.rules.Guidance) -> String = { guidance ->
            // 디버그 빌드에서만 어떤 안내를 냈는지 남긴다(위치·경로 정보는 없음)
            if (BuildConfig.DEBUG) Log.d(TAG, "${profile.language} ${guidance.id} ${guidance.args}")
            GuidanceStrings.text(resources, guidance)
        }
        text
    },
)
