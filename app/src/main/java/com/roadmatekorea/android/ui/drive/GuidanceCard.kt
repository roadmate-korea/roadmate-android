package com.roadmatekorea.android.ui.drive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.roadmatekorea.android.rules.Guidance
import com.roadmatekorea.android.ui.components.RmIcon
import com.roadmatekorea.android.ui.scene.Scene
import com.roadmatekorea.android.ui.theme.RoadMate

/**
 * 주행 중 안내 카드(Figma 05): 왼쪽 띠(일반 노랑, 안전 경고 빨강), 상황 그림, 머리글, 한 줄 지시, 한 줄 설명.
 * 문구는 지금 화면 언어(프로필 언어)의 리소스에서 꺼낸다. 음성을 쓸 수 없으면 설명 줄에 [fullText](전체 문장)를 보여 준다.
 */
@Composable
fun GuidanceCard(guidance: Guidance, fullText: String?, voiced: Boolean, modifier: Modifier = Modifier) {
    val c = RoadMate.colors
    val content = GuidanceCards.of(guidance.id)
    val res = LocalContext.current.resources
    val args = guidance.args.toTypedArray()
    val accent = if (content.safety) c.danger else c.centerline
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .heightIn(min = 360.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (content.safety) c.dangerSurface else c.surface),
    ) {
        Box(Modifier.width(14.dp).fillMaxHeight().background(accent))
        if (content.scene != null) {
            Scene(
                content.scene,
                Modifier.padding(start = 40.dp, top = 40.dp, bottom = 40.dp).height(540.dp),
                speedLimitKmh = content.sceneSpeedArg?.let { guidance.args.getOrNull(it) },
            )
        } else {
            Box(Modifier.padding(start = 56.dp).align(Alignment.CenterVertically)) {
                RmIcon(content.icon, size = 120.dp, tint = if (content.safety) c.danger else c.ink)
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 48.dp, end = 56.dp, top = 40.dp, bottom = 40.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RmIcon(content.icon, size = 44.dp, tint = if (content.safety) c.danger else c.inkSecondary)
                Text(res.getString(content.kicker, *args), style = RoadMate.type.titleS, color = if (content.safety) c.danger else c.inkSecondary)
            }
            Text(res.getString(content.headline, *args), style = RoadMate.type.headlineM, color = c.ink)
            Text(
                GuidanceCards.bodyText(voiced, { res.getString(content.body, *args) }, fullText),
                style = RoadMate.type.bodyL,
                color = c.inkSecondary,
            )
        }
    }
}
