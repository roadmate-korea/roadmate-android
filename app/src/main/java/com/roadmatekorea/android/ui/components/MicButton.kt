package com.roadmatekorea.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.roadmatekorea.android.R
import com.roadmatekorea.android.ui.theme.RoadMate

/** 마이크 버튼의 높이(Figma `MicButton`). 화면은 이만큼 자리를 비워 둔다. */
val MIC_BUTTON_HEIGHT = 104.dp

/**
 * 음성 질문 버튼(Figma `MicButton`): 대기는 진한 바탕, 듣는 중은 노란 바탕.
 * 주행 중에도 눌리는 유일한 버튼이다. 그래서 크고 한 번에 누르기 쉽게 둔다.
 */
@Composable
fun MicButton(listening: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, iconOnly: Boolean = false) {
    val c = RoadMate.colors
    val (container, content) = if (listening) c.centerline to c.onCenterline else c.ink to c.onInk
    val description = stringResource(R.string.drive_mic_desc)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
            .height(MIC_BUTTON_HEIGHT)
            .clip(RoundedCornerShape(MIC_BUTTON_HEIGHT / 2))
            .background(container)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(start = if (iconOnly) 30.dp else 36.dp, end = if (iconOnly) 30.dp else 44.dp),
    ) {
        RmIcon(R.drawable.ic_rm_mic, size = 44.dp, tint = content)
        // 좁은 창에서 다른 버튼과 한 줄에 둘 때는 아이콘만(이름은 접근성 설명으로)
        if (!iconOnly) {
            Text(
                stringResource(if (listening) R.string.drive_mic_listening else R.string.drive_mic_ask),
                style = RoadMate.type.titleS,
                color = content,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
