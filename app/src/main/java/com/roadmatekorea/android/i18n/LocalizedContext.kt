package com.roadmatekorea.android.i18n

import android.content.Context
import android.content.res.Configuration
import com.roadmatekorea.android.profile.model.AppLanguage

/** 시스템 언어가 아니라 프로필 언어로 리소스를 꺼내는 Context. 화면과 TTS 문장이 같은 언어를 쓴다. */
fun Context.withLanguage(language: AppLanguage): Context {
    val config = Configuration(resources.configuration)
    config.setLocale(AppLocales.localeOf(language))
    return createConfigurationContext(config)
}
