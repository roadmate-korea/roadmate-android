package com.roadmatekorea.android.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roadmatekorea.android.profile.ProfileStore
import com.roadmatekorea.android.profile.model.AppLanguage
import com.roadmatekorea.android.profile.model.DriverProfile
import com.roadmatekorea.android.profile.model.DrivingCountry
import com.roadmatekorea.android.profile.model.KoreaExperience
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

class OnboardingViewModel(
    private val store: ProfileStore,
    existing: DriverProfile?,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState.from(existing))
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    fun selectLanguage(language: AppLanguage) = _state.update { it.selectLanguage(language) }
    fun selectCountry(country: DrivingCountry) = _state.update { it.selectCountry(country) }
    fun selectExperience(experience: KoreaExperience) = _state.update { it.selectExperience(experience) }
    fun back() = _state.update { it.back() }

    /**
     * 세 항목을 모두 골랐을 때만 저장한다. 저장이 끝나면 상태의 [OnboardingState.saved]가 true가 된다.
     * 완료를 콜백이 아니라 상태로 두어, 저장 중 화면이 다시 만들어져도 새 화면이 완료를 받는다.
     * 저장에 실패하면 고른 값은 그대로 두고 오류를 보여 주며, START로 다시 시도할 수 있다.
     */
    fun start() {
        val current = _state.value
        if (current.saving || current.saved) return
        val profile = current.toProfile() ?: return
        _state.update { it.copy(saving = true, saveFailed = false) }
        viewModelScope.launch {
            val saved = try {
                store.save(profile)
                true
            } catch (e: IOException) {
                false
            }
            _state.update { it.copy(saving = false, saveFailed = !saved, saved = saved) }
        }
    }
}
