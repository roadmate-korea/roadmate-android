package com.roadmatekorea.android.voice

import java.util.Locale

/**
 * 음성 질문을 듣는 엔진(Pleos STT를 감싼다). JVM 테스트에서는 가짜를 넣는다.
 * 콜백은 메인 스레드에서 온다. 한 번에 하나만 듣는다.
 *
 * 개인정보: 인식한 문장을 저장하거나 기록(로그)하지 않는다. 화면에 잠깐 보여 주고 답을 고르는 데만 쓴다.
 */
interface SpeechInput {

    interface Listener {
        /** 엔진이 준비되어 실제로 듣기 시작했다(처음에는 초기화 시간이 걸린다). */
        fun onListening() {}

        /** 듣는 중의 중간 결과(화면 표시용). */
        fun onPartial(text: String)

        /** 질문 하나가 끝났다. 비어 있을 수 있다. */
        fun onFinal(text: String)

        /** 엔진 오류(마이크, 언어, 초기화 실패 등). */
        fun onError()
    }

    /**
     * [locale]로 듣기 시작한다. [audio]가 있으면 마이크 대신 그 PCM(16kHz 16bit mono)을 실제 엔진에 넣는다
     * (마이크가 없는 에뮬레이터에서 시연 영상을 만들 때, 디버그 빌드에서만 쓴다).
     */
    fun listen(locale: Locale, listener: Listener, audio: ByteArray? = null)

    /** 처음 질문이 늦지 않게 엔진을 미리 준비한다(마이크는 열지 않는다). */
    fun prepare() {}

    /** 듣기를 멈춘다. 이 뒤에 오는 결과는 버린다. */
    fun stop()

    fun release()
}
