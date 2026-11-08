package com.roadmatekorea.android.ui.onboarding

import com.roadmatekorea.android.profile.model.AppLanguage
import com.roadmatekorea.android.profile.model.DriverProfile
import com.roadmatekorea.android.profile.model.DrivingCountry
import com.roadmatekorea.android.profile.model.KoreaExperience

/**
 * 온보딩 2페이지의 상태(순수 Kotlin).
 * PAGE 1에서 언어를 고르면 바로 PAGE 2로 넘어가고, PAGE 2의 화면 언어는 고른 언어를 따른다.
 */
data class OnboardingState(
    val page: Page = Page.LANGUAGE,
    val language: AppLanguage? = null,
    val country: DrivingCountry? = null,
    val experience: KoreaExperience? = null,
) {
    enum class Page { LANGUAGE, DRIVING }

    val canStart: Boolean get() = language != null && country != null && experience != null

    fun selectLanguage(language: AppLanguage) = copy(language = language, page = Page.DRIVING)

    fun selectCountry(country: DrivingCountry) = copy(country = country)

    fun selectExperience(experience: KoreaExperience) = copy(experience = experience)

    fun back() = copy(page = Page.LANGUAGE)

    fun toProfile(): DriverProfile? {
        return DriverProfile(language ?: return null, country ?: return null, experience ?: return null)
    }

    companion object {
        /** 설정을 다시 할 때는 저장된 값을 미리 골라 둔 채 PAGE 1부터 연다. */
        fun from(existing: DriverProfile?) = OnboardingState(
            language = existing?.language,
            country = existing?.country,
            experience = existing?.experience,
        )
    }
}

object OnboardingOptions {

    /** 한국어는 개발과 테스트용이라 디버그 빌드에서만 보여 준다. */
    fun languages(includeKorean: Boolean): List<AppLanguage> =
        listOf(AppLanguage.EN, AppLanguage.FR, AppLanguage.ES) + if (includeKorean) listOf(AppLanguage.KO) else emptyList()

    /** 고정 목록. 고른 언어로 거르지 않는다(기획서 2.4절). */
    val countries: List<DrivingCountry> = listOf(
        DrivingCountry.CN, DrivingCountry.JP, DrivingCountry.TW, DrivingCountry.US,
        DrivingCountry.HK, DrivingCountry.PH, DrivingCountry.VN, DrivingCountry.TH,
        DrivingCountry.SG, DrivingCountry.MY, DrivingCountry.OTHER,
    )

    val experiences: List<KoreaExperience> = listOf(
        KoreaExperience.DROVE_IN_KOREA,
        KoreaExperience.FIRST_TIME_IN_KOREA,
        KoreaExperience.FIRST_TIME_LEFT_HAND_DRIVE,
    )
}
