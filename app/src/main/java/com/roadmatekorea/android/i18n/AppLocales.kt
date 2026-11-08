package com.roadmatekorea.android.i18n

import com.roadmatekorea.android.profile.model.AppLanguage
import java.util.Locale

/**
 * 앱 언어 → 화면 리소스와 TTS에 쓸 로케일.
 * 언어를 추가할 때는 values-xx 리소스와 여기 한 줄만 더하면 된다.
 * 실제 TTS 지원 여부는 에뮬레이터에서 확인한다.
 */
object AppLocales {

    fun localeOf(language: AppLanguage): Locale = when (language) {
        AppLanguage.EN -> Locale.US
        AppLanguage.FR -> Locale.FRANCE
        AppLanguage.ES -> Locale.forLanguageTag("es-ES")
        AppLanguage.KO -> Locale.KOREA
    }
}
