package com.roadmatekorea.android.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.roadmatekorea.android.R
import com.roadmatekorea.android.i18n.ProfileLabels
import com.roadmatekorea.android.profile.model.AppLanguage
import com.roadmatekorea.android.profile.model.DrivingCountry
import com.roadmatekorea.android.profile.model.KoreaExperience
import com.roadmatekorea.android.ui.LocalCompact
import com.roadmatekorea.android.ui.ProvideLanguage
import com.roadmatekorea.android.ui.components.LogoMark
import com.roadmatekorea.android.ui.components.OptionRow
import com.roadmatekorea.android.ui.components.RmButton
import com.roadmatekorea.android.ui.components.RmIcon
import com.roadmatekorea.android.ui.theme.RoadMate

/**
 * 온보딩 2페이지(Figma 01, 02). 정차 중에만 고를 수 있다.
 * PAGE 1은 언어를 누르면 바로 PAGE 2로 넘어간다(차 안에서 누르는 횟수를 줄인다).
 */
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
    Column(Modifier.fillMaxSize().background(RoadMate.colors.canvas)) {
        when (state.page) {
            OnboardingState.Page.LANGUAGE -> LanguagePage(languages, state.language, enabled = !locked, onLanguage)
            OnboardingState.Page.DRIVING -> ProvideLanguage(state.language ?: AppLanguage.EN) {
                DrivingPage(state, locked, onCountry, onExperience, onBack, onStart)
            }
        }
    }
}

