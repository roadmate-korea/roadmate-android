package com.roadmatekorea.android.ui.drive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.roadmatekorea.android.R
import com.roadmatekorea.android.i18n.ProfileLabels
import com.roadmatekorea.android.profile.model.DriverProfile

/**
 * 주행 화면 자리. 안내 엔진, 음성, 자막 연결과 주행 중 조작 잠금은 주행 화면 PR에서 붙인다.
 */
@Composable
fun DriveScreen(profile: DriverProfile, onChangeSettings: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(64.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.drive_ready), style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(32.dp))
        Text(
            text = listOf(
                stringResource(ProfileLabels.language(profile.language)),
                stringResource(ProfileLabels.country(profile.country)),
                stringResource(ProfileLabels.experience(profile.experience)),
            ).joinToString("  ·  "),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(48.dp))
        OutlinedButton(onClick = onChangeSettings, modifier = Modifier.height(96.dp)) {
            Text(stringResource(R.string.drive_change_settings), style = MaterialTheme.typography.headlineSmall)
        }
    }
}
