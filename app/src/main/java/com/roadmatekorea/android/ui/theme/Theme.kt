package com.roadmatekorea.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** [RoadMatePalette]를 Compose 색으로 바꾼 것. 화면 코드는 `RoadMate.colors`로 쓴다. */
@Immutable
class RoadMateColors(p: RoadMatePalette, val isDark: Boolean) {
    val canvas = Color(p.canvas)
    val surface = Color(p.surface)
    val sunken = Color(p.sunken)
    val ink = Color(p.ink)
    val inkSecondary = Color(p.inkSecondary)
    val inkTertiary = Color(p.inkTertiary)
    val onInk = Color(p.onInk)
    val line = Color(p.line)
    val lineStrong = Color(p.lineStrong)
    val centerline = Color(p.centerline)
    val onCenterline = Color(p.onCenterline)
    val asphalt = Color(p.asphalt)
    val paint = Color(p.paint)
    val curb = Color(p.curb)
    val danger = Color(p.danger)
    val onDanger = Color(p.onDanger)
    val dangerSurface = Color(p.dangerSurface)
    val bus = Color(p.bus)
    val go = Color(p.go)
}

private val LightColors = RoadMateColors(RoadMatePalette.Light, isDark = false)
private val DarkColors = RoadMateColors(RoadMatePalette.Dark, isDark = true)

private val LocalRoadMateColors = staticCompositionLocalOf { LightColors }

/** 디자인 토큰 접근점. */
object RoadMate {
    val colors: RoadMateColors
        @Composable @ReadOnlyComposable get() = LocalRoadMateColors.current
    val type: RoadMateType get() = RoadMateType
}

/**
 * 앱 테마. 시스템(차량) 주간·야간 설정을 따른다.
 * Material 기본 보라가 남지 않게 Material 색도 토큰으로 덮는다.
 */
@Composable
fun RoadMateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val c = if (darkTheme) DarkColors else LightColors
    val scheme = (if (darkTheme) darkColorScheme() else lightColorScheme()).copy(
        primary = c.centerline,
        onPrimary = c.onCenterline,
        primaryContainer = c.centerline,
        onPrimaryContainer = c.onCenterline,
        secondary = c.ink,
        onSecondary = c.onInk,
        secondaryContainer = c.sunken,
        onSecondaryContainer = c.ink,
        tertiary = c.ink,
        onTertiary = c.onInk,
        tertiaryContainer = c.sunken,
        onTertiaryContainer = c.ink,
        background = c.canvas,
        onBackground = c.ink,
        surface = c.canvas,
        onSurface = c.ink,
        surfaceVariant = c.surface,
        onSurfaceVariant = c.inkSecondary,
        outline = c.lineStrong,
        outlineVariant = c.line,
        error = c.danger,
        onError = c.onDanger,
        inverseSurface = c.ink,
        inverseOnSurface = c.onInk,
    )
    CompositionLocalProvider(LocalRoadMateColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = RoadMateType.material, content = content)
    }
}
