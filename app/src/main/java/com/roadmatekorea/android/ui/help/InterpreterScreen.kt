package com.roadmatekorea.android.ui.help

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.roadmatekorea.android.R
import com.roadmatekorea.android.interpreter.InterpreterNotice
import com.roadmatekorea.android.interpreter.InterpreterUi
import com.roadmatekorea.android.interpreter.Phrase
import com.roadmatekorea.android.interpreter.Phrases
import com.roadmatekorea.android.interpreter.Side
import com.roadmatekorea.android.interpreter.Situation
import com.roadmatekorea.android.ui.LocalCompact
import com.roadmatekorea.android.ui.components.MicButton
import com.roadmatekorea.android.ui.theme.RoadMate

/**
 * 정차 중 통역(Figma 10): 상황 선택, 내 말(운전자 언어 → 한국어), 직원 말(한국어 → 운전자 언어).
 * 미리 번역해 둔 문장 카드는 연결 없이도 쓸 수 있다. 화면을 떠나면 듣기를 멈춘다.
 */
@Composable
fun InterpreterScreen(
    state: InterpreterUi,
    onBack: () -> Unit,
    onSituation: (Situation) -> Unit,
    onPhrase: (Phrase) -> Unit,
    onListen: (Side) -> Unit,
    onEnter: () -> Unit,
    onLeave: () -> Unit,
) {
    val c = RoadMate.colors
    val compact = LocalCompact.current
    DisposableEffect(Unit) {
        onEnter()
        onDispose(onLeave)
    }
    Column(Modifier.fillMaxSize().background(c.canvas).padding(start = 40.dp, end = 40.dp, top = 24.dp, bottom = 32.dp)) {
        HelpBackRow(stringResource(R.string.interp_title), onBack)
        Spacer(Modifier.size(12.dp))
        SituationChips(state.situation, onSituation)
        state.notice?.let { Spacer(Modifier.size(12.dp)); NoticeLine(it) }
        Spacer(Modifier.size(20.dp))
        if (compact) {
            // 좁은 창(분할 화면): 두 패널은 위아래로 스크롤하고, 두 마이크는 아래에 고정해 늘 보이게 한다
            val scroll = rememberScrollState()
            // 새 번역이 오면 그 패널이 보이게 한다(내 말은 위, 직원 말은 아래)
            LaunchedEffect(state.mine) { if (state.mine != null) scroll.animateScrollTo(0) }
            LaunchedEffect(state.staff) { if (state.staff != null) scroll.animateScrollTo(scroll.maxValue) }
            Column(Modifier.weight(1f).verticalScroll(scroll), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                MinePanel(state, onPhrase, onListen, Modifier.fillMaxWidth(), showMic = false)
                StaffPanel(state, onListen, Modifier.fillMaxWidth(), showMic = false)
            }
            Spacer(Modifier.size(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MicButton(
                    listening = state.listening == Side.DRIVER,
                    preparing = state.listening == Side.DRIVER && state.preparing,
                    onClick = { onListen(Side.DRIVER) },
                    label = stringResource(R.string.interp_speak_mine),
                    modifier = Modifier.weight(1f),
                )
                MicButton(
                    listening = state.listening == Side.STAFF,
                    preparing = state.listening == Side.STAFF && state.preparing,
                    onClick = { onListen(Side.STAFF) },
                    label = stringResource(R.string.interp_speak_staff),
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            val mine = @Composable { modifier: Modifier -> MinePanel(state, onPhrase, onListen, modifier) }
            val staff = @Composable { modifier: Modifier -> StaffPanel(state, onListen, modifier) }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                mine(Modifier.weight(1f))
                staff(Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SituationChips(selected: Situation, onSituation: (Situation) -> Unit) {
    val c = RoadMate.colors
    // 좁은 창에서도 상황 네 개가 모두 보이게 줄을 바꾼다
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.interp_situation),
            style = RoadMate.type.labelM,
            color = c.inkSecondary,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        Situation.entries.forEach { s ->
            val on = s == selected
            Text(
                stringResource(situationName(s)),
                style = RoadMate.type.titleS,
                color = if (on) c.onCenterline else c.ink,
                modifier = Modifier
                    .clip(RoundedCornerShape(32.dp))
                    .background(if (on) c.centerline else c.surface)
                    .then(if (on) Modifier else Modifier.border(BorderStroke(2.dp, c.lineStrong), RoundedCornerShape(32.dp)))
                    .clickable(role = Role.Tab, onClick = { onSituation(s) })
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun NoticeLine(notice: InterpreterNotice) {
    val c = RoadMate.colors
    val text = stringResource(
        when (notice) {
            InterpreterNotice.NO_CONNECTION -> R.string.interp_no_connection
            InterpreterNotice.STAFF_NEEDS_CONNECTION -> R.string.interp_staff_needs_connection
            InterpreterNotice.NOT_HEARD -> R.string.interp_not_heard
            InterpreterNotice.LANGUAGE_NOT_SUPPORTED -> R.string.interp_language_not_supported
            InterpreterNotice.SAY_AGAIN -> R.string.interp_say_again
        },
    )
    Text(
        text,
        style = RoadMate.type.bodyM,
        color = c.ink,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.sunken).padding(horizontal = 24.dp, vertical = 12.dp),
    )
}

/** 내 말: 한국어(크게, 직원에게 보여 줄 것), 내가 한 말(작게), 문장 카드, 내 언어 마이크. */
@Composable
private fun MinePanel(state: InterpreterUi, onPhrase: (Phrase) -> Unit, onListen: (Side) -> Unit, modifier: Modifier, showMic: Boolean = true) {
    val c = RoadMate.colors
    Panel(c.surface, modifier) {
        Text(stringResource(R.string.interp_you_said), style = RoadMate.type.labelM, color = c.inkSecondary)
        val mine = state.mine
        when {
            state.listening == Side.DRIVER -> Text(listeningText(state), style = RoadMate.type.headlineS, color = c.ink)
            mine != null -> {
                // 번역 중에만 "…". 실패하면 비워 두고 알림 줄이 문장 카드를 쓰라고 알린다
                (mine.translated ?: "…".takeIf { state.translating == Side.DRIVER })?.let {
                    Text(it, style = RoadMate.type.headlineS, color = c.ink)
                }
                Text(mine.original, style = RoadMate.type.bodyM, color = c.inkSecondary)
            }
            else -> Text(stringResource(R.string.interp_hint_mine), style = RoadMate.type.bodyM, color = c.inkSecondary)
        }
        Spacer(Modifier.size(8.dp))
        Text(stringResource(R.string.interp_phrases), style = RoadMate.type.labelM, color = c.inkTertiary)
        Phrases.of(state.situation).forEach { phrase -> PhraseRow(phrase, onPhrase) }
        if (!showMic) return@Panel
        Spacer(Modifier.size(8.dp))
        MicButton(
            listening = state.listening == Side.DRIVER,
            preparing = state.listening == Side.DRIVER && state.preparing,
            onClick = { onListen(Side.DRIVER) },
            label = stringResource(R.string.interp_speak_mine),
        )
    }
}

/** 직원 말: 내 언어(크게), 직원이 한 한국어(작게), 한국어 마이크. Figma처럼 노란 바탕. */
@Composable
private fun StaffPanel(state: InterpreterUi, onListen: (Side) -> Unit, modifier: Modifier, showMic: Boolean = true) {
    val c = RoadMate.colors
    Panel(c.centerline, modifier) {
        Text(stringResource(R.string.interp_staff_said), style = RoadMate.type.labelM, color = c.onCenterline)
        val staff = state.staff
        when {
            state.listening == Side.STAFF -> Text(listeningText(state), style = RoadMate.type.headlineS, color = c.onCenterline)
            staff != null -> {
                (staff.translated ?: "…".takeIf { state.translating == Side.STAFF })?.let {
                    Text(it, style = RoadMate.type.headlineS, color = c.onCenterline)
                }
                Text(staff.original, style = RoadMate.type.bodyM, color = c.onCenterline)
            }
            else -> Text(stringResource(R.string.interp_hint_staff), style = RoadMate.type.bodyM, color = c.onCenterline)
        }
        if (!showMic) return@Panel
        Spacer(Modifier.weight(1f, fill = false))
        MicButton(
            listening = state.listening == Side.STAFF,
            preparing = state.listening == Side.STAFF && state.preparing,
            onClick = { onListen(Side.STAFF) },
            label = stringResource(R.string.interp_speak_staff),
        )
    }
}

@Composable
private fun listeningText(state: InterpreterUi): String =
    stringResource(if (state.preparing) R.string.drive_mic_preparing else R.string.interp_listening)

@Composable
private fun Panel(background: Color, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(background).padding(32.dp),
        content = content,
    )
}

@Composable
private fun PhraseRow(phrase: Phrase, onPhrase: (Phrase) -> Unit) {
    val c = RoadMate.colors
    Text(
        stringResource(phrase.driverText),
        style = RoadMate.type.bodyM,
        color = c.ink,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.canvas)
            .clickable(role = Role.Button, onClick = { onPhrase(phrase) })
            .padding(horizontal = 20.dp, vertical = 14.dp),
    )
}

private fun situationName(s: Situation): Int = when (s) {
    Situation.GAS_STATION -> R.string.sit_gas_station
    Situation.PARKING -> R.string.sit_parking
    Situation.ACCIDENT -> R.string.sit_accident
    Situation.RENTAL -> R.string.sit_rental
}
