package com.panyou.missnet.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.panyou.missnet.ui.theme.ContainerTokens

@Composable
fun MissNetListDivider(
    insetStart: Dp = ContainerTokens.ListDividerInsetStart,
    insetEnd: Dp = ContainerTokens.ListDividerInsetEnd,
    alpha: Float = 0.36f
) {
    HorizontalDivider(
        modifier = Modifier.padding(start = insetStart, end = insetEnd),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = alpha)
    )
}
