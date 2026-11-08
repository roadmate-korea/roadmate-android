package com.roadmatekorea.android.rules

import com.roadmatekorea.android.profile.model.DriverProfile
import com.roadmatekorea.android.profile.model.DrivingCountry
import com.roadmatekorea.android.profile.model.KoreaExperience

/** 평소 운전 국가의 통행 방향. 기타 국가는 추정하지 않는다. */
enum class TrafficSide { LEFT, RIGHT, UNKNOWN }

enum class DetailLevel {
    /** 한국 교통법규를 상세히 안내한다. */
    DETAILED,

    /** 기초 설명을 줄이고 필수 안전 경고만 준다. */
    MINIMAL,
}

/**
 * 프로필로 정한 안내 범위.
 *
 * 필수 안전 경고(어린이보호구역 등)는 이 값과 상관없이 모든 사용자에게 나간다.
 * 그래서 안전 경고를 끄는 스위치는 일부러 두지 않는다.
 */
data class GuidanceScope(
    val detail: DetailLevel,
    val rightSideAdaptation: Boolean,
    val leftHandDriveAdaptation: Boolean,
)

object GuidancePolicy {

    fun trafficSideOf(country: DrivingCountry): TrafficSide = when (country) {
        DrivingCountry.JP,
        DrivingCountry.HK,
        DrivingCountry.TH,
        DrivingCountry.SG,
        DrivingCountry.MY -> TrafficSide.LEFT

        DrivingCountry.CN,
        DrivingCountry.TW,
        DrivingCountry.US,
        DrivingCountry.PH,
        DrivingCountry.VN -> TrafficSide.RIGHT

        DrivingCountry.OTHER -> TrafficSide.UNKNOWN
    }

    fun scopeFor(profile: DriverProfile): GuidanceScope {
        val experienced = profile.experience == KoreaExperience.DROVE_IN_KOREA
        return GuidanceScope(
            detail = if (experienced) DetailLevel.MINIMAL else DetailLevel.DETAILED,
            // 좌측통행 국가 + (좌핸들 처음 또는 한국 운전 처음)
            rightSideAdaptation = trafficSideOf(profile.country) == TrafficSide.LEFT && !experienced,
            leftHandDriveAdaptation = profile.experience == KoreaExperience.FIRST_TIME_LEFT_HAND_DRIVE,
        )
    }
}
