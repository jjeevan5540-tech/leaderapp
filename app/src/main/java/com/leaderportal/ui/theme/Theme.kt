package com.leaderportal.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = BrandGold,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA16207),
    onPrimaryContainer = Color(0xFFFEF08A),
    secondary = BrandYellowAccent,
    onSecondary = BrandNavy,
    background = Color(0xFF081230),
    surface = Color(0xFF0F1F4D),
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF1F5F9)
)

private val LightColorScheme = lightColorScheme(
    primary = BrandGold,          // #CA8A04: Topbar, table header, View Details, Add button, modal header
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFEF08A),
    onPrimaryContainer = BrandNavy,
    secondary = BrandNavy,        // #0F1F4D: Headings, metric numbers, icons, avatars, toast bg
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = BrandNavy,
    tertiary = BrandYellowAccent, // #F5C542: Shield chip, counters, PA avatar letter
    onTertiary = BrandNavy,
    background = NeutralBackground,
    surface = SurfaceWhite,
    onBackground = BrandNavy,
    onSurface = BrandNavy,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = NeutralMedium,
    outline = SurfaceBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
