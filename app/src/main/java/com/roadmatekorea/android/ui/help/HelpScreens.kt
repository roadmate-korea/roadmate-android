package com.roadmatekorea.android.ui.help

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.roadmatekorea.android.R
import com.roadmatekorea.android.rules.Guidance
import com.roadmatekorea.android.rules.GuidanceId
import com.roadmatekorea.android.ui.LocalCompact
import com.roadmatekorea.android.ui.components.RmButton
import com.roadmatekorea.android.ui.components.RmButtonStyle
import com.roadmatekorea.android.ui.components.RmIcon
import com.roadmatekorea.android.ui.theme.RoadMate

/** 상황 도움에서 여는 화면. */
enum class HelpRoute { MENU, ACCIDENT, INTERPRETER }

/**
 * 상황 도움(Figma 09): 정차(P) 중에만 연다. 지금은 사고 항목과 긴급 번호만 있다.
 * 주유소, 고장, 요금소, 주차 항목은 기능이 생길 때 넣는다(동작 없는 버튼은 두지 않는다).
 */
@Composable
fun HelpScreen(onBack: () -> Unit, onAccident: () -> Unit, onInterpreter: () -> Unit = {}) {
    val c = RoadMate.colors
    val compact = LocalCompact.current
    Column(Modifier.fillMaxSize().background(c.canvas).padding(start = 40.dp, end = 40.dp, top = 24.dp, bottom = 40.dp)) {
        HelpBackRow(stringResource(R.string.help_title), onBack)
        Spacer(Modifier.size(24.dp))
        if (compact) {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                HelpItem(R.drawable.ic_rm_warning, stringResource(R.string.help_accident), stringResource(R.string.help_accident_sub), onAccident)
                HelpItem(R.drawable.ic_rm_translate, stringResource(R.string.help_interpreter), stringResource(R.string.help_interpreter_sub), onInterpreter)
                EmergencyNumbers()
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(56.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    HelpItem(R.drawable.ic_rm_warning, stringResource(R.string.help_accident), stringResource(R.string.help_accident_sub), onAccident)
                    HelpItem(R.drawable.ic_rm_translate, stringResource(R.string.help_interpreter), stringResource(R.string.help_interpreter_sub), onInterpreter)
                }
                Box(Modifier.width(560.dp)) { EmergencyNumbers() }
            }
        }
    }
}

/**
 * 사고(Figma 09-2): 할 일 4단계, 주변 사람에게 보여 줄 한국어 카드, 전화 버튼.
 * 화면을 열면 할 일을 프로필 언어로 읽는다. 한국어 카드는 버튼으로 한국어로 읽는다.
 * 전화는 전화 앱의 번호 입력 화면만 연다(권한 없이, 운전자가 직접 건다).
 */
@Composable
fun AccidentScreen(onBack: () -> Unit, onSpeak: (Guidance, Boolean) -> Unit, announce: Boolean = true, onAnnounced: () -> Unit = {}) {
    val c = RoadMate.colors
    val compact = LocalCompact.current
    LaunchedEffect(Unit) {
        if (announce) {
            onSpeak(Guidance(GuidanceId.HELP_ACCIDENT_STEPS), false)
            onAnnounced()
        }
    }
    Column(Modifier.fillMaxSize().background(c.canvas).padding(start = 40.dp, end = 40.dp, top = 24.dp, bottom = 40.dp)) {
        HelpBackRow(stringResource(R.string.accident_title), onBack)
        Spacer(Modifier.size(24.dp))
        val steps = @Composable {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(stringResource(R.string.accident_first), style = RoadMate.type.titleM, color = c.ink)
                Step(1, stringResource(R.string.accident_step_1))
                Step(2, stringResource(R.string.accident_step_2))
                Step(3, stringResource(R.string.accident_step_3))
                Step(4, stringResource(R.string.accident_step_4))
            }
        }
        val call = @Composable {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                KoreanCard(onPlay = { onSpeak(Guidance(GuidanceId.PHRASE_ACCIDENT_HELP), true) })
                CallButton("119", RmButtonStyle.Danger)
                CallButton("112", RmButtonStyle.Secondary)
                val rental = stringResource(R.string.rental_company_phone)
                if (rental.isNotBlank()) CallButton(rental, RmButtonStyle.Secondary, label = stringResource(R.string.accident_call_rental))
            }
        }
        if (compact) {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(32.dp)) {
                call()
                steps()
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(56.dp)) {
                Box(Modifier.weight(1f).verticalScroll(rememberScrollState())) { steps() }
                Box(Modifier.width(640.dp)) { call() }
            }
        }
    }
}

