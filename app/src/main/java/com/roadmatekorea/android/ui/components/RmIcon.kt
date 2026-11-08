package com.roadmatekorea.android.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.roadmatekorea.android.ui.theme.RoadMate

/** 직접 그린 아이콘(res/drawable/ic_rm_*). 글자와 함께 쓰면 [contentDescription]은 null. */
@Composable
fun RmIcon(
    @DrawableRes id: Int,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    tint: Color = RoadMate.colors.ink,
) {
    Icon(painterResource(id), contentDescription, modifier.size(size), tint = tint)
}
