package com.roadmatekorea.android.debug

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.roadmatekorea.android.RoadMateApplication
import java.io.File

/**
 * 디버그 빌드 전용: 에뮬레이터에는 마이크가 없으므로, 앱 files/의 PCM(16kHz 16bit mono)을 실제 Pleos STT에 넣어
 * 주행 화면의 마이크 버튼을 누른 것처럼 질문한다. 시연 영상과 동작 확인용. 화면 없이 바로 끝난다.
 *
 * adb shell am start --user 10 -n com.roadmatekorea.android/.debug.AskWithAudioActivity --es file es.pcm
 */
class AskWithAudioActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val name = intent.getStringExtra("file")
        val file = name?.let { File(filesDir, File(it).name) }
        if (file == null || !file.exists()) {
            Log.w(TAG, "no audio file: $name")
        } else {
            (application as RoadMateApplication).driveController.toggleQuestion(file.readBytes())
        }
        finish()
    }

    private companion object {
        const val TAG = "RoadMateAsk"
    }
}
