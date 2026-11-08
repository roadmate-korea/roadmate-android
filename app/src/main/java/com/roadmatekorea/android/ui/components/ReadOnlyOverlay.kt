package com.roadmatekorea.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.roadmatekorea.android.R
import com.roadmatekorea.android.ui.theme.RoadMate

/**
 * 주행 중(기어가 P가 아님) 화면을 읽기 전용으로 만든다.
 * 맨 위에 덮어 모든 터치(버튼, 스크롤)를 먼저 가로채 버린다. 화면 내용은 그대로 보인다.
 * [message]가 있으면 오른쪽 아래에 자물쇠와 함께 보여 준다(머리에 주행 상태가 없는 화면용).
 */
@Composable
fun ReadOnlyOverlay(message: String?) {
    val c = RoadMate.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.BottomEnd,
    ) {
        if (message != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .padding(40.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(c.ink)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                RmIcon(R.drawable.ic_rm_lock, tint = c.onInk)
                Text(message, style = RoadMate.type.labelM, color = c.onInk)
            }
        }
    }
}
