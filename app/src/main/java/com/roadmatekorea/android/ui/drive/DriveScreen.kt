package com.roadmatekorea.android.ui.drive

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.roadmatekorea.android.R
import com.roadmatekorea.android.i18n.ProfileLabels
import com.roadmatekorea.android.profile.model.DriverProfile
import com.roadmatekorea.android.ui.components.ReadOnlyOverlay

/**
 * 주행 화면: 지금 안내(크게), 최근 안내, 제한 속도, 프로필 요약.
 * 주행 중에는 읽기 전용이고, 설정 다시 하기는 정차(P) 중에만 누를 수 있다.
 */
@Composable
fun DriveScreen(
    profile: DriverProfile,
    state: DriveUiState,
    onChangeSettings: () -> Unit,
) {
    val locked = state.vehicle.locked
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = listOf(
                        stringResource(ProfileLabels.language(profile.language)),
                        stringResource(ProfileLabels.country(profile.country)),
                        stringResource(ProfileLabels.experience(profile.experience)),
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                state.speedLimitKmh?.let { SpeedLimitSign(it) }
            }

            Spacer(Modifier.height(24.dp))
            Banners(state)

            Surface(
                shape = RoundedCornerShape(28.dp),
                color = if (state.caption != null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().heightIn(min = 280.dp),
            ) {
                Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.padding(40.dp)) {
                    Text(
                        text = state.caption ?: stringResource(R.string.drive_waiting),
                        style = if (state.caption != null) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineSmall,
                        color = if (state.caption != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
            if (state.history.isNotEmpty()) {
                Text(stringResource(R.string.drive_recent_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                // 지금 말하는 문장은 위 카드에 있으므로 목록에서는 뺀다
                state.history.filter { it != state.caption }.take(3).forEach {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
            }

            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = onChangeSettings,
                    enabled = !locked,
                    modifier = Modifier.height(88.dp),
                ) {
                    Text(stringResource(R.string.drive_change_settings), style = MaterialTheme.typography.titleLarge)
                }
                if (locked) {
                    Text(
                        text = stringResource(R.string.drive_parked_only),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 24.dp),
                    )
                }
            }
        }
        if (locked) ReadOnlyOverlay()
    }
}

@Composable
private fun Banners(state: DriveUiState) {
    val messages = buildList {
        if (state.vehicle.showUnavailableBanner) add(stringResource(R.string.drive_vehicle_unavailable))
        if (state.voiceUnavailable) add(stringResource(R.string.drive_voice_unavailable))
    }
    if (messages.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 16.dp)) {
        messages.forEach {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** 한국 제한 속도 표지 모양(흰 원, 빨간 테두리). 값은 NaviHelper가 준 것. */
@Composable
private fun SpeedLimitSign(kmh: Int) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(112.dp)
            .background(Color.White, CircleShape)
            .border(BorderStroke(12.dp, Color(0xFFD32F2F)), CircleShape),
    ) {
        Text(
            text = stringResource(R.string.drive_speed_limit_value, kmh),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
        )
    }
}
