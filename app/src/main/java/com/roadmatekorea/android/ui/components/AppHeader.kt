package com.roadmatekorea.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.roadmatekorea.android.R
import com.roadmatekorea.android.ui.theme.RoadMate

/**
 * 로고 마크(시안 A "중앙선 도로"): 아스팔트 위 노란 겹중앙선이 왼쪽, 내 차선 화살표가 오른쪽.
 * 앱 아이콘(ic_launcher)과 같은 그림.
 */
@Composable
fun LogoMark(size: Dp = 56.dp, modifier: Modifier = Modifier) {
    val c = RoadMate.colors
    Canvas(modifier.size(size).clip(RoundedCornerShape(size * 22f / 96f)).background(c.asphalt)) {
        val u = this.size.width / 96f
        drawRect(c.centerline, Offset(30 * u, 0f), Size(6 * u, this.size.height))
        drawRect(c.centerline, Offset(40 * u, 0f), Size(6 * u, this.size.height))
        val stroke = Stroke(width = 8 * u, join = StrokeJoin.Miter)
        drawLine(c.paint, Offset(68 * u, 74 * u), Offset(68 * u, 30 * u), strokeWidth = 8 * u)
        drawPath(
            Path().apply { moveTo(55 * u, 42 * u); lineTo(68 * u, 24 * u); lineTo(81 * u, 42 * u) },
            c.paint,
            style = stroke,
        )
    }
}

/** 주행 상태(Figma `DriveState`). 실제 기어를 따른다. */
@Composable
fun DriveStateChip(locked: Boolean, modifier: Modifier = Modifier) {
    val c = RoadMate.colors
    val bg = if (locked) c.ink else c.sunken
    val fg = if (locked) c.onInk else c.ink
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(bg)
            .padding(start = 20.dp, end = 24.dp),
    ) {
        if (locked) RmIcon(R.drawable.ic_rm_lock, tint = fg)
        else Text("P", style = RoadMate.type.numberM, color = fg)
        Text(
            stringResource(if (locked) R.string.drive_state_locked else R.string.drive_state_parked),
            style = RoadMate.type.labelM,
            color = fg,
        )
    }
}

/**
 * 앱 머리(Figma `AppHeader`): 로고, 주행 상태, 설정. 주행 중에는 설정을 숨긴다.
 * [onSettings]가 null이면 설정 버튼을 그리지 않는다.
 */
@Composable
fun AppHeader(locked: Boolean, onSettings: (() -> Unit)?, modifier: Modifier = Modifier) {
    val c = RoadMate.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        modifier = modifier.fillMaxWidth().height(112.dp).padding(start = 56.dp, end = 40.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LogoMark()
            Text(
                stringResource(R.string.app_name_short),
                style = RoadMate.type.titleL.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em, fontSize = 40.sp),
                color = c.ink,
            )
        }
        Spacer(Modifier.weight(1f))
        DriveStateChip(locked)
        if (!locked && onSettings != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .heightIn(min = 64.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(c.surface)
                    .clickable(role = Role.Button, onClick = onSettings)
                    .padding(start = 20.dp, end = 24.dp),
            ) {
                RmIcon(R.drawable.ic_rm_settings, size = 36.dp)
                Text(stringResource(R.string.header_settings), style = RoadMate.type.labelM, color = c.ink)
            }
        }
    }
}
