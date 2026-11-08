package com.roadmatekorea.android.ui.drive

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.roadmatekorea.android.R
import com.roadmatekorea.android.rules.GuidanceId
import com.roadmatekorea.android.rules.Priority
import com.roadmatekorea.android.ui.scene.SceneKind

/**
 * 안내 카드 내용(Figma `GuidanceCard`): 머리글, 한 줄 지시, 한 줄 설명, 상황 그림.
 * 음성은 guidance_strings.xml의 전체 문장을 읽고, 카드는 1~2초에 읽히는 요약을 보여 준다.
 * 형식 인자(거리, 제한 속도)는 세 문장 모두에 넘긴다. 자리표시자가 없는 문장은 인자를 무시한다.
 */
data class GuidanceCardContent(
    @StringRes val kicker: Int,
    @StringRes val headline: Int,
    @StringRes val body: Int,
    @DrawableRes val icon: Int,
    /** null이면 그림 없이 아이콘과 글자만 보여 준다. */
    val scene: SceneKind?,
    /** 안전 경고면 빨간 띠와 빨간 바탕 */
    val safety: Boolean,
)

object GuidanceCards {

    fun of(id: GuidanceId): GuidanceCardContent {
        val safety = id.priority == Priority.SAFETY
        return when (id) {
            // 우측통행 비교 그림(왼쪽 차선 금지 표시)은 좌측통행 국가 운전자에게만 나가는 이 안내에만 쓴다
            GuidanceId.WELCOME_RIGHT_SIDE -> card(R.string.card_welcome_right_side_kicker, R.string.card_welcome_right_side_headline, R.string.card_welcome_right_side_body, R.drawable.ic_rm_steering, SceneKind.KEEP_RIGHT, safety)
            GuidanceId.WELCOME_FIRST_TIME -> card(R.string.card_welcome_first_time_kicker, R.string.card_welcome_first_time_headline, R.string.card_welcome_first_time_body, R.drawable.ic_rm_book, null, safety)
            GuidanceId.WELCOME_EXPERIENCED -> card(R.string.card_welcome_experienced_kicker, R.string.card_welcome_experienced_headline, R.string.card_welcome_experienced_body, R.drawable.ic_rm_check, null, safety)
            GuidanceId.LHD_INTRO -> card(R.string.card_lhd_intro_kicker, R.string.card_lhd_intro_headline, R.string.card_lhd_intro_body, R.drawable.ic_rm_steering, SceneKind.LANE_CHANGE_LHD, safety)
            GuidanceId.LHD_MIRROR_CHECK -> card(R.string.card_lhd_mirror_check_kicker, R.string.card_lhd_mirror_check_headline, R.string.card_lhd_mirror_check_body, R.drawable.ic_rm_lane_change, SceneKind.LANE_CHANGE_LHD, safety)
            GuidanceId.RIGHT_SIDE_LEFT_TURN -> card(R.string.card_right_side_left_turn_kicker, R.string.card_right_side_left_turn_headline, R.string.card_right_side_left_turn_body, R.drawable.ic_rm_turn_left, SceneKind.UNPROTECTED_LEFT, safety)
            GuidanceId.RIGHT_TURN_RULE -> card(R.string.card_right_turn_rule_kicker, R.string.card_right_turn_rule_headline, R.string.card_right_turn_rule_body, R.drawable.ic_rm_turn_right, SceneKind.RIGHT_TURN_RED, safety)
            GuidanceId.LEFT_TURN_RULE -> card(R.string.card_left_turn_rule_kicker, R.string.card_left_turn_rule_headline, R.string.card_left_turn_rule_body, R.drawable.ic_rm_turn_left, SceneKind.UNPROTECTED_LEFT, safety)
            GuidanceId.SCHOOL_ZONE_AHEAD -> card(R.string.card_school_zone_ahead_kicker, R.string.card_school_zone_ahead_headline, R.string.card_school_zone_ahead_body, R.drawable.ic_rm_children, SceneKind.SCHOOL_ZONE, safety)
            GuidanceId.SCHOOL_ZONE_SPEED_LIMIT -> card(R.string.card_school_zone_speed_limit_kicker, R.string.card_school_zone_speed_limit_headline, R.string.card_school_zone_speed_limit_body, R.drawable.ic_rm_children, SceneKind.SCHOOL_ZONE, safety)
            GuidanceId.SCHOOL_ZONE_CROSSWALK -> card(R.string.card_school_zone_crosswalk_kicker, R.string.card_school_zone_crosswalk_headline, R.string.card_school_zone_crosswalk_body, R.drawable.ic_rm_children, SceneKind.SCHOOL_ZONE, safety)
            GuidanceId.BUS_LANE_AHEAD -> card(R.string.card_bus_lane_ahead_kicker, R.string.card_bus_lane_ahead_headline, R.string.card_bus_lane_ahead_body, R.drawable.ic_rm_bus, SceneKind.BUS_LANE, safety)
            GuidanceId.SECTION_START -> card(R.string.card_section_start_kicker, R.string.card_section_start_headline, R.string.card_section_start_body, R.drawable.ic_rm_camera, SceneKind.SECTION_CHECK, safety)
            GuidanceId.SECTION_OVER_AVERAGE -> card(R.string.card_section_over_average_kicker, R.string.card_section_over_average_headline, R.string.card_section_over_average_body, R.drawable.ic_rm_camera, SceneKind.SECTION_CHECK, safety)
            GuidanceId.ARRIVED -> card(R.string.card_arrived_kicker, R.string.card_arrived_headline, R.string.card_arrived_body, R.drawable.ic_rm_flag, null, safety)
        }
    }

    private fun card(kicker: Int, headline: Int, body: Int, icon: Int, scene: SceneKind?, safety: Boolean) =
        GuidanceCardContent(kicker, headline, body, icon, scene, safety)
}
