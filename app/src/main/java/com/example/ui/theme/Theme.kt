package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val JuthoorLightColorScheme = lightColorScheme(
    primary = JuthoorEmerald,
    onPrimary = JuthoorOnEmerald,
    primaryContainer = JuthoorEmeraldContainer,
    onPrimaryContainer = JuthoorOnEmeraldContainer,
    secondary = JuthoorBurgundy,
    onSecondary = JuthoorOnBurgundy,
    secondaryContainer = JuthoorBurgundyContainer,
    onSecondaryContainer = JuthoorOnBurgundyContainer,
    tertiary = JuthoorHeritageGold,
    onTertiary = JuthoorOnHeritageGold,
    tertiaryContainer = JuthoorGoldContainer,
    onTertiaryContainer = JuthoorOnGoldContainer,
    background = JuthoorWhiteBackground,
    onBackground = JuthoorOnSurfaceLight,
    surface = JuthoorWhiteSurface,
    onSurface = JuthoorOnSurfaceLight,
    surfaceVariant = JuthoorWhiteSurfaceVariant,
    onSurfaceVariant = JuthoorOnSurfaceVariantLight,
    outline = JuthoorOutlineLight,
    outlineVariant = JuthoorOutlineLight,
    error = JuthoorErrorLight,
)

private val JuthoorDarkColorScheme = darkColorScheme(
    primary = JuthoorDarkEmeraldPrimary,
    onPrimary = JuthoorDarkOnPrimary,
    primaryContainer = JuthoorDarkPrimaryContainer,
    onPrimaryContainer = JuthoorDarkOnPrimaryContainer,
    secondary = JuthoorDarkBurgundySecondary,
    onSecondary = JuthoorDarkOnSecondary,
    secondaryContainer = JuthoorDarkSecondaryContainer,
    onSecondaryContainer = JuthoorDarkOnSecondaryContainer,
    tertiary = JuthoorDarkGoldTertiary,
    onTertiary = JuthoorDarkOnTertiary,
    tertiaryContainer = JuthoorDarkGoldContainer,
    onTertiaryContainer = JuthoorDarkOnGoldContainer,
    background = JuthoorDarkBackground,
    onBackground = JuthoorOnSurfaceDark,
    surface = JuthoorDarkSurface,
    onSurface = JuthoorOnSurfaceDark,
    surfaceVariant = JuthoorDarkSurfaceVariant,
    onSurfaceVariant = JuthoorOnSurfaceVariantDark,
    outline = JuthoorOutlineDark,
    outlineVariant = JuthoorOutlineDark,
    error = JuthoorErrorDark,
)

val JuthoorShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun JuthoorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) JuthoorDarkColorScheme else JuthoorLightColorScheme

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = JuthoorTypography,
            shapes = JuthoorShapes,
            content = content,
        )
    }
}
