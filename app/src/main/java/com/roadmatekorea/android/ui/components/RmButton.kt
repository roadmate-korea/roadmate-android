package com.roadmatekorea.android.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.roadmatekorea.android.ui.theme.RoadMate

enum class RmButtonStyle { Primary, Secondary, Text, Danger }

/**
 * 버튼. 높이 88, 모서리 12 (Figma `Button`).
 * Primary(노랑)는 화면에 하나만, Danger는 위험 동작과 긴급 전화에만.
 */
@Composable
fun RmButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: RmButtonStyle = RmButtonStyle.Primary,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
) {
    val c = RoadMate.colors
    val (bg, fg) = when (style) {
        RmButtonStyle.Primary -> c.centerline to c.onCenterline
        RmButtonStyle.Secondary -> c.surface to c.ink
        RmButtonStyle.Text -> Color.Transparent to c.ink
        RmButtonStyle.Danger -> c.danger to c.onDanger
    }
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 88.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = bg,
            contentColor = fg,
            disabledContainerColor = if (style == RmButtonStyle.Text) Color.Transparent else c.sunken,
            disabledContentColor = c.inkTertiary,
        ),
        border = if (style == RmButtonStyle.Secondary) BorderStroke(2.dp, if (enabled) c.lineStrong else c.line) else null,
        contentPadding = PaddingValues(horizontal = 40.dp, vertical = 16.dp),
        elevation = null,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) RmIcon(icon, size = 36.dp, tint = if (enabled) fg else c.inkTertiary)
            Text(text, style = RoadMate.type.titleS)
        }
    }
}
