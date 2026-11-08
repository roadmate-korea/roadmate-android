package com.roadmatekorea.android.ui.onboarding

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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.roadmatekorea.android.R
import com.roadmatekorea.android.i18n.ProfileLabels
import com.roadmatekorea.android.profile.model.AppLanguage
import com.roadmatekorea.android.profile.model.DrivingCountry
import com.roadmatekorea.android.profile.model.KoreaExperience
import com.roadmatekorea.android.ui.ProvideLanguage
import com.roadmatekorea.android.ui.components.SelectableOption

/** 온보딩 2페이지. 정차 중에만 열린다(주행 중 잠금은 주행 화면 PR에서 연결). */
@Composable
fun OnboardingScreen(
    state: OnboardingState,
    languages: List<AppLanguage>,
    locked: Boolean = false,
    onLanguage: (AppLanguage) -> Unit,
    onCountry: (DrivingCountry) -> Unit,
    onExperience: (KoreaExperience) -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit,
) {
    when (state.page) {
        OnboardingState.Page.LANGUAGE -> LanguagePage(languages, state.language, enabled = !locked, onLanguage)
        OnboardingState.Page.DRIVING -> ProvideLanguage(state.language ?: AppLanguage.EN) {
            DrivingPage(state, locked, onCountry, onExperience, onBack, onStart)
        }
    }
}

@Composable
private fun LanguagePage(languages: List<AppLanguage>, selected: AppLanguage?, enabled: Boolean, onLanguage: (AppLanguage) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(64.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.onboarding_language_title), style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(64.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            languages.forEach { language ->
                SelectableOption(
                    text = stringResource(ProfileLabels.language(language)),
                    selected = language == selected,
                    onClick = { onLanguage(language) },
                    enabled = enabled,
                    modifier = Modifier.width(360.dp),
                    minHeight = 200.dp,
                )
            }
        }
    }
}

@Composable
private fun DrivingPage(
    state: OnboardingState,
    locked: Boolean,
    onCountry: (DrivingCountry) -> Unit,
    onExperience: (KoreaExperience) -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit,
) {
    val editable = state.editable && !locked
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 40.dp)) {
        TextButton(onClick = onBack, enabled = editable, modifier = Modifier.heightIn(min = 72.dp)) {
            Text(stringResource(R.string.onboarding_back), style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(16.dp))

        Text(stringResource(R.string.onboarding_country_question), style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(24.dp))
        OnboardingOptions.countries.chunked(COUNTRIES_PER_ROW).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { country ->
                    SelectableOption(
                        text = stringResource(ProfileLabels.country(country)),
                        selected = country == state.country,
                        onClick = { onCountry(country) },
                        enabled = editable,
                        modifier = Modifier.weight(1f),
                    )
                }
                // 마지막 줄의 빈칸을 채워 버튼 크기를 맞춘다
                repeat(COUNTRIES_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(20.dp))
        }

        Spacer(Modifier.height(32.dp))
        Text(stringResource(R.string.onboarding_experience_question), style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth()) {
            OnboardingOptions.experiences.forEach { experience ->
                SelectableOption(
                    text = stringResource(ProfileLabels.experience(experience)),
                    selected = experience == state.experience,
                    onClick = { onExperience(experience) },
                    enabled = editable,
                    modifier = Modifier.weight(1f),
                    minHeight = 140.dp,
                )
            }
        }

        Spacer(Modifier.weight(1f))
        if (state.saveFailed) {
            Text(
                text = stringResource(R.string.onboarding_save_failed),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.End).padding(bottom = 16.dp),
            )
        }
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Button(onClick = onStart, enabled = state.canStart && !locked, modifier = Modifier.width(400.dp).height(112.dp)) {
                Text(stringResource(R.string.onboarding_start), style = MaterialTheme.typography.headlineMedium)
            }
        }
    }
}

private const val COUNTRIES_PER_ROW = 6
