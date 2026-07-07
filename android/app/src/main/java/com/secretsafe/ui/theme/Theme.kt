package com.secretsafe.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryGold,
    secondary = SecondaryGold,
    background = DarkBg,
    surface = DarkCard,
    onBackground = TextLight,
    onSurface = TextLight,
    error = AlertRed
)

@Composable
fun SecretSafeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
