package com.roadmatekorea.android.ui.scene

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.roadmatekorea.android.ui.theme.RoadMate
import com.roadmatekorea.android.ui.theme.RoadMateColors
import com.roadmatekorea.android.ui.theme.RoadMateType

/** 위에서 본 상황 그림. Figma의 scene 컴포넌트와 같은 그림이다. */
enum class SceneKind {
    KEEP_RIGHT, RIGHT_TURN_RED, UNPROTECTED_LEFT, LEFT_TURN_ARROW, LEFT_TURN_KEEP_RIGHT,
    SCHOOL_ZONE, LANE_CHANGE_LHD, BUS_LANE, SECTION_CHECK,
}

/**
 * 상황 그림(640×480 비율). 내 차와 내 길은 노랑, 빨강은 멈출 곳과 안 되는 것, 흰색은 차선과 다른 차.
 * 장식이라 접근성 설명은 카드 글자가 맡는다.
 * [speedLimitKmh]: 어린이보호구역 노면 숫자. NaviHelper 값을 알 때만 그린다(고정값이나 추정 금지).
 */
@Composable
fun Scene(kind: SceneKind, modifier: Modifier = Modifier, speedLimitKmh: Int? = null) {
    val colors = RoadMate.colors
    val measurer = rememberTextMeasurer()
    Canvas(modifier.aspectRatio(640f / 480f)) {
        val p = ScenePainter(this, colors, size.width / 640f)
        when (kind) {
            SceneKind.KEEP_RIGHT -> p.keepRight()
            SceneKind.RIGHT_TURN_RED -> p.rightTurnRed()
            SceneKind.UNPROTECTED_LEFT -> p.unprotectedLeft()
            SceneKind.LEFT_TURN_ARROW -> p.leftTurnArrow()
            SceneKind.LEFT_TURN_KEEP_RIGHT -> p.leftTurnKeepRight()
            SceneKind.SCHOOL_ZONE -> {
                p.schoolZone()
                if (speedLimitKmh != null && speedLimitKmh > 0) {
                val text = measurer.measure(speedLimitKmh.toString(), TextStyle(fontFamily = RoadMateType.numberL.fontFamily, fontWeight = RoadMateType.numberL.fontWeight, fontSize = (56 * p.u / density).sp, color = colors.paint))
                drawText(text, topLeft = Offset(372 * p.u - text.size.width / 2f, 84 * p.u))
                }
            }
            SceneKind.LANE_CHANGE_LHD -> p.laneChangeLhd()
            SceneKind.BUS_LANE -> {
                p.busLane()
                val text = measurer.measure("BUS", TextStyle(fontFamily = RoadMateType.numberL.fontFamily, fontWeight = RoadMateType.numberL.fontWeight, fontSize = (24 * p.u / density).sp, color = colors.paint))
                drawText(text, topLeft = Offset(352 * p.u - text.size.width / 2f, 300 * p.u))
            }
            SceneKind.SECTION_CHECK -> p.sectionCheck()
        }
    }
}

/** 640×480 좌표로 그린다. [u]는 1단위의 픽셀 크기. */
private class ScenePainter(private val d: DrawScope, private val c: RoadMateColors, val u: Float) {

    private fun rect(x: Float, y: Float, w: Float, h: Float, color: Color, alpha: Float = 1f) =
        d.drawRect(color, Offset(x * u, y * u), Size(w * u, h * u), alpha = alpha)

    private fun round(x: Float, y: Float, w: Float, h: Float, r: Float, color: Color, stroke: Float? = null, dash: Boolean = false) {
        if (stroke == null) {
            d.drawRoundRect(color, Offset(x * u, y * u), Size(w * u, h * u), CornerRadius(r * u))
        } else {
            d.drawRoundRect(
                color, Offset(x * u, y * u), Size(w * u, h * u), CornerRadius(r * u),
                style = Stroke(stroke * u, pathEffect = if (dash) PathEffect.dashPathEffect(floatArrayOf(10 * u, 6 * u)) else null),
            )
        }
    }

    private fun line(x1: Float, y1: Float, x2: Float, y2: Float, color: Color, width: Float, dash: FloatArray? = null) =
        d.drawLine(
            color, Offset(x1 * u, y1 * u), Offset(x2 * u, y2 * u), strokeWidth = width * u, cap = StrokeCap.Butt,
            pathEffect = dash?.let { PathEffect.dashPathEffect(it.map { v -> v * u }.toFloatArray()) },
        )

    private fun laneDash(x1: Float, y1: Float, x2: Float, y2: Float) = line(x1, y1, x2, y2, c.paint, 4f, floatArrayOf(24f, 16f))

