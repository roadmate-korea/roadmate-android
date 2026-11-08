package com.roadmatekorea.android

import android.app.Application
import com.roadmatekorea.android.profile.ProfileRepository

/** 앱 전체에서 하나만 쓰는 객체를 둔다(간단한 수동 의존성 주입). */
class RoadMateApplication : Application() {
    val profileRepository: ProfileRepository by lazy { ProfileRepository(this) }
}
