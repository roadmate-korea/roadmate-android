package com.roadmatekorea.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.roadmatekorea.android.R

/**
 * 글자 스타일. Figma 텍스트 스타일과 이름, 크기가 같다(Pleos 가이드: Headline 60/56/48, Title 40/36/32,
 * Body 30/28/26, Label 24/20).
 *
 * 본문 글꼴은 시스템 기본(Pleos에서는 Asta Sans, /product/etc/fonts_customization.xml)을 쓴다.
 * 숫자(제한 속도, 거리)만 고속도로 표지판 계열인 Overpass를 앱에 넣어 쓴다(OFL, assets/licenses).
 */
@OptIn(ExperimentalTextApi::class)
object RoadMateType {
    private val Sans = FontFamily.Default

    private val Overpass = FontFamily(
        Font(R.font.overpass_variable, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
        Font(R.font.overpass_variable, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
    )

    private fun sans(weight: FontWeight, size: Int, lineHeight: Double, letterSpacing: Double = 0.0) = TextStyle(
        fontFamily = Sans,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (size * lineHeight).sp,
        letterSpacing = letterSpacing.em,
    )

    private fun number(weight: FontWeight, size: Int, lineHeight: Double) = TextStyle(
        fontFamily = Overpass,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (size * lineHeight).sp,
        letterSpacing = (-0.02).em,
    )

    val headlineL = sans(FontWeight.ExtraBold, 60, 1.25)
    val headlineM = sans(FontWeight.ExtraBold, 56, 1.25)
    val headlineS = sans(FontWeight.Bold, 48, 1.25)
    val titleL = sans(FontWeight.Bold, 40, 1.3)
    val titleM = sans(FontWeight.Bold, 36, 1.3)
    val titleS = sans(FontWeight.SemiBold, 32, 1.3)
    val bodyL = sans(FontWeight.Normal, 30, 1.45)
    val bodyM = sans(FontWeight.Normal, 28, 1.45)
    val bodyS = sans(FontWeight.Normal, 26, 1.45)
    val labelM = sans(FontWeight.Medium, 24, 1.35, 0.01)
    val labelS = sans(FontWeight.Medium, 20, 1.35, 0.01)
    val numberXL = number(FontWeight.ExtraBold, 96, 1.0)
    val numberL = number(FontWeight.ExtraBold, 56, 1.05)
    val numberM = number(FontWeight.Bold, 36, 1.1)

    /** Material 컴포넌트가 남아 있어도 같은 글자 체계를 쓰게 한다. */
    val material = Typography(
        displayLarge = headlineL,
        displayMedium = headlineM,
        displaySmall = headlineS,
        headlineLarge = titleL,
        headlineMedium = titleM,
        headlineSmall = titleS,
        titleLarge = bodyL,
        titleMedium = bodyM,
        titleSmall = bodyS,
        bodyLarge = bodyL,
        bodyMedium = bodyM,
        bodySmall = bodyS,
        labelLarge = labelM,
        labelMedium = labelM,
        labelSmall = labelS,
    )
}
