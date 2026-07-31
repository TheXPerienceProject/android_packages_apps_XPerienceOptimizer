// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 XPerience Project

package mx.xperience.optimizer.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Blue40,
    background = DarkSurface,
    surface = DarkCard,
    onPrimary = Color.White,
    onBackground = DarkOnSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = BlueGrey80,
    outline = Grey40
)

private val LightColorScheme = lightColorScheme(
    primary = Blue40,
    background = LightGrey,
    surface = Color.White,
    onPrimary = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black,
    surfaceVariant = Grey20,
    outline = Color.Gray
)

@Composable
fun XPerienceOptimizerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
