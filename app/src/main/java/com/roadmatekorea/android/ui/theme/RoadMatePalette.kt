package com.roadmatekorea.android.ui.theme

/**
 * 색 토큰 값(0xAARRGGBB). Figma `Color` 변수(Light/Dark)와 이름과 값이 같다.
 * Compose 의존이 없어 대비 검사를 JVM 단위 테스트로 돌릴 수 있다.
 *
 * 방향 "도로 도색": 대표 색은 노란 중앙선. 빨강은 안전 경고에만, 파랑은 버스전용차로에만 쓴다.
 */
data class RoadMatePalette(
    /** bg/canvas: 화면 바탕(콘크리트 / 아스팔트) */
    val canvas: Long,
    /** bg/surface: 카드, 선택 줄 */
    val surface: Long,
    /** bg/sunken: 눌림, 비활성, 보조 칸 */
    val sunken: Long,
    /** text/primary: 본문, 제목 */
    val ink: Long,
    /** text/secondary: 보조 설명 */
    val inkSecondary: Long,
    /** text/tertiary: 작은 머리글, 흐린 안내 */
    val inkTertiary: Long,
    /** text/on-ink: 먹색 바탕 위 글자 */
    val onInk: Long,
    /** line/default: 구분선 */
    val line: Long,
    /** line/strong: 보조 버튼 테두리 */
    val lineStrong: Long,
    /** brand/centerline: 노란 중앙선. 내 차, 내 길, 주 버튼, 고른 항목 */
    val centerline: Long,
    /** brand/on-centerline: 노랑 위 글자(두 모드 모두 먹색) */
    val onCenterline: Long,
    /** road/asphalt: 그림 속 도로 */
    val asphalt: Long,
    /** road/paint: 그림 속 흰 차선, 표지판 바탕 */
    val paint: Long,
    /** road/curb: 그림 속 보도 */
    val curb: Long,
    /** signal/danger: 안전 경고, 위험 동작 */
    val danger: Long,
    /** signal/on-danger: 빨간 버튼 위 글자 */
    val onDanger: Long,
    /** signal/danger-surface: 안전 경고 카드 바탕 */
    val dangerSurface: Long,
    /** signal/bus: 버스전용차로 */
    val bus: Long,
    /** signal/go: 녹색 신호 */
    val go: Long,
) {
    companion object {
        val Light = RoadMatePalette(
            canvas = 0xFFF4F4F2,
            surface = 0xFFFFFFFF,
            sunken = 0xFFE7E7E3,
            ink = 0xFF191A17,
            inkSecondary = 0xFF4B4C47,
            inkTertiary = 0xFF6C6D67,
            onInk = 0xFFFFFFFF,
            line = 0xFFDCDCD7,
            lineStrong = 0xFFBDBDB7,
            centerline = 0xFFFFC20E,
            onCenterline = 0xFF191A17,
            asphalt = 0xFF3A3B38,
            paint = 0xFFFFFFFF,
            curb = 0xFFC9C9C3,
            danger = 0xFFC4261A,
            onDanger = 0xFFFFFFFF,
            dangerSurface = 0xFFFBE4E1,
            bus = 0xFF1F5FBF,
            go = 0xFF1E8A4C,
        )

        val Dark = RoadMatePalette(
            canvas = 0xFF121311,
            surface = 0xFF1D1E1B,
            sunken = 0xFF2A2B28,
            ink = 0xFFF2F2EE,
            inkSecondary = 0xFFBDBEB8,
            inkTertiary = 0xFF8E8F89,
            onInk = 0xFF121311,
            line = 0xFF31322E,
            lineStrong = 0xFF4A4B46,
            centerline = 0xFFFFC20E,
            onCenterline = 0xFF191A17,
            asphalt = 0xFF34352F,
            paint = 0xFFE9E9E4,
            curb = 0xFF4A4B46,
            danger = 0xFFFF5A4A,
            onDanger = 0xFF191A17,
            dangerSurface = 0xFF3A1A17,
            bus = 0xFF5A8FE6,
            go = 0xFF3DBE73,
        )

        /** WCAG 대비(1~21). */
        fun contrast(a: Long, b: Long): Double {
            val la = luminance(a)
            val lb = luminance(b)
            return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
        }

        private fun luminance(argb: Long): Double {
            fun channel(shift: Int): Double {
                val c = ((argb shr shift) and 0xFF) / 255.0
                return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
            }
            return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        }
    }
}
