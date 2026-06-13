package com.phonepvr.friends.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.phonepvr.friends.domain.model.CallType
import com.phonepvr.friends.domain.model.ThemeMode

/**
 * Semantic colours that sit alongside the M3 [androidx.compose.material3.ColorScheme]
 * but have no role in it — currently just the call-accept accent. Read via
 * [LocalCallColors]; provided by [FriendsTheme] so they track light/dark.
 */
@Immutable
data class CallColors(
    val accept: Color,
    val onAccept: Color,
    /** Call-log direction accents: blue outgoing, green incoming, red missed. */
    val incoming: Color,
    val outgoing: Color,
    val missed: Color,
)

private val LightCallColors = CallColors(
    accept = LightCallAccept,
    onAccept = LightOnCallAccept,
    incoming = LightCallIncoming,
    outgoing = LightCallOutgoing,
    missed = LightCallMissed,
)
private val DarkCallColors = CallColors(
    accept = DarkCallAccept,
    onAccept = DarkOnCallAccept,
    incoming = DarkCallIncoming,
    outgoing = DarkCallOutgoing,
    missed = DarkCallMissed,
)

val LocalCallColors = staticCompositionLocalOf { LightCallColors }

/**
 * The fixed accent for a call [type]: blue outgoing, green incoming, red for
 * both missed and rejected. Reads [LocalCallColors], so it tracks light/dark
 * and stays stable under Material You. Single source of truth for the call-log
 * colour coding across the dialer, call history and person timeline.
 */
@Composable
fun callColor(type: CallType): Color {
    val colors = LocalCallColors.current
    return when (type) {
        CallType.INCOMING -> colors.incoming
        CallType.OUTGOING -> colors.outgoing
        CallType.MISSED, CallType.REJECTED -> colors.missed
    }
}

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
)

@Composable
fun FriendsTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    /**
     * When true on Android 12+, the system's wallpaper-derived palette wins.
     * Default is false so the app ships with its intended warm vibe; the
     * Settings → Appearance toggle flips this on for users who prefer their
     * wallpaper colours.
     */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(
        LocalCallColors provides if (darkTheme) DarkCallColors else LightCallColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = FriendsShapes,
            content = content,
        )
    }
}
