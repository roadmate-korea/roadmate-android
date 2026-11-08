package com.roadmatekorea.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.roadmatekorea.android.BuildConfig
import com.roadmatekorea.android.profile.ProfileRepository
import com.roadmatekorea.android.profile.model.DriverProfile
import com.roadmatekorea.android.ui.drive.DriveScreen
import com.roadmatekorea.android.ui.onboarding.OnboardingOptions
import com.roadmatekorea.android.ui.onboarding.OnboardingScreen
import com.roadmatekorea.android.ui.onboarding.OnboardingViewModel
import kotlinx.coroutines.flow.map

private sealed interface ProfileLoad {
    data object Loading : ProfileLoad
    data class Loaded(val profile: DriverProfile?) : ProfileLoad
}

/**
 * 저장된 프로필이 없으면 온보딩, 있으면 주행 화면.
 * 주행 화면에서 설정을 다시 하면 저장된 값을 미리 골라 둔 온보딩을 연다.
 */
@Composable
fun RoadMateApp(repository: ProfileRepository) {
    val load by remember(repository) { repository.profile.map { ProfileLoad.Loaded(it) } }
        .collectAsStateWithLifecycle(initialValue = ProfileLoad.Loading)
    var editing by rememberSaveable { mutableStateOf(false) }
    // 온보딩을 열 때마다 새 ViewModel을 쓰도록 키를 바꾼다
    var onboardingSession by rememberSaveable { mutableIntStateOf(0) }

    when (val current = load) {
        ProfileLoad.Loading -> Box(Modifier.fillMaxSize())
        is ProfileLoad.Loaded -> {
            val profile = current.profile
            if (profile == null || editing) {
                val vm: OnboardingViewModel = viewModel(key = "onboarding-$onboardingSession") {
                    OnboardingViewModel(repository, profile)
                }
                val state by vm.state.collectAsStateWithLifecycle()
                OnboardingScreen(
                    state = state,
                    languages = OnboardingOptions.languages(includeKorean = BuildConfig.DEBUG),
                    onLanguage = vm::selectLanguage,
                    onCountry = vm::selectCountry,
                    onExperience = vm::selectExperience,
                    onBack = vm::back,
                    onStart = { vm.start { editing = false } },
                )
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
