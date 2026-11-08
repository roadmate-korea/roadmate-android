package com.roadmatekorea.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.roadmatekorea.android.ui.theme.RoadMate

/**
 * 주행 화면이 아닐 때(설정 화면) 지금 안내를 화면 위쪽에 띄운다.
 * 음성을 쓸 수 없을 때 안전 경고의 유일한 출력이 되므로 설정 화면에서도 보여 준다.
 * 그림자 대신 먹색 테두리로 아래 화면과 나눈다.
 */
@Composable
fun GuidanceCaptionBar(text: String) {
    val c = RoadMate.colors
    val shape = RoundedCornerShape(16.dp)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier
                .padding(horizontal = 48.dp, vertical = 16.dp)
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clip(shape)
                .background(c.surface)
                .border(2.dp, c.ink, shape),
        ) {
            Box(Modifier.width(14.dp).fillMaxHeight().background(c.centerline))
            Text(
                text = text,
                style = RoadMate.type.titleS,
                color = c.ink,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
            )
        }
    }
}
