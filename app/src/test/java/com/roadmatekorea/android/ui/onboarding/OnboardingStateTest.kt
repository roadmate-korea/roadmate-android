package com.roadmatekorea.android.ui.onboarding

import com.roadmatekorea.android.profile.model.AppLanguage
import com.roadmatekorea.android.profile.model.DrivingCountry
import com.roadmatekorea.android.profile.model.KoreaExperience
import com.roadmatekorea.android.profile.model.Personas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingStateTest {

    @Test
    fun startsOnLanguagePage_withNothingSelected() {
        val s = OnboardingState()
        assertEquals(OnboardingState.Page.LANGUAGE, s.page)
        assertFalse(s.canStart)
        assertNull(s.toProfile())
    }

    @Test
    fun choosingLanguage_movesToDrivingPage() {
        val s = OnboardingState().selectLanguage(AppLanguage.ES)
        assertEquals(OnboardingState.Page.DRIVING, s.page)
        assertEquals(AppLanguage.ES, s.language)
    }

    @Test
    fun start_enabledOnlyWhenAllThreeChosen() {
        var s = OnboardingState().selectLanguage(AppLanguage.EN)
        assertFalse(s.canStart)
        s = s.selectCountry(DrivingCountry.JP)
        assertFalse(s.canStart)
        s = s.selectExperience(KoreaExperience.FIRST_TIME_LEFT_HAND_DRIVE)
        assertTrue(s.canStart)
        assertEquals(Personas.NAKAMURA, s.toProfile())
    }

    @Test
    fun back_keepsChoices() {
        val s = OnboardingState().selectLanguage(AppLanguage.ES).selectCountry(DrivingCountry.OTHER).back()
        assertEquals(OnboardingState.Page.LANGUAGE, s.page)
        assertEquals(DrivingCountry.OTHER, s.country)
    }

    @Test
    fun changingLanguage_keepsCountryAndExperience() {
        val s = OnboardingState()
            .selectLanguage(AppLanguage.EN)
            .selectCountry(DrivingCountry.OTHER)
            .selectExperience(KoreaExperience.FIRST_TIME_IN_KOREA)
            .back()
            .selectLanguage(AppLanguage.ES)
        assertEquals(Personas.CARLOS, s.toProfile())
    }

    @Test
    fun resettingSettings_prefillsSavedProfile_andStartsOnPage1() {
        val s = OnboardingState.from(Personas.CARLOS)
        assertEquals(OnboardingState.Page.LANGUAGE, s.page)
        assertEquals(Personas.CARLOS, s.toProfile())
    }

    @Test
    fun languages_threeForUsers_koreanOnlyInDebug() {
        assertEquals(listOf(AppLanguage.EN, AppLanguage.FR, AppLanguage.ES), OnboardingOptions.languages(includeKorean = false))
        assertEquals(
            listOf(AppLanguage.EN, AppLanguage.FR, AppLanguage.ES, AppLanguage.KO),
            OnboardingOptions.languages(includeKorean = true),
        )
    }

    @Test
    fun countries_fixedElevenInScenarioOrder() {
        assertEquals(
            listOf("CN", "JP", "TW", "US", "HK", "PH", "VN", "TH", "SG", "MY", "OTHER"),
            OnboardingOptions.countries.map { it.name },
        )
        assertEquals(DrivingCountry.entries.toSet(), OnboardingOptions.countries.toSet())
    }

    @Test
    fun experiences_threeOptions() {
        assertEquals(KoreaExperience.entries.toList(), OnboardingOptions.experiences)
    }
}
