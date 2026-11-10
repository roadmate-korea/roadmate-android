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
        GuidanceId.SCHOOL_ZONE_NEARBY -> R.string.guidance_school_zone_nearby
        GuidanceId.SCHOOL_ZONE_SPEED_LIMIT -> R.string.guidance_school_zone_speed_limit
        GuidanceId.SCHOOL_ZONE_CROSSWALK -> R.string.guidance_school_zone_crosswalk
        GuidanceId.BUS_LANE_AHEAD -> R.string.guidance_bus_lane_ahead
        GuidanceId.SECTION_START -> R.string.guidance_section_start
        GuidanceId.TOLL_AHEAD -> R.string.guidance_toll_ahead
        GuidanceId.SECTION_OVER_AVERAGE -> R.string.guidance_section_over_average
        GuidanceId.SCHOOL_ZONE_OVER_SPEED -> R.string.guidance_school_zone_over_speed
        GuidanceId.SCHOOL_ZONE_APPROACH_FAST -> R.string.guidance_school_zone_approach_fast
        GuidanceId.ARRIVED -> R.string.guidance_arrived
        GuidanceId.ANSWER_SCHOOL_ZONE -> R.string.guidance_answer_school_zone
        GuidanceId.ANSWER_BUS_LANE -> R.string.guidance_answer_bus_lane
        GuidanceId.ANSWER_SECTION -> R.string.guidance_answer_section
        GuidanceId.ANSWER_SPEED_LIMIT -> R.string.guidance_answer_speed_limit
        GuidanceId.ANSWER_SPEED_LIMIT_UNKNOWN -> R.string.guidance_answer_speed_limit_unknown
        GuidanceId.ANSWER_HELP -> R.string.guidance_answer_help
        GuidanceId.ANSWER_NOT_HEARD -> R.string.guidance_answer_not_heard
        GuidanceId.HELP_ACCIDENT_STEPS -> R.string.guidance_help_accident_steps
        GuidanceId.PHRASE_ACCIDENT_HELP -> R.string.guidance_phrase_accident_help
        GuidanceId.INTERPRETER_SPEECH -> R.string.guidance_interpreter_speech
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
