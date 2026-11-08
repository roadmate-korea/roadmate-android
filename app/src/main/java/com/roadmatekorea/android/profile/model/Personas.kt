package com.roadmatekorea.android.profile.model

/** 시연 페르소나 (docs/mvp-scenario.md 4장). 시연 모드와 테스트가 함께 쓴다. */
object Personas {
    /** 일본에서 운전, 영어 사용, 좌핸들 차량은 처음. */
    val NAKAMURA = DriverProfile(
        language = AppLanguage.EN,
        country = DrivingCountry.JP,
        experience = KoreaExperience.FIRST_TIME_LEFT_HAND_DRIVE,
    )

    /** 과테말라(기타 국가)에서 운전, 스페인어 사용, 한국 운전은 처음. */
    val CARLOS = DriverProfile(
        language = AppLanguage.ES,
        country = DrivingCountry.OTHER,
        experience = KoreaExperience.FIRST_TIME_IN_KOREA,
    )
}
