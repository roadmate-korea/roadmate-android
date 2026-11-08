package com.roadmatekorea.android.profile.model

/**
 * 저장된 문자열 ↔ 프로필. enum 이름으로 저장한다.
 * 하나라도 없거나 알 수 없는 값이면 null을 돌려주고, 앱은 온보딩을 다시 연다.
 */
object ProfileCodec {

    data class Stored(val language: String, val country: String, val experience: String)

    fun encode(profile: DriverProfile) = Stored(
        language = profile.language.name,
        country = profile.country.name,
        experience = profile.experience.name,
    )

    fun decode(language: String?, country: String?, experience: String?): DriverProfile? {
        val l = AppLanguage.entries.find { it.name == language } ?: return null
        val c = DrivingCountry.entries.find { it.name == country } ?: return null
        val e = KoreaExperience.entries.find { it.name == experience } ?: return null
        return DriverProfile(l, c, e)
    }
}
