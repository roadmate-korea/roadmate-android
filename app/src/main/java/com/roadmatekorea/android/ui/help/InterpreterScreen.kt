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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.roadmatekorea.android.R
import com.roadmatekorea.android.interpreter.InterpreterUi
import com.roadmatekorea.android.interpreter.Phrase
import com.roadmatekorea.android.interpreter.Phrases
import com.roadmatekorea.android.interpreter.Situation
import com.roadmatekorea.android.ui.LocalCompact
import com.roadmatekorea.android.ui.theme.RoadMate

/**
 * 정차 중 통역(Figma 10): 상황 선택, 미리 번역해 둔 문장 카드, 고른 카드의 한국어(크게, 직원에게 보여 줄 것).
 * 번역 서버 없이 기기 안에서만 동작한다. 화면을 떠나면 고른 카드를 지운다.
 */
@Composable
fun InterpreterScreen(
    state: InterpreterUi,
    onBack: () -> Unit,
    onSituation: (Situation) -> Unit,
    onPhrase: (Phrase) -> Unit,
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
        Spacer(Modifier.size(20.dp))
        if (compact) {
            // 좁은 창(분할 화면): 한국어를 위에 두고 카드 목록만 스크롤한다(고른 문장이 늘 보이게)
            KoreanPanel(state, Modifier.fillMaxWidth())
            Spacer(Modifier.size(16.dp))
            PhrasePanel(state, onPhrase, Modifier.fillMaxWidth().weight(1f))
        } else {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                PhrasePanel(state, onPhrase, Modifier.weight(1f))
                KoreanPanel(state, Modifier.weight(1f))
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

/** 상황별 문장 카드(운전자 언어). 누르면 한국어로 보여 주고 읽는다. */
@Composable
private fun PhrasePanel(state: InterpreterUi, onPhrase: (Phrase) -> Unit, modifier: Modifier) {
    val c = RoadMate.colors
    Panel(c.surface, modifier) {
        Text(stringResource(R.string.interp_phrases), style = RoadMate.type.labelM, color = c.inkSecondary)
        // 카드 목록만 스크롤한다(좁은 창이나 큰 글자에서도 모든 카드에 닿게)
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Phrases.of(state.situation).forEach { phrase -> PhraseRow(phrase, onPhrase) }
        }
    }
}

/** 고른 카드의 한국어(크게)와 운전자 언어 문장(작게). Figma처럼 노란 바탕. */
@Composable
private fun KoreanPanel(state: InterpreterUi, modifier: Modifier) {
    val c = RoadMate.colors
    Panel(c.centerline, modifier) {
        Text(stringResource(R.string.interp_show_staff), style = RoadMate.type.labelM, color = c.onCenterline)
        val shown = state.shown
        if (shown == null) {
            Text(stringResource(R.string.interp_hint), style = RoadMate.type.bodyM, color = c.onCenterline)
        } else {
            Text(shown.korean, style = RoadMate.type.headlineS, color = c.onCenterline)
            Text(shown.driverText, style = RoadMate.type.bodyM, color = c.onCenterline)
        }
    }
}

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
