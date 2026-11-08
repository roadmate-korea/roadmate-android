package com.roadmatekorea.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.IntrinsicSize
import com.roadmatekorea.android.R
import com.roadmatekorea.android.ui.theme.RoadMate

/**
 * 선택 줄(Figma `OptionRow`). 한 그룹에서 하나만 고르는 라디오.
 * 고르면 왼쪽에 노란 중앙선 띠, 먹색 테두리, 체크가 생긴다.
 */
@Composable
fun OptionRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
    minHeight: Dp = 104.dp,
) {
    val c = RoadMate.colors
    val shape = RoundedCornerShape(8.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .heightIn(min = minHeight)
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(c.surface)
            .then(if (selected) Modifier.border(2.dp, c.ink, shape) else Modifier)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick),
    ) {
        Box(Modifier.width(10.dp).fillMaxHeight().background(if (selected) c.centerline else Color.Transparent))
        Column(Modifier.weight(1f).padding(start = 28.dp, top = 20.dp, bottom = 20.dp)) {
            Text(
                text = title,
                style = RoadMate.type.titleS,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (selected) c.ink else c.inkSecondary,
            )
            if (description != null) {
                Text(description, style = RoadMate.type.bodyS, color = c.inkTertiary)
            }
        }
        if (selected) RmIcon(R.drawable.ic_rm_check, size = 40.dp, modifier = Modifier.padding(start = 16.dp))
        Spacer(Modifier.width(28.dp))
    }
}