    private fun path(color: Color, width: Float, dash: Boolean = false, fill: Boolean = false, alpha: Float = 1f, build: Path.() -> Unit) {
        val path = Path().apply(build)
        if (fill) d.drawPath(path, color, alpha = alpha)
        else d.drawPath(
            path, color,
            style = Stroke(width * u, cap = StrokeCap.Butt, join = StrokeJoin.Miter, pathEffect = if (dash) PathEffect.dashPathEffect(floatArrayOf(18 * u, 12 * u)) else null),
        )
    }

    private fun Path.m(x: Float, y: Float) = moveTo(x * u, y * u)
    private fun Path.l(x: Float, y: Float) = lineTo(x * u, y * u)
    private fun Path.cb(x1: Float, y1: Float, x2: Float, y2: Float, x: Float, y: Float) = cubicTo(x1 * u, y1 * u, x2 * u, y2 * u, x * u, y * u)

    private fun arrowHead(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, color: Color = c.centerline) =
        path(color, 0f, fill = true) { m(x1, y1); l(x2, y2); l(x3, y3); close() }

    /** 내 차(노랑, 위를 향함) */
    private fun car(x: Float, y: Float) {
        round(x, y, 34f, 58f, 9f, c.centerline)
        round(x + 5, y + 11, 24f, 12f, 3f, c.onCenterline)
    }

    private fun cross(x: Float, y: Float, s: Float = 30f) {
        line(x, y, x + s, y + s, c.danger, 6f)
        line(x + s, y, x, y + s, c.danger, 6f)
    }

    private fun doubleCenterVertical(y: Float, h: Float) {
        rect(315f, y, 4f, h, c.centerline)
        rect(321f, y, 4f, h, c.centerline)
    }

    /** 십자 교차로: 세로 길(내 길, 위로 감)과 가로 길, 아래와 오른쪽 횡단보도, 내 쪽 정지선. */
    private fun intersection() {
        rect(220f, 0f, 200f, 480f, c.asphalt)
        rect(0f, 140f, 640f, 160f, c.asphalt)
        doubleCenterVertical(0f, 118f)
        doubleCenterVertical(362f, 118f)
        rect(0f, 216f, 196f, 4f, c.centerline); rect(0f, 222f, 196f, 4f, c.centerline)
        rect(490f, 216f, 150f, 4f, c.centerline); rect(490f, 222f, 150f, 4f, c.centerline)
        laneDash(270f, 0f, 270f, 118f); laneDash(370f, 0f, 370f, 118f)
        laneDash(270f, 362f, 270f, 480f); laneDash(370f, 362f, 370f, 480f)
        laneDash(0f, 180f, 196f, 180f); laneDash(0f, 260f, 196f, 260f)
        laneDash(490f, 180f, 640f, 180f); laneDash(490f, 260f, 640f, 260f)
        for (i in 0 until 8) rect(226f + i * 24, 312f, 14f, 28f, c.paint)
        for (i in 0 until 6) rect(448f, 150f + i * 24, 28f, 14f, c.paint)
        rect(322f, 350f, 96f, 6f, c.paint)
    }

    /** 신호등. 켜진 등 하나만 색을 넣는다. */
    private fun signal(lit: Color) {
        round(440f, 318f, 96f, 34f, 6f, c.onCenterline)
        listOf(c.danger to 458f, c.centerline to 488f, c.go to 518f).forEach { (color, x) ->
            d.drawCircle(if (color == lit) color else c.asphalt, 10 * u, Offset(x * u, 335 * u))
        }
    }

    /** 좌회전 화살표 신호: 빨강, 노랑은 꺼지고 세 번째 칸에 녹색 왼쪽 화살표 */
    private fun arrowSignal() {
        round(440f, 318f, 96f, 34f, 6f, c.onCenterline)
        d.drawCircle(c.asphalt, 10 * u, Offset(458 * u, 335 * u))
        d.drawCircle(c.asphalt, 10 * u, Offset(488 * u, 335 * u))
        line(528f, 335f, 512f, 335f, c.go, 4f)
        arrowHead(516f, 327f, 506f, 335f, 516f, 343f, c.go)
    }

    /**
     * 사람 픽토그램(아이콘 icon/children과 같은 모양). [ox], [oy]는 32 격자 원점의 그림 좌표, [s]는 격자 한 칸의 크기.
     * 머리와 몸(다리 포함)을 면으로 채운다. 막대 인형은 쓰지 않는다.
     */
    private fun adultPath(ox: Float, oy: Float, s: Float) = Path().apply {
        addOval(Rect(Offset((ox + 10.5f * s) * u, (oy + 5.5f * s) * u), 3f * s * u))
        figureBody(ox, oy, s, left = 7f, top = 13f, width = 7f, legGap = 1.5f, crotch = 20f)
    }

