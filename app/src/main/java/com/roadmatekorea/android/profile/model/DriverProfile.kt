package com.roadmatekorea.android.profile.model

/** 화면과 음성 안내 언어. KO는 개발과 테스트용으로, 디버그 빌드에서만 고를 수 있게 한다. */
enum class AppLanguage { EN, FR, ES, KO }

/** 평소 운전하는 국가. 온보딩 PAGE 2의 고정 목록이며, 선택한 언어로 거르지 않는다. */
enum class DrivingCountry { CN, JP, TW, US, HK, PH, VN, TH, SG, MY, OTHER }

/** 한국 운전 경험. 온보딩 PAGE 2 질문 2의 세 선택지. */
enum class KoreaExperience {
    DROVE_IN_KOREA,
    FIRST_TIME_IN_KOREA,

    /** MVP에서는 FIRST_TIME_IN_KOREA의 상세 안내에 좌핸들 적응 안내를 더한다. */
    FIRST_TIME_LEFT_HAND_DRIVE,
}

data class DriverProfile(
    val language: AppLanguage,
    val country: DrivingCountry,
    val experience: KoreaExperience,
)
