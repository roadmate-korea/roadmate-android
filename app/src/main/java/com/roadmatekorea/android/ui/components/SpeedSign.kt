package com.roadmatekorea.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadmatekorea.android.ui.theme.RoadMate

/**
 * 한국 제한 속도 표지판(흰 원, 빨간 테두리, 먹색 숫자). 값은 NaviHelper가 준 제한 속도를 그대로 쓴다.
 * 두 모드에서 같은 모양이라 바탕은 흰 차선 색, 숫자는 노랑 위 글자 색(두 모드 모두 먹색)을 쓴다.
 */
@Composable
fun SpeedSign(kmh: Int, description: String, modifier: Modifier = Modifier, size: Dp = 160.dp) {
    val c = RoadMate.colors
    val scale = size.value / 160f
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .semantics { contentDescription = description }
            .background(c.paint, CircleShape)
            .border((18 * scale).dp, c.danger, CircleShape),
    ) {
        Text(
            text = kmh.toString(),
            style = RoadMate.type.numberL.copy(fontSize = ((if (kmh >= 100) 50 else 56) * scale).sp),
            color = c.onCenterline,
        )
    }
}
