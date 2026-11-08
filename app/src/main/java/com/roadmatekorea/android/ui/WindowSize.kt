package com.roadmatekorea.android.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 좁은 창인지. Pleos Connect는 앱 영역에 앱 두 개를 나란히 띄울 수 있다(내비 + RoadMate).
 * 그때 RoadMate 창은 폭이 절반(약 840dp)이 되므로, 가로로 나눠 놓은 화면을 세로로 쌓는다.
 */
val LocalCompact = staticCompositionLocalOf { false }

/** 창 폭이 이보다 좁으면 세로 배치를 쓴다. 앱 영역 전체는 약 1680dp, 반으로 나누면 약 840dp. */
val COMPACT_WIDTH = 1200.dp

@Composable
fun ProvideWindowSize(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalCompact provides (maxWidth < COMPACT_WIDTH), content = content)
    }
}
