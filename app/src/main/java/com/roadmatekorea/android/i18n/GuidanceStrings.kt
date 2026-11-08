package com.roadmatekorea.android.i18n

import android.content.res.Resources
import androidx.annotation.StringRes
import com.roadmatekorea.android.R
import com.roadmatekorea.android.rules.Guidance
import com.roadmatekorea.android.rules.GuidanceId

/**
 * 안내 ID → 문자열 리소스. 문장은 언어별 `guidance_strings.xml`에만 있다.
 * 화면 자막과 음성이 같은 문장을 쓰도록, 문장은 항상 여기서 꺼낸다.
 */
object GuidanceStrings {

    @StringRes
    fun resId(id: GuidanceId): Int = when (id) {
        GuidanceId.WELCOME_RIGHT_SIDE -> R.string.guidance_welcome_right_side
        GuidanceId.WELCOME_FIRST_TIME -> R.string.guidance_welcome_first_time
        GuidanceId.WELCOME_EXPERIENCED -> R.string.guidance_welcome_experienced
        GuidanceId.LHD_INTRO -> R.string.guidance_lhd_intro
        GuidanceId.LHD_MIRROR_CHECK -> R.string.guidance_lhd_mirror_check
        GuidanceId.RIGHT_SIDE_LEFT_TURN -> R.string.guidance_right_side_left_turn
        GuidanceId.RIGHT_TURN_RULE -> R.string.guidance_right_turn_rule
        GuidanceId.LEFT_TURN_RULE -> R.string.guidance_left_turn_rule
        GuidanceId.SCHOOL_ZONE_AHEAD -> R.string.guidance_school_zone_ahead
        GuidanceId.SCHOOL_ZONE_SPEED_LIMIT -> R.string.guidance_school_zone_speed_limit
        GuidanceId.SCHOOL_ZONE_CROSSWALK -> R.string.guidance_school_zone_crosswalk
        GuidanceId.ARRIVED -> R.string.guidance_arrived
    }

    /** [resources]는 프로필 언어로 맞춘 Context의 것을 넘긴다(시스템 언어가 아니라). */
    fun text(resources: Resources, guidance: Guidance): String {
        val res = resId(guidance.id)
        return if (guidance.args.isEmpty()) {
            resources.getString(res)
        } else {
            resources.getString(res, *guidance.args.toTypedArray())
        }
    }
}
