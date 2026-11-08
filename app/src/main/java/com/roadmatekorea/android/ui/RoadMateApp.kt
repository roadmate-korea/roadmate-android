package com.roadmatekorea.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.roadmatekorea.android.BuildConfig
import com.roadmatekorea.android.R
import com.roadmatekorea.android.RoadMateApplication
import com.roadmatekorea.android.profile.ProfileRead
import com.roadmatekorea.android.profile.ProfileStore
import com.roadmatekorea.android.profile.model.AppLanguage
import com.roadmatekorea.android.profile.model.DriverProfile
import com.roadmatekorea.android.ui.components.GuidanceCaptionBar
import com.roadmatekorea.android.ui.components.ReadOnlyOverlay
import com.roadmatekorea.android.ui.drive.DriveScreen
import com.roadmatekorea.android.ui.onboarding.OnboardingOptions
import com.roadmatekorea.android.ui.onboarding.OnboardingScreen
import com.roadmatekorea.android.ui.onboarding.OnboardingState
import com.roadmatekorea.android.ui.onboarding.OnboardingViewModel
import com.roadmatekorea.android.ui.theme.RoadMate

/**
 * 저장된 프로필이 없으면 온보딩, 있으면 주행 화면.
 * 주행 화면에서 설정을 다시 하면 저장된 값을 미리 골라 둔 온보딩을 연다.
 * 저장소를 읽지 못하면 안내 문구를 보여 주고, 저장소가 다시 읽히면 이어서 진행한다.
 *
 * 실제 기어가 P가 아니면 온보딩과 주행 화면 모두 읽기 전용이다(설정 변경은 정차 중에만).
 */
@Composable
fun RoadMateApp(store: ProfileStore) {
    val app = LocalContext.current.applicationContext as RoadMateApplication
    // 안내 세션은 화면이 아니라 앱 범위에 있다(내비를 앞에 띄워도 계속)
    val driveVm = app.driveController
    val vehicle by driveVm.vehicle.collectAsStateWithLifecycle()

    val read by remember(store) { store.profile }.collectAsStateWithLifecycle(initialValue = null)
    var editing by rememberSaveable { mutableStateOf(false) }
    // 설정을 다시 할 때마다 늘려서 온보딩 편집 세션을 구분한다
    var onboardingSession by rememberSaveable { mutableIntStateOf(0) }
    // START 직후 주행 화면에 들어가면 바로 시작 안내를 낸다
    var announceOnEnter by rememberSaveable { mutableStateOf(false) }

    when (val current = read) {
        null -> Box(Modifier.fillMaxSize().background(RoadMate.colors.canvas))
        ProfileRead.Error -> Box(Modifier.fillMaxSize().background(RoadMate.colors.canvas), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.settings_read_error), style = RoadMate.type.titleM, color = RoadMate.colors.inkSecondary)
        }
        is ProfileRead.Loaded -> {
            val profile = current.profile
            // 저장된 프로필이 있으면 화면(설정 중이든, 프로세스가 설정 화면으로 복원됐든)과 상관없이 안내 세션을 둔다
            if (profile != null) LaunchedEffect(profile) { driveVm.ensureSession(profile) }
            val driveState by driveVm.uiState.collectAsStateWithLifecycle()
            if (profile == null || editing) {
                Box(Modifier.fillMaxSize()) {
                    Onboarding(
                        store = store,
                        existing = profile,
                        session = onboardingSession,
                        locked = vehicle.locked,
                        // 입력이 실제로 실행되는 순간의 기어로 다시 확인한다(누른 채 D로 바꾸거나 키보드·접근성 입력)
                        isLockedNow = { driveVm.vehicle.value.locked },
                        // 저장이 끝나는 순간 프로필이 먼저 바뀌어 주행 화면으로 넘어갈 수 있으므로 START를 누를 때 표시한다
                        onStartPressed = { announceOnEnter = true },
                        onDone = { editing = false },
                    )
                    // 설정 화면에서도 지금 안내 자막을 보여 준다(음성을 쓸 수 없을 때 유일한 출력). 저장된 프로필 언어로.
                    val caption = driveState.caption
                    val sessionProfile = driveState.profile
                    if (caption != null && sessionProfile != null) {
                        ProvideLanguage(sessionProfile.language) { GuidanceCaptionBar(caption) }
                    }
                }
            } else {
                LaunchedEffect(profile) {
                    driveVm.enterDrive(profile, announceNow = announceOnEnter)
                    announceOnEnter = false
                }
                // 시연 중에는 시연 페르소나의 언어로 화면을 보여 준다
                ProvideLanguage(driveState.profile?.language ?: profile.language) {
                    DriveScreen(
                        state = driveState,
                        onChangeSettings = {
                            // 설정 화면에 가도 주행 세션은 유지한다(편집 중 출발해도 안전 경고가 계속 나가야 함)
                            if (!driveVm.vehicle.value.locked) {
                                onboardingSession++
                                editing = true
                            }
                        },
                        onPlayDemo = driveVm::startDemo,
                        onStopDemo = { driveVm.stopDemo(profile) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Onboarding(
    store: ProfileStore,
    existing: DriverProfile?,
    session: Int,
    locked: Boolean,
    isLockedNow: () -> Boolean,
    onStartPressed: () -> Unit,
    onDone: () -> Unit,
) {
    // ViewModel은 하나만 두고 세션이 바뀔 때만 새 편집을 시작한다(구성 변경 때는 상태 유지)
    val vm: OnboardingViewModel = viewModel { OnboardingViewModel(store) }
    remember(session) { vm.beginSession(session, existing) }
    val state by vm.state.collectAsStateWithLifecycle()
    // 저장 완료는 ViewModel 상태로 받는다. 저장 중 Activity가 다시 만들어져도 새 화면이 이 값을 본다.
    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    // 주행 중이면 화면을 비활성화하고, 동작마다 실행 직전에 다시 확인한다
    fun guarded(action: () -> Unit) {
        if (!isLockedNow()) action()
    }
    Box(Modifier.fillMaxSize()) {
    OnboardingScreen(
        state = state,
        locked = locked,
        languages = OnboardingOptions.languages(includeKorean = BuildConfig.DEBUG),
        onLanguage = { guarded { vm.selectLanguage(it) } },
        onCountry = { guarded { vm.selectCountry(it) } },
        onExperience = { guarded { vm.selectExperience(it) } },
        onBack = { guarded { vm.back() } },
        onStart = {
            guarded {
                onStartPressed()
                vm.start()
            }
        },
    )
    if (locked) {
        // 잠금 안내는 지금 화면과 같은 언어로: PAGE 2는 고른 언어, PAGE 1은 저장된 언어(없으면 영어)
        val language = if (state.page == OnboardingState.Page.DRIVING) state.language else existing?.language
        ProvideLanguage(language ?: AppLanguage.EN) {
            ReadOnlyOverlay(stringResource(R.string.drive_parked_only))
        }
    }
    }
}
