package com.digitalku.roost.remote.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.digitalku.roost.remote.store.ThemeChoice

/** The icon's two oranges, `#FFA43A` and `#E85606`, as the primary colour. */
private val Dark = darkColorScheme(
    primary = Color(0xFFFFA43A),
    onPrimary = Color(0xFF3B1C00),
    primaryContainer = Color(0xFF7A3300),
    onPrimaryContainer = Color(0xFFFFDBC6),
    secondary = Color(0xFFE6BEA6),
)

private val Light = lightColorScheme(
    primary = Color(0xFFB84700),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBC6),
    onPrimaryContainer = Color(0xFF3B1C00),
    secondary = Color(0xFF765848),
)

/** Whether [choice] draws dark now: the system's answer when it is [ThemeChoice.System]. */
@Composable
fun isDark(choice: ThemeChoice): Boolean = when (choice) {
    ThemeChoice.System -> isSystemInDarkTheme()
    ThemeChoice.Light -> false
    ThemeChoice.Dark -> true
}

@Composable
fun RoostTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
