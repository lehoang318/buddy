package com.example.buddy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VintageColorScheme = lightColorScheme(
    primary        = SendButton,
    onPrimary      = Color.White,
    secondary      = UserBubble,
    onSecondary    = Color.White,
    background     = VintageBackground,
    onBackground   = TextColor,
    surface        = VintageBackground,
    onSurface      = TextColor,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline        = Outline
)

@Composable
fun BuddyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VintageColorScheme,
        typography   = Typography(),
        content      = content
    )
}
