package com.roadmatekorea.android.ui.drive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.roadmatekorea.android.R
import com.roadmatekorea.android.demo.DemoPersona
import com.roadmatekorea.android.ui.components.AppHeader
import com.roadmatekorea.android.ui.components.ReadOnlyOverlay
import com.roadmatekorea.android.ui.components.RmButton
import com.roadmatekorea.android.ui.components.RmButtonStyle
import com.roadmatekorea.android.ui.components.SpeedSign
import com.roadmatekorea.android.ui.theme.RoadMate

/**
 * 주행 화면(Figma 04, 05): 머리(로고, 주행 상태, 설정), 제한 속도, 지금 안내(크게), 마지막 안내(흐리게).
 * 주행 중에는 읽기 전용이고 설정과 시연 버튼을 숨긴다. 설정은 정차(P) 중에만 머리에서 연다.
 */
@Composable
fun DriveScreen(
    state: DriveUiState,
    onChangeSettings: () -> Unit,
    onPlayDemo: (DemoPersona) -> Unit,
    onStopDemo: () -> Unit,
) {
    val c = RoadMate.colors
    val locked = state.vehicle.locked
    Box(Modifier.fillMaxSize().background(c.canvas)) {
        Column(Modifier.fillMaxSize()) {
            // 시연 중에는 설정을 열지 않는다(끝내기 버튼으로 먼저 돌아온다)
            AppHeader(locked = locked, onSettings = if (state.demo == null) onChangeSettings else null)
            Column(
                verticalArrangement = Arrangement.spacedBy(32.dp),
                modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 40.dp, end = 40.dp, top = 8.dp, bottom = 40.dp),
            ) {
                StatusRow(state)
                GuidanceArea(state, Modifier.weight(1f))
                if (!locked) DemoControls(state, onPlayDemo, onStopDemo)
            }
        }
        // 주행 중: 모든 터치를 막는다. 상태는 머리의 "주행 중, 화면 잠김"이 알려 준다.
        if (locked) ReadOnlyOverlay(message = null)
    }
}

/** 제한 속도와 시연·알림 표시. 비어 있으면 자리를 차지하지 않는다. */
@Composable
private fun StatusRow(state: DriveUiState) {
    val notices = buildList {
        state.demo?.let { add(stringResource(R.string.drive_demo_running, stringResource(personaName(it.persona)))) }
        if (state.demo?.finished == true) add(stringResource(R.string.drive_demo_finished))
        if (state.vehicle.showUnavailableBanner) add(stringResource(R.string.drive_vehicle_unavailable))
        if (state.voiceUnavailable) add(stringResource(R.string.drive_voice_unavailable))
    }
    val limit = state.speedLimitKmh
    if (limit == null && notices.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        if (limit != null) SpeedSign(limit, stringResource(R.string.drive_speed_limit_desc, limit))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            notices.forEach { Notice(it) }
        }
    }
}

@Composable
private fun Notice(text: String) {
    val c = RoadMate.colors
    Text(
        text = text,
        style = RoadMate.type.labelM,
        color = c.ink,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(c.sunken)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

/**
 * 말하는 중이면 지금 안내를 노란 띠 카드로 크게 보여 주고, 아니면 마지막 안내를 흐리게 남긴다.
 * 음성을 쓸 수 없을 때도 이 카드가 안내를 대신한다.
 */
@Composable
private fun GuidanceArea(state: DriveUiState, modifier: Modifier) {
    val c = RoadMate.colors
    val caption = state.caption
    val last = state.history.firstOrNull()
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopStart) {
        when {
            state.guidance != null -> GuidanceCard(state.guidance)
            caption != null -> Text(caption, style = RoadMate.type.headlineS, color = c.ink)
            last != null -> Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.surface)
                    .padding(horizontal = 36.dp, vertical = 28.dp),
            ) {
                Text(stringResource(R.string.drive_last_guidance), style = RoadMate.type.labelM, color = c.inkTertiary)
                Text(last, style = RoadMate.type.bodyL, color = c.inkSecondary, maxLines = 3)
            }
            else -> Text(
                text = stringResource(R.string.drive_waiting),
                style = RoadMate.type.titleM,
                color = c.inkTertiary,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp),
            )
        }
    }
}

/** 정차 중에만: 시연 재생(페르소나별) 또는 시연 끝내기. */
@Composable
private fun DemoControls(state: DriveUiState, onPlayDemo: (DemoPersona) -> Unit, onStopDemo: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        if (state.demo == null) {
            DemoPersona.entries.forEach { persona ->
                RmButton(
                    text = stringResource(R.string.drive_demo_play, stringResource(personaName(persona))),
                    onClick = { onPlayDemo(persona) },
                    style = RmButtonStyle.Secondary,
                    icon = R.drawable.ic_rm_play,
                )
            }
        } else {
            RmButton(text = stringResource(R.string.drive_demo_stop), onClick = onStopDemo, style = RmButtonStyle.Secondary)
        }
        Spacer(Modifier.weight(1f))
    }
}

private fun personaName(persona: DemoPersona): Int = when (persona) {
    DemoPersona.NAKAMURA -> R.string.persona_nakamura
    DemoPersona.CARLOS -> R.string.persona_carlos
}
