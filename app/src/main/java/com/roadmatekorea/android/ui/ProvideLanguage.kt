package com.roadmatekorea.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.roadmatekorea.android.i18n.withLanguage
import com.roadmatekorea.android.profile.model.AppLanguage

/** 안쪽 화면의 stringResource가 시스템 언어가 아니라 [language]를 따르게 한다. */
@Composable
fun ProvideLanguage(language: AppLanguage, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val localized = remember(base, language) { base.withLanguage(language) }
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides localized.resources.configuration,
        content = content,
    )
}
