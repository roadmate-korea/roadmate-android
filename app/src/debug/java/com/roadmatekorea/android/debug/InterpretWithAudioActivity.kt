package com.roadmatekorea.android.debug

import android.app.Activity
import android.os.Bundle
import android.util.Log
import com.roadmatekorea.android.RoadMateApplication
import com.roadmatekorea.android.interpreter.Side
import java.io.File

/**
 * 디버그 빌드 전용: 통역 화면의 마이크를 누른 것처럼 앱 files/의 PCM(16kHz 16bit mono)을 실제 Pleos STT에 넣는다.
 * 에뮬레이터에는 마이크가 없어서 만든 시연·확인용이다. 화면 없이 바로 끝난다.
 *
 * adb shell am start --user 10 -n com.roadmatekorea.android/.debug.InterpretWithAudioActivity --es side STAFF --es file q_ko.pcm
 */
class InterpretWithAudioActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val side = runCatching { Side.valueOf(intent.getStringExtra("side").orEmpty()) }.getOrDefault(Side.DRIVER)
        val name = intent.getStringExtra("file")
        val file = name?.let { File(filesDir, File(it).name) }
        if (file == null || !file.exists()) {
            Log.w(TAG, "no audio file: $name")
        } else {
            // 실제 마이크처럼 말 끝에 무음 1초를 붙인다(녹음이 바로 끝나면 마지막 음절이 잘려 인식됐다)
            (application as RoadMateApplication).interpreter.toggleListen(side, file.readBytes() + ByteArray(TRAILING_SILENCE_BYTES))
        }
        finish()
    }

    private companion object {
        const val TAG = "RoadMateInterpret"

        /** 16kHz 16bit mono 1초. */
        const val TRAILING_SILENCE_BYTES = 32_000
    }
}
