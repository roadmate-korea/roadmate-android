package com.roadmatekorea.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.roadmatekorea.android.BuildConfig
import com.roadmatekorea.android.R
import com.roadmatekorea.android.profile.ProfileRead
import com.roadmatekorea.android.profile.ProfileStore
import com.roadmatekorea.android.profile.model.DriverProfile
import com.roadmatekorea.android.ui.drive.DriveScreen
import com.roadmatekorea.android.ui.onboarding.OnboardingOptions
import com.roadmatekorea.android.ui.onboarding.OnboardingScreen
import com.roadmatekorea.android.ui.onboarding.OnboardingViewModel

/**
 * 저장된 프로필이 없으면 온보딩, 있으면 주행 화면.
 * 주행 화면에서 설정을 다시 하면 저장된 값을 미리 골라 둔 온보딩을 연다.
 * 저장소를 읽지 못하면 안내 문구를 보여 주고, 저장소가 다시 읽히면 이어서 진행한다.
 */
@Composable
fun RoadMateApp(store: ProfileStore) {
    val read by remember(store) { store.profile }.collectAsStateWithLifecycle(initialValue = null)
    var editing by rememberSaveable { mutableStateOf(false) }
    // 온보딩을 열 때마다 새 ViewModel을 쓰도록 키를 바꾼다
    var onboardingSession by rememberSaveable { mutableIntStateOf(0) }

    when (val current = read) {
        null -> Box(Modifier.fillMaxSize())
        ProfileRead.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.settings_read_error), style = MaterialTheme.typography.headlineMedium)
        }
        is ProfileRead.Loaded -> {
            val profile = current.profile
            if (profile == null || editing) {
                Onboarding(store, profile, onboardingSession, onDone = { editing = false })
            } else {
                ProvideLanguage(profile.language) {
                    DriveScreen(
                        profile = profile,
                        onChangeSettings = {
                            onboardingSession++
                            editing = true
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Onboarding(store: ProfileStore, existing: DriverProfile?, session: Int, onDone: () -> Unit) {
    val vm: OnboardingViewModel = viewModel(key = "onboarding-$session") { OnboardingViewModel(store, existing) }
    val state by vm.state.collectAsStateWithLifecycle()
    OnboardingScreen(
        state = state,
        languages = OnboardingOptions.languages(includeKorean = BuildConfig.DEBUG),
        onLanguage = vm::selectLanguage,
        onCountry = vm::selectCountry,
        onExperience = vm::selectExperience,
        onBack = vm::back,
        onStart = { vm.start { onDone() } },
    )
}
