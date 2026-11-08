package com.roadmatekorea.android.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roadmatekorea.android.profile.ProfileRepository
import com.roadmatekorea.android.profile.model.AppLanguage
import com.roadmatekorea.android.profile.model.DriverProfile
import com.roadmatekorea.android.profile.model.DrivingCountry
import com.roadmatekorea.android.profile.model.KoreaExperience
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val repository: ProfileRepository,
    existing: DriverProfile?,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState.from(existing))
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    fun selectLanguage(language: AppLanguage) = _state.update { it.selectLanguage(language) }
    fun selectCountry(country: DrivingCountry) = _state.update { it.selectCountry(country) }
    fun selectExperience(experience: KoreaExperience) = _state.update { it.selectExperience(experience) }
    fun back() = _state.update { it.back() }

    /** 세 항목을 모두 골랐을 때만 저장한다. 저장되면 앱이 주행 화면으로 넘어간다. */
    fun start(onSaved: () -> Unit) {
        val profile = _state.value.toProfile() ?: return
        viewModelScope.launch {
            repository.save(profile)
            onSaved()
        }
    }
}
