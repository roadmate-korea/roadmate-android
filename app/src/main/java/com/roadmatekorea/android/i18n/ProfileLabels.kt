package com.roadmatekorea.android.i18n

import androidx.annotation.StringRes
import com.roadmatekorea.android.R
import com.roadmatekorea.android.profile.model.AppLanguage
import com.roadmatekorea.android.profile.model.DrivingCountry
import com.roadmatekorea.android.profile.model.KoreaExperience

/** 프로필 선택지 → 화면 문자열 리소스. 국가 이름은 고른 언어로, 언어 이름은 각자 자기 언어로 보여 준다. */
object ProfileLabels {

    @StringRes
    fun language(language: AppLanguage): Int = when (language) {
        AppLanguage.EN -> R.string.language_name_en
        AppLanguage.FR -> R.string.language_name_fr
        AppLanguage.ES -> R.string.language_name_es
        AppLanguage.KO -> R.string.language_name_ko
    }

    @StringRes
    fun country(country: DrivingCountry): Int = when (country) {
        DrivingCountry.CN -> R.string.country_cn
        DrivingCountry.JP -> R.string.country_jp
        DrivingCountry.TW -> R.string.country_tw
        DrivingCountry.US -> R.string.country_us
        DrivingCountry.HK -> R.string.country_hk
        DrivingCountry.PH -> R.string.country_ph
        DrivingCountry.VN -> R.string.country_vn
        DrivingCountry.TH -> R.string.country_th
        DrivingCountry.SG -> R.string.country_sg
        DrivingCountry.MY -> R.string.country_my
        DrivingCountry.OTHER -> R.string.country_other
    }

    @StringRes
    fun experience(experience: KoreaExperience): Int = when (experience) {
        KoreaExperience.DROVE_IN_KOREA -> R.string.experience_drove_in_korea
        KoreaExperience.FIRST_TIME_IN_KOREA -> R.string.experience_first_time_in_korea
        KoreaExperience.FIRST_TIME_LEFT_HAND_DRIVE -> R.string.experience_first_time_left_hand_drive
    }
}