@Composable
private fun LanguagePage(languages: List<AppLanguage>, selected: AppLanguage?, enabled: Boolean, onLanguage: (AppLanguage) -> Unit) {
    if (LocalCompact.current) return CompactLanguagePage(languages, selected, enabled, onLanguage)
    val c = RoadMate.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(120.dp),
        modifier = Modifier.fillMaxSize().padding(start = 96.dp, end = 96.dp, top = 96.dp, bottom = 80.dp),
    ) {
        Column(Modifier.width(620.dp), verticalArrangement = Arrangement.spacedBy(40.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LogoMark()
                Text(
                    stringResource(R.string.app_name_short),
                    style = RoadMate.type.titleL.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em),
                    color = c.ink,
                )
            }
            Spacer(Modifier.height(80.dp))
            Text(stringResource(R.string.onboarding_language_title), style = RoadMate.type.headlineL, color = c.ink)
            Text(stringResource(R.string.onboarding_language_body), style = RoadMate.type.bodyL, color = c.inkSecondary)
        }
        Column(Modifier.weight(1f).padding(top = 176.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            languages.forEach { language ->
                OptionRow(
                    title = stringResource(ProfileLabels.language(language)),
                    selected = language == selected,
                    onClick = { onLanguage(language) },
                    enabled = enabled,
                    minHeight = 112.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** 좁은 창(내비와 나란히): 로고, 제목, 언어 목록을 위에서 아래로. */
@Composable
private fun CompactLanguagePage(languages: List<AppLanguage>, selected: AppLanguage?, enabled: Boolean, onLanguage: (AppLanguage) -> Unit) {
    val c = RoadMate.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState(), enabled = enabled).padding(horizontal = 40.dp, vertical = 48.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LogoMark()
            Text(
                stringResource(R.string.app_name_short),
                style = RoadMate.type.titleL.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.02).em),
                color = c.ink,
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.onboarding_language_title), style = RoadMate.type.headlineS, color = c.ink)
        Text(stringResource(R.string.onboarding_language_body), style = RoadMate.type.bodyM, color = c.inkSecondary)
        Spacer(Modifier.height(8.dp))
        languages.forEach { language ->
            OptionRow(
                title = stringResource(ProfileLabels.language(language)),
                selected = language == selected,
                onClick = { onLanguage(language) },
                enabled = enabled,
                minHeight = 104.dp,
                modifier = Modifier.fillMaxWidth(),
            )
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
    val c = RoadMate.colors
    val editable = state.editable && !locked
    if (LocalCompact.current) return CompactDrivingPage(state, editable, locked, onCountry, onExperience, onBack, onStart)
    Column(Modifier.fillMaxSize().padding(start = 96.dp, end = 96.dp, top = 40.dp, bottom = 72.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .heightIn(min = 72.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = editable, role = Role.Button, onClick = onBack)
                .padding(end = 16.dp),
        ) {
            RmIcon(R.drawable.ic_rm_arrow_left, size = 44.dp, tint = if (editable) c.ink else c.inkTertiary)
            Text(stringResource(R.string.onboarding_back), style = RoadMate.type.labelM, color = c.inkTertiary)
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(80.dp), modifier = Modifier.weight(1f)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Text(stringResource(R.string.onboarding_country_question), style = RoadMate.type.titleL, color = c.ink)
                OnboardingOptions.countries.chunked(COUNTRIES_PER_ROW).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { country ->
                            OptionRow(
                                title = stringResource(ProfileLabels.country(country)),
                                selected = country == state.country,
                                onClick = { onCountry(country) },
                                enabled = editable,
                                minHeight = 88.dp,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // 마지막 줄의 빈칸을 채워 크기를 맞춘다
                        repeat(COUNTRIES_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            Column(Modifier.width(640.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Text(stringResource(R.string.onboarding_experience_question), style = RoadMate.type.titleL, color = c.ink)
                OnboardingOptions.experiences.forEach { experience ->
                    OptionRow(
                        title = stringResource(ProfileLabels.experience(experience)),
                        description = stringResource(ProfileLabels.experienceDescription(experience)),
                        selected = experience == state.experience,
                        onClick = { onExperience(experience) },
                        enabled = editable,
                        minHeight = 136.dp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (state.saveFailed) {
                    Text(stringResource(R.string.onboarding_save_failed), style = RoadMate.type.bodyM, color = c.danger)
                }
                RmButton(
                    text = stringResource(R.string.onboarding_start),
                    onClick = onStart,
                    enabled = state.canStart && !locked,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * 좁은 창(내비와 나란히): 뒤로, 국가(2열), 경험, 시작 버튼을 한 열로 쌓고 스크롤한다.
 * 정차 중에만 조작할 수 있으므로 스크롤도 정차 중에만 받는다. 시작 버튼은 아래에 고정한다.
 */
@Composable
private fun CompactDrivingPage(
    state: OnboardingState,
    editable: Boolean,
    locked: Boolean,
    onCountry: (DrivingCountry) -> Unit,
    onExperience: (KoreaExperience) -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit,
) {
    val c = RoadMate.colors
    Column(Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = 24.dp, bottom = 32.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .heightIn(min = 72.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = editable, role = Role.Button, onClick = onBack)
                .padding(end = 16.dp),
        ) {
            RmIcon(R.drawable.ic_rm_arrow_left, size = 44.dp, tint = if (editable) c.ink else c.inkTertiary)
            Text(stringResource(R.string.onboarding_back), style = RoadMate.type.labelM, color = c.inkTertiary)
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState(), enabled = !locked).padding(top = 16.dp, bottom = 24.dp),
        ) {
            Text(stringResource(R.string.onboarding_country_question), style = RoadMate.type.titleM, color = c.ink)
            OnboardingOptions.countries.chunked(COUNTRIES_PER_ROW).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { country ->
                        OptionRow(
                            title = stringResource(ProfileLabels.country(country)),
                            selected = country == state.country,
                            onClick = { onCountry(country) },
                            enabled = editable,
                            minHeight = 88.dp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(COUNTRIES_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.onboarding_experience_question), style = RoadMate.type.titleM, color = c.ink)
            OnboardingOptions.experiences.forEach { experience ->
                OptionRow(
                    title = stringResource(ProfileLabels.experience(experience)),
                    description = stringResource(ProfileLabels.experienceDescription(experience)),
                    selected = experience == state.experience,
                    onClick = { onExperience(experience) },
                    enabled = editable,
                    minHeight = 120.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (state.saveFailed) {
            Text(stringResource(R.string.onboarding_save_failed), style = RoadMate.type.bodyM, color = c.danger)
        }
        RmButton(
            text = stringResource(R.string.onboarding_start),
            onClick = onStart,
            enabled = state.canStart && !locked,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private const val COUNTRIES_PER_ROW = 2
