package net.dkly.myflashlight.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Amber80,
    secondary = Steel80,
    tertiary = Mint80,
    background = Color(0xFF111318),
    surface = Color(0xFF1A1D23),
    surfaceVariant = Color(0xFF252A32),
    onPrimary = Color(0xFF2F1B00),
    onBackground = Color(0xFFF4F0E8),
    onSurface = Color(0xFFF4F0E8),
    onSurfaceVariant = Color(0xFFC9CDD4)
)

private val LightColorScheme = lightColorScheme(
    primary = Amber40,
    secondary = Steel40,
    tertiary = Mint40,
    background = Color(0xFFFAF7F0),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8EDF2),
    onPrimary = Color.White,
    onBackground = Color(0xFF181A1F),
    onSurface = Color(0xFF181A1F),
    onSurfaceVariant = Color(0xFF5C6470)

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun MyFlashlightTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
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