    private fun childPath(ox: Float, oy: Float, s: Float) = Path().apply {
        addOval(Rect(Offset((ox + 22f * s) * u, (oy + 12f * s) * u), 2.5f * s * u))
        figureBody(ox, oy, s, left = 19f, top = 18.5f, width = 6f, legGap = 1.5f, crotch = 23f)
    }

    /** 위가 둥근 몸통과 두 다리를 한 덩어리로. 발끝은 격자 28. */
    private fun Path.figureBody(ox: Float, oy: Float, s: Float, left: Float, top: Float, width: Float, legGap: Float, crotch: Float) {
        fun x(v: Float) = (ox + v * s) * u
        fun y(v: Float) = (oy + v * s) * u
        val r = width / 2
        val leg = (width - legGap) / 2
        moveTo(x(left), y(top))
        arcTo(Rect(x(left), y(top - r), x(left + width), y(top + r)), 180f, 180f, false)
        lineTo(x(left + width), y(28f))
        lineTo(x(left + width - leg), y(28f))
        lineTo(x(left + width - leg), y(crotch))
        lineTo(x(left + leg), y(crotch))
        lineTo(x(left + leg), y(28f))
        lineTo(x(left), y(28f))
        close()
    }

    /** 어른이 아이 손을 잡은 모습. */
    private fun adultAndChild(ox: Float, oy: Float, s: Float, color: Color) {
        d.drawPath(adultPath(ox, oy, s), color)
        d.drawPath(childPath(ox, oy, s), color)
        val arm = Path().apply { m(ox + 14f * s, oy + 13.5f * s); l(ox + 16.75f * s, oy + 20.5f * s); l(ox + 19f * s, oy + 18.5f * s) }
        d.drawPath(arm, color, style = Stroke(2.2f * s * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    /** 건너는 사람 한 명. 노면 위에서도 보이게 흰 테두리를 두른다. */
    private fun pedestrian(x: Float, y: Float) {
        val s = 1.5f
        val figure = adultPath(x - 10.5f * s, y - 9f - 2.5f * s, s)
        d.drawPath(figure, c.paint, style = Stroke(4 * u, join = StrokeJoin.Round))
        d.drawPath(figure, c.onCenterline)
    }

    fun rightTurnRed() {
        intersection()
        signal(c.danger)
        rect(322f, 347f, 96f, 12f, c.danger) // 먼저 멈출 곳
        car(378f, 378f)
        path(c.centerline, 8f, dash = true) { m(395f, 338f); cb(395f, 298f, 412f, 280f, 470f, 280f); l(600f, 280f) }
        arrowHead(598f, 264f, 628f, 280f, 598f, 296f)
        pedestrian(462f, 228f)
    }

    fun unprotectedLeft() {
        intersection()
        signal(c.go)
        car(329f, 378f)
        path(c.centerline, 8f) { m(346f, 370f); l(346f, 300f); cb(346f, 228f, 318f, 180f, 250f, 180f); l(44f, 180f) }
        arrowHead(46f, 164f, 16f, 180f, 46f, 196f)
        // 마주 오는 차(흰 테두리): 먼저 보낸다
        round(278f, 36f, 34f, 58f, 9f, c.paint, stroke = 4f)
        round(283f, 72f, 24f, 12f, 3f, c.paint)
        line(295f, 104f, 295f, 128f, c.paint, 4f)
        path(c.paint, 4f) { m(285f, 122f); l(295f, 136f); l(305f, 122f) }
    }

    fun leftTurnArrow() {
        intersection()
        arrowSignal()
        car(329f, 378f)
        path(c.centerline, 8f) { m(346f, 370f); l(346f, 300f); cb(346f, 228f, 318f, 180f, 250f, 180f); l(44f, 180f) }
        arrowHead(46f, 164f, 16f, 180f, 46f, 196f)
    }

    /** 좌회전한 뒤 새 길의 오른쪽(위쪽 차로)으로. 왼쪽(좌측통행 습관) 진입은 빨간 점선과 X. 신호는 그리지 않는다. */
    fun leftTurnKeepRight() {
        intersection()
        car(329f, 378f)
        path(c.danger, 5f, dash = true) { m(346f, 300f); cb(346f, 270f, 300f, 262f, 230f, 262f); l(120f, 262f) }
        cross(78f, 248f)
        path(c.centerline, 8f) { m(346f, 370f); l(346f, 300f); cb(346f, 228f, 318f, 180f, 250f, 180f); l(44f, 180f) }
        arrowHead(46f, 164f, 16f, 180f, 46f, 196f)
    }

    private fun straightRoad(x: Float, w: Float, dashes: List<Float>) {
        rect(x, 0f, w, 480f, c.asphalt)
        doubleCenterVertical(0f, 480f)
        dashes.forEach { laneDash(it, 0f, it, 480f) }
    }

    fun schoolZone() {
        straightRoad(220f, 200f, listOf(270f, 370f))
        rect(325f, 20f, 95f, 440f, c.danger, alpha = 0.3f) // 붉은 노면
        rect(220f, 196f, 200f, 40f, c.asphalt)
        for (i in 0 until 8) rect(226f + i * 24, 200f, 14f, 32f, c.paint)
        rect(322f, 246f, 96f, 12f, c.danger)
        car(378f, 330f)
        // 길가에서 어른과 아이가 손을 잡고 있다
        adultAndChild(428f, 175f, 2.6f, c.ink)
    }

    fun laneChangeLhd() {
        straightRoad(220f, 200f, listOf(270f, 371f))
        // 오른쪽 뒤 사각지대
        path(c.centerline, 0f, fill = true, alpha = 0.32f) { m(363f, 316f); l(420f, 330f); l(420f, 460f); l(363f, 390f); close() }
        car(329f, 310f)
        d.drawCircle(c.paint, 5 * u, Offset(338 * u, 338 * u)) // 운전석(왼쪽)
        path(c.centerline, 8f) { m(346f, 304f); cb(346f, 250f, 395f, 244f, 395f, 184f) }
        arrowHead(380f, 190f, 395f, 160f, 410f, 190f)
    }

    fun keepRight() {
        straightRoad(180f, 280f, listOf(248f, 390f))
        car(373f, 300f)
        line(390f, 290f, 390f, 120f, c.centerline, 8f)
        arrowHead(375f, 124f, 390f, 94f, 405f, 124f)
        // 왼쪽 차선(좌측통행 습관)은 안 된다
        round(232f, 300f, 34f, 58f, 9f, c.danger, stroke = 4f, dash = true)
        cross(234f, 236f)
    }

    /** 한국 버스전용차로는 1차로(중앙선 바로 옆)다. 시내 중앙버스전용차로, 고속도로 버스전용차로 모두. */
    fun busLane() {
        rect(200f, 0f, 240f, 480f, c.asphalt)
        doubleCenterVertical(0f, 480f)
        laneDash(259f, 0f, 259f, 480f)
        // 1차로: 옅은 파란 바닥, 일반 차로와 나누는 파란 선
        rect(325f, 0f, 54f, 480f, c.bus, alpha = 0.24f)
        rect(379f, 0f, 5f, 480f, c.bus)
        round(333f, 30f, 38f, 110f, 8f, c.bus)
        round(338f, 38f, 28f, 14f, 3f, c.paint)
        car(395f, 330f)
        // 들어가면 안 된다
        path(c.danger, 6f, dash = true) { m(412f, 324f); cb(412f, 286f, 352f, 280f, 352f, 232f) }
        line(338f, 204f, 366f, 232f, c.danger, 7f)
        line(366f, 204f, 338f, 232f, c.danger, 7f)
    }

    fun sectionCheck() {
        rect(0f, 180f, 640f, 120f, c.asphalt)
        rect(0f, 234f, 640f, 4f, c.centerline); rect(0f, 240f, 640f, 4f, c.centerline)
        laneDash(0f, 208f, 640f, 208f); laneDash(0f, 272f, 640f, 272f)
        // 내 차(오른쪽을 향함)
        round(40f, 255f, 58f, 34f, 9f, c.centerline)
        round(75f, 260f, 12f, 24f, 3f, c.onCenterline)
        // 시작과 끝 카메라, 그 사이가 구간
        for (x in listOf(120f, 520f)) {
            round(x, 122f, 40f, 26f, 4f, c.ink)
            d.drawCircle(c.surfaceForCamera(), 6 * u, Offset((x + 14) * u, 135 * u))
            rect(x + 18, 148f, 5f, 32f, c.ink)
        }
        path(c.ink, 4f) { m(140f, 104f); l(140f, 88f); l(540f, 88f); l(540f, 104f) }
        line(110f, 272f, 600f, 272f, c.centerline, 6f, floatArrayOf(4f, 14f))
        arrowHead(596f, 260f, 620f, 272f, 596f, 284f)
    }

    /** 카메라 렌즈: 먹색 몸통 위 바탕색 점 */
    private fun RoadMateColors.surfaceForCamera() = canvas
}
