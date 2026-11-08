package com.roadmatekorea.android.i18n

import com.roadmatekorea.android.profile.model.AppLanguage
import java.util.Locale

/**
 * 앱 언어 → 화면 리소스와 TTS에 쓸 로케일.
 * 언어를 추가할 때는 values-xx 리소스와 여기 한 줄만 더하면 된다.
 *
 * Pleos 온디바이스 TTS 확인 결과(에뮬레이터 2.1.0, 2026-11-08):
 * en-US, fr-CA, es-US, es-MX, ko-KR은 재생되고 fr-FR, es-ES, en-GB는 ON_DEVICE_ENGINE_ERROR가 난다.
 * 그래서 프랑스어는 캐나다, 스페인어는 미국 로케일을 쓴다. 리소스는 values-fr, values-es가 그대로 쓰인다.
 */
object AppLocales {

    fun localeOf(language: AppLanguage): Locale = when (language) {
        AppLanguage.EN -> Locale.US
        AppLanguage.FR -> Locale.CANADA_FRENCH
        AppLanguage.ES -> Locale.forLanguageTag("es-US")
        AppLanguage.KO -> Locale.KOREA
    }
}
