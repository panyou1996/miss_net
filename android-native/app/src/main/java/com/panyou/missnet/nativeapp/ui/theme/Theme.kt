package com.panyou.missnet.nativeapp.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import com.panyou.missnet.nativeapp.core.model.AppSettings

private val MissNetDarkScheme = darkColorScheme(
    primary = Signal,
    onPrimary = Foam,
    secondary = SignalSoft,
    onSecondary = InkBlack,
    tertiary = Color(0xFF5BE2BC),
    background = InkBlack,
    onBackground = Foam,
    surface = SurfaceDark,
    onSurface = Foam,
    surfaceVariant = SurfaceRaised,
    outline = Divider,
)

@Composable
fun MissNetTheme(
    settings: AppSettings,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        settings.useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicDarkColorScheme(context).copy(
            primary = Signal,
            secondary = lerp(dynamicDarkColorScheme(context).secondary, SignalSoft, 0.35f),
            background = InkBlack,
            surface = SurfaceDark,
            surfaceVariant = SurfaceRaised,
        )

        else -> MissNetDarkScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MissNetTypography,
        shapes = MissNetShapes,
        content = content,
    )
}

val ScreenGradient: Brush
    @Composable get() = Brush.verticalGradient(
        listOf(
            InkBlack,
            Graphite,
            InkBlack,
        ),
    )
