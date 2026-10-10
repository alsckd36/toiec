package com.toiec.vocabulary.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

// A fixed light palette preserves the requested warm paper and mint appearance on every device.
private val PaperColors = lightColorScheme(
    primary = Mint, onPrimary = Paper, primaryContainer = MintWash, onPrimaryContainer = Ink,
    secondary = Mint, onSecondary = Paper, secondaryContainer = MintWash, onSecondaryContainer = Ink,
    background = Paper, onBackground = Ink, surface = Paper, onSurface = Ink,
    surfaceVariant = MintWash, onSurfaceVariant = MutedInk,
    surfaceContainer = Paper, surfaceContainerLow = Paper, surfaceContainerHigh = MintWash,
    outline = MutedInk, outlineVariant = Rule,
    error = WrongInk, onError = Paper, errorContainer = WrongWash, onErrorContainer = WrongInk,
)

@Composable
fun Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PaperColors, typography = Typography,
        shapes = Shapes(small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(20.dp)),
        content = content,
    )
}