@Composable
internal fun HelpBackRow(title: String, onBack: () -> Unit) {
    val c = RoadMate.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onBack)
            .padding(end = 16.dp),
    ) {
        RmIcon(R.drawable.ic_rm_arrow_left, contentDescription = stringResource(R.string.onboarding_back), size = 44.dp)
        Text(title, style = RoadMate.type.titleM, color = c.ink)
    }
}

@Composable
private fun HelpItem(icon: Int, title: String, sub: String, onClick: () -> Unit) {
    val c = RoadMate.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 32.dp, vertical = 20.dp),
    ) {
        RmIcon(icon, size = 44.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = RoadMate.type.titleS, color = c.ink)
            Text(sub, style = RoadMate.type.bodyS, color = c.inkSecondary)
        }
        RmIcon(R.drawable.ic_rm_chevron_right, size = 36.dp, tint = c.inkSecondary)
    }
}

/** 긴급 번호(119 소방·구급, 112 경찰, 1330 관광 통역 안내). 번호는 고정된 공공 번호다. */
@Composable
private fun EmergencyNumbers() {
    val c = RoadMate.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface).padding(32.dp),
    ) {
        Text(stringResource(R.string.help_emergency), style = RoadMate.type.titleS, color = c.ink)
        NumberRow("119", stringResource(R.string.help_119))
        NumberRow("112", stringResource(R.string.help_112))
        NumberRow("1330", stringResource(R.string.help_1330))
        CallButton("119", RmButtonStyle.Danger)
    }
}

@Composable
private fun NumberRow(number: String, label: String) {
    val c = RoadMate.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Text(number, style = RoadMate.type.numberM, color = c.ink, modifier = Modifier.width(120.dp))
        Text(label, style = RoadMate.type.bodyS, color = c.inkSecondary)
    }
}

@Composable
private fun Step(n: Int, text: String) {
    val c = RoadMate.colors
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.Top) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(56.dp).clip(CircleShape).background(c.surface),
        ) { Text(n.toString(), style = RoadMate.type.numberM, color = c.ink) }
        Text(text, style = RoadMate.type.bodyL, color = c.ink, modifier = Modifier.weight(1f).padding(top = 6.dp))
    }
}

/** 주변 사람(한국인)에게 보여 줄 카드: 큰 한국어 + 운전자 언어 번역 + 한국어로 읽기. */
@Composable
private fun KoreanCard(onPlay: () -> Unit) {
    val c = RoadMate.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.centerline).padding(32.dp),
    ) {
        Text(stringResource(R.string.accident_show), style = RoadMate.type.labelM, color = c.onCenterline)
        // 카드의 한국어는 언어와 상관없이 같은 문장(카드 문구 리소스)
        Text(stringResource(R.string.card_phrase_accident_help_headline), style = RoadMate.type.headlineS, color = c.onCenterline)
        Text(stringResource(R.string.guidance_phrase_accident_help), style = RoadMate.type.bodyM, color = c.onCenterline)
        RmButton(stringResource(R.string.accident_play_korean), onClick = onPlay, style = RmButtonStyle.Secondary, icon = R.drawable.ic_rm_speaker)
    }
}

@Composable
private fun CallButton(number: String, style: RmButtonStyle, label: String = stringResource(R.string.help_call, number)) {
    val context = LocalContext.current
    RmButton(label, onClick = { dial(context, number) }, style = style, icon = R.drawable.ic_rm_phone, modifier = Modifier.fillMaxWidth())
}

/** 전화 앱의 번호 입력 화면을 연다(CALL_PHONE 권한 없이). 전화 앱이 없으면 아무 일도 하지 않는다. */
private fun dial(context: Context, number: String) {
    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number.filter { it.isDigit() || it == '+' }))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w("RoadMateHelp", "no dialer", e)
    }
}
