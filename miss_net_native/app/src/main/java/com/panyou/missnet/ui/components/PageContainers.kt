package com.panyou.missnet.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.panyou.missnet.ui.theme.ContainerTokens

/**
 * Secondary pages shared content surface.
 * Keeps shape / stroke / inset rhythm consistent across Search/Tags/Actress/Detail/Player sub areas.
 */
@Composable
fun SecondaryPageSurface(
    modifier: Modifier = Modifier,
    outerPadding: PaddingValues = PaddingValues(
        start = ContainerTokens.ScreenCompactHorizontalPadding,
        end = ContainerTokens.ScreenCompactHorizontalPadding,
        bottom = ContainerTokens.ScreenContentPadding
    ),
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .padding(outerPadding),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        content()
    }
}
