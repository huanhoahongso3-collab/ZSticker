package dhp.thl.tpl.ndv.ui.theme

import androidx.annotation.ColorInt
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * Complete Material 3 semantic palette used uniformly across Android Views, Dialogs,
 * and Jetpack Compose surfaces whether Dynamic Color is ON (wallpaper seed) or OFF (#F57C00 seed).
 */
data class ExpressivePalette(
    val isDark: Boolean,
    @ColorInt val primary: Int,
    @ColorInt val onPrimary: Int,
    @ColorInt val primaryContainer: Int,
    @ColorInt val onPrimaryContainer: Int,
    @ColorInt val secondary: Int,
    @ColorInt val onSecondary: Int,
    @ColorInt val secondaryContainer: Int,
    @ColorInt val onSecondaryContainer: Int,
    @ColorInt val tertiary: Int,
    @ColorInt val onTertiary: Int,
    @ColorInt val tertiaryContainer: Int,
    @ColorInt val onTertiaryContainer: Int,
    @ColorInt val surface: Int,
    @ColorInt val surfaceContainerLowest: Int,
    @ColorInt val surfaceContainerLow: Int,
    @ColorInt val surfaceContainer: Int,
    @ColorInt val surfaceContainerHigh: Int,
    @ColorInt val surfaceContainerHighest: Int,
    @ColorInt val onSurface: Int,
    @ColorInt val onSurfaceVariant: Int,
    @ColorInt val outline: Int,
    @ColorInt val outlineVariant: Int,
    @ColorInt val error: Int,
    @ColorInt val onError: Int,
    @ColorInt val errorContainer: Int,
    @ColorInt val onErrorContainer: Int
) {
    fun toComposeColorScheme(): ColorScheme {
        return if (isDark) {
            darkColorScheme(
                primary = ComposeColor(primary),
                onPrimary = ComposeColor(onPrimary),
                primaryContainer = ComposeColor(primaryContainer),
                onPrimaryContainer = ComposeColor(onPrimaryContainer),
                secondary = ComposeColor(secondary),
                onSecondary = ComposeColor(onSecondary),
                secondaryContainer = ComposeColor(secondaryContainer),
                onSecondaryContainer = ComposeColor(onSecondaryContainer),
                tertiary = ComposeColor(tertiary),
                onTertiary = ComposeColor(onTertiary),
                tertiaryContainer = ComposeColor(tertiaryContainer),
                onTertiaryContainer = ComposeColor(onTertiaryContainer),
                background = ComposeColor(surface),
                onBackground = ComposeColor(onSurface),
                surface = ComposeColor(surface),
                onSurface = ComposeColor(onSurface),
                surfaceVariant = ComposeColor(surfaceContainerHighest),
                onSurfaceVariant = ComposeColor(onSurfaceVariant),
                surfaceContainerLowest = ComposeColor(surfaceContainerLowest),
                surfaceContainerLow = ComposeColor(surfaceContainerLow),
                surfaceContainer = ComposeColor(surfaceContainer),
                surfaceContainerHigh = ComposeColor(surfaceContainerHigh),
                surfaceContainerHighest = ComposeColor(surfaceContainerHighest),
                outline = ComposeColor(outline),
                outlineVariant = ComposeColor(outlineVariant),
                error = ComposeColor(error),
                onError = ComposeColor(onError),
                errorContainer = ComposeColor(errorContainer),
                onErrorContainer = ComposeColor(onErrorContainer)
            )
        } else {
            lightColorScheme(
                primary = ComposeColor(primary),
                onPrimary = ComposeColor(onPrimary),
                primaryContainer = ComposeColor(primaryContainer),
                onPrimaryContainer = ComposeColor(onPrimaryContainer),
                secondary = ComposeColor(secondary),
                onSecondary = ComposeColor(onSecondary),
                secondaryContainer = ComposeColor(secondaryContainer),
                onSecondaryContainer = ComposeColor(onSecondaryContainer),
                tertiary = ComposeColor(tertiary),
                onTertiary = ComposeColor(onTertiary),
                tertiaryContainer = ComposeColor(tertiaryContainer),
                onTertiaryContainer = ComposeColor(onTertiaryContainer),
                background = ComposeColor(surface),
                onBackground = ComposeColor(onSurface),
                surface = ComposeColor(surface),
                onSurface = ComposeColor(onSurface),
                surfaceVariant = ComposeColor(surfaceContainerHighest),
                onSurfaceVariant = ComposeColor(onSurfaceVariant),
                surfaceContainerLowest = ComposeColor(surfaceContainerLowest),
                surfaceContainerLow = ComposeColor(surfaceContainerLow),
                surfaceContainer = ComposeColor(surfaceContainer),
                surfaceContainerHigh = ComposeColor(surfaceContainerHigh),
                surfaceContainerHighest = ComposeColor(surfaceContainerHighest),
                outline = ComposeColor(outline),
                outlineVariant = ComposeColor(outlineVariant),
                error = ComposeColor(error),
                onError = ComposeColor(onError),
                errorContainer = ComposeColor(errorContainer),
                onErrorContainer = ComposeColor(onErrorContainer)
            )
        }
    }
}
