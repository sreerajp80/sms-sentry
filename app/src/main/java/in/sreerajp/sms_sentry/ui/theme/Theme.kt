package `in`.sreerajp.sms_sentry.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

enum class ThemeStyle {
    LAVENDER, SAGE, SLATE, BLUE, HIGH_DENSITY
}

/** True when the active color scheme is a dark one (used to pick category/status colours). */
@Composable
fun isDarkScheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/**
 * Message category colours in chart/legend order: Personal, Promotions, Others, Spam.
 */
@Composable
fun categoryColors(): List<Color> = if (isDarkScheme()) {
    listOf(CategoryPersonalDark, CategoryPromotionsDark, CategoryOthersDark, CategorySpamDark)
} else {
    listOf(CategoryPersonalLight, CategoryPromotionsLight, CategoryOthersLight, CategorySpamLight)
}

/** Positive / "credit" / success colour (green), theme-aware. */
@Composable
fun goodColor(): Color = if (isDarkScheme()) GoodDark else GoodLight

/** Soft positive background, theme-aware. */
@Composable
fun goodSoftColor(): Color = if (isDarkScheme()) GoodSoftDark else GoodSoftLight

/** Negative / "debit" / spam colour (red), theme-aware. */
@Composable
fun spamColor(): Color = if (isDarkScheme()) SpamStatusDark else SpamStatusLight

/** Soft negative background, theme-aware. */
@Composable
fun spamSoftColor(): Color = if (isDarkScheme()) SpamSoftDark else SpamSoftLight

/** Category colour for a single message category string. */
@Composable
fun categoryColor(category: String): Color {
    val c = categoryColors()
    return when (category) {
        "Personal" -> c[0]
        "Promotions" -> c[1]
        "Spam" -> c[3]
        else -> c[2] // Others (plus any legacy Accounts/Reminder/Services value)
    }
}

// Light color schemes
private val LavenderLight = lightColorScheme(
    primary = LavenderPrimaryLight,
    onPrimary = LavenderOnPrimaryLight,
    primaryContainer = LavenderPrimaryContainerLight,
    onPrimaryContainer = LavenderOnPrimaryContainerLight,
    secondary = LavenderOnPrimaryContainerLight,
    onSecondary = LavenderOnPrimaryLight,
    secondaryContainer = LavenderPrimaryContainerLight,
    onSecondaryContainer = LavenderOnPrimaryContainerLight,
    background = LavenderBackgroundLight,
    onBackground = LavenderTextLight,
    surface = LavenderSurfaceLight,
    onSurface = LavenderTextLight,
    surfaceVariant = LavenderInsetLight,
    onSurfaceVariant = LavenderTextMuteLight,
    outline = LavenderBorderLight,
    outlineVariant = LavenderBorderLight,
    surfaceTint = LavenderPrimaryLight,
    error = SpamStatusLight,
    onError = LavenderOnPrimaryLight,
    errorContainer = SpamSoftLight,
    onErrorContainer = SpamStatusLight
)

private val SageLight = lightColorScheme(
    primary = SagePrimaryLight,
    onPrimary = SageOnPrimaryLight,
    primaryContainer = SagePrimaryContainerLight,
    onPrimaryContainer = SageOnPrimaryContainerLight,
    secondary = SageOnPrimaryContainerLight,
    onSecondary = SageOnPrimaryLight,
    secondaryContainer = SagePrimaryContainerLight,
    onSecondaryContainer = SageOnPrimaryContainerLight,
    background = SageBackgroundLight,
    onBackground = SageTextLight,
    surface = SageSurfaceLight,
    onSurface = SageTextLight,
    surfaceVariant = SageInsetLight,
    onSurfaceVariant = SageTextMuteLight,
    outline = SageBorderLight,
    outlineVariant = SageBorderLight,
    surfaceTint = SagePrimaryLight,
    error = SpamStatusLight,
    onError = SageOnPrimaryLight,
    errorContainer = SpamSoftLight,
    onErrorContainer = SpamStatusLight
)

private val SlateLight = lightColorScheme(
    primary = SlatePrimaryLight,
    onPrimary = SlateOnPrimaryLight,
    primaryContainer = SlatePrimaryContainerLight,
    onPrimaryContainer = SlateOnPrimaryContainerLight,
    secondary = SlateOnPrimaryContainerLight,
    onSecondary = SlateOnPrimaryLight,
    secondaryContainer = SlatePrimaryContainerLight,
    onSecondaryContainer = SlateOnPrimaryContainerLight,
    background = SlateBackgroundLight,
    onBackground = SlateTextLight,
    surface = SlateSurfaceLight,
    onSurface = SlateTextLight,
    surfaceVariant = SlateInsetLight,
    onSurfaceVariant = SlateTextMuteLight,
    outline = SlateBorderLight,
    outlineVariant = SlateBorderLight,
    surfaceTint = SlatePrimaryLight,
    error = SpamStatusLight,
    onError = SlateOnPrimaryLight,
    errorContainer = SpamSoftLight,
    onErrorContainer = SpamStatusLight
)

private val BlueLight = lightColorScheme(
    primary = BluePrimaryLight,
    onPrimary = BlueOnPrimaryLight,
    primaryContainer = BluePrimaryContainerLight,
    onPrimaryContainer = BlueOnPrimaryContainerLight,
    secondary = BlueOnPrimaryContainerLight,
    onSecondary = BlueOnPrimaryLight,
    secondaryContainer = BluePrimaryContainerLight,
    onSecondaryContainer = BlueOnPrimaryContainerLight,
    background = BlueBackgroundLight,
    onBackground = BlueTextLight,
    surface = BlueSurfaceLight,
    onSurface = BlueTextLight,
    surfaceVariant = BlueInsetLight,
    onSurfaceVariant = BlueTextMuteLight,
    outline = BlueBorderLight,
    outlineVariant = BlueBorderLight,
    surfaceTint = BluePrimaryLight,
    error = SpamStatusLight,
    onError = BlueOnPrimaryLight,
    errorContainer = SpamSoftLight,
    onErrorContainer = SpamStatusLight
)

private val HighDensityLight = lightColorScheme(
    primary = HighDensityPrimaryLight,
    onPrimary = HighDensityOnPrimaryLight,
    primaryContainer = HighDensityPrimaryContainerLight,
    onPrimaryContainer = HighDensityOnPrimaryContainerLight,
    secondary = HighDensityOnPrimaryContainerLight,
    onSecondary = HighDensityOnPrimaryLight,
    secondaryContainer = HighDensityPrimaryContainerLight,
    onSecondaryContainer = HighDensityOnPrimaryContainerLight,
    background = HighDensityBackgroundLight,
    onBackground = HighDensityTextLight,
    surface = HighDensitySurfaceLight,
    onSurface = HighDensityTextLight,
    surfaceVariant = HighDensityInsetLight,
    onSurfaceVariant = HighDensityTextMuteLight,
    outline = HighDensityBorderLight,
    outlineVariant = HighDensityBorderLight,
    surfaceTint = HighDensityPrimaryLight,
    error = SpamStatusLight,
    onError = HighDensityOnPrimaryLight,
    errorContainer = SpamSoftLight,
    onErrorContainer = SpamStatusLight
)

// Dark color schemes
private val LavenderDark = darkColorScheme(
    primary = LavenderPrimaryDark,
    onPrimary = LavenderOnPrimaryDark,
    primaryContainer = LavenderPrimaryContainerDark,
    onPrimaryContainer = LavenderOnPrimaryContainerDark,
    secondary = LavenderOnPrimaryContainerDark,
    onSecondary = LavenderOnPrimaryDark,
    secondaryContainer = LavenderPrimaryContainerDark,
    onSecondaryContainer = LavenderOnPrimaryContainerDark,
    background = LavenderBackgroundDark,
    onBackground = LavenderTextDark,
    surface = LavenderSurfaceDark,
    onSurface = LavenderTextDark,
    surfaceVariant = LavenderInsetDark,
    onSurfaceVariant = LavenderTextMuteDark,
    outline = LavenderBorderDark,
    outlineVariant = LavenderBorderDark,
    surfaceTint = LavenderPrimaryDark,
    error = SpamStatusDark,
    onError = LavenderOnPrimaryDark,
    errorContainer = SpamSoftDark,
    onErrorContainer = SpamStatusDark
)

private val SageDark = darkColorScheme(
    primary = SagePrimaryDark,
    onPrimary = SageOnPrimaryDark,
    primaryContainer = SagePrimaryContainerDark,
    onPrimaryContainer = SageOnPrimaryContainerDark,
    secondary = SageOnPrimaryContainerDark,
    onSecondary = SageOnPrimaryDark,
    secondaryContainer = SagePrimaryContainerDark,
    onSecondaryContainer = SageOnPrimaryContainerDark,
    background = SageBackgroundDark,
    onBackground = SageTextDark,
    surface = SageSurfaceDark,
    onSurface = SageTextDark,
    surfaceVariant = SageInsetDark,
    onSurfaceVariant = SageTextMuteDark,
    outline = SageBorderDark,
    outlineVariant = SageBorderDark,
    surfaceTint = SagePrimaryDark,
    error = SpamStatusDark,
    onError = SageOnPrimaryDark,
    errorContainer = SpamSoftDark,
    onErrorContainer = SpamStatusDark
)

private val SlateDark = darkColorScheme(
    primary = SlatePrimaryDark,
    onPrimary = SlateOnPrimaryDark,
    primaryContainer = SlatePrimaryContainerDark,
    onPrimaryContainer = SlateOnPrimaryContainerDark,
    secondary = SlateOnPrimaryContainerDark,
    onSecondary = SlateOnPrimaryDark,
    secondaryContainer = SlatePrimaryContainerDark,
    onSecondaryContainer = SlateOnPrimaryContainerDark,
    background = SlateBackgroundDark,
    onBackground = SlateTextDark,
    surface = SlateSurfaceDark,
    onSurface = SlateTextDark,
    surfaceVariant = SlateInsetDark,
    onSurfaceVariant = SlateTextMuteDark,
    outline = SlateBorderDark,
    outlineVariant = SlateBorderDark,
    surfaceTint = SlatePrimaryDark,
    error = SpamStatusDark,
    onError = SlateOnPrimaryDark,
    errorContainer = SpamSoftDark,
    onErrorContainer = SpamStatusDark
)

private val BlueDark = darkColorScheme(
    primary = BluePrimaryDark,
    onPrimary = BlueOnPrimaryDark,
    primaryContainer = BluePrimaryContainerDark,
    onPrimaryContainer = BlueOnPrimaryContainerDark,
    secondary = BlueOnPrimaryContainerDark,
    onSecondary = BlueOnPrimaryDark,
    secondaryContainer = BluePrimaryContainerDark,
    onSecondaryContainer = BlueOnPrimaryContainerDark,
    background = BlueBackgroundDark,
    onBackground = BlueTextDark,
    surface = BlueSurfaceDark,
    onSurface = BlueTextDark,
    surfaceVariant = BlueInsetDark,
    onSurfaceVariant = BlueTextMuteDark,
    outline = BlueBorderDark,
    outlineVariant = BlueBorderDark,
    surfaceTint = BluePrimaryDark,
    error = SpamStatusDark,
    onError = BlueOnPrimaryDark,
    errorContainer = SpamSoftDark,
    onErrorContainer = SpamStatusDark
)

private val HighDensityDark = darkColorScheme(
    primary = HighDensityPrimaryDark,
    onPrimary = HighDensityOnPrimaryDark,
    primaryContainer = HighDensityPrimaryContainerDark,
    onPrimaryContainer = HighDensityOnPrimaryContainerDark,
    secondary = HighDensityOnPrimaryContainerDark,
    onSecondary = HighDensityOnPrimaryDark,
    secondaryContainer = HighDensityPrimaryContainerDark,
    onSecondaryContainer = HighDensityOnPrimaryContainerDark,
    background = HighDensityBackgroundDark,
    onBackground = HighDensityTextDark,
    surface = HighDensitySurfaceDark,
    onSurface = HighDensityTextDark,
    surfaceVariant = HighDensityInsetDark,
    onSurfaceVariant = HighDensityTextMuteDark,
    outline = HighDensityBorderDark,
    outlineVariant = HighDensityBorderDark,
    surfaceTint = HighDensityPrimaryDark,
    error = SpamStatusDark,
    onError = HighDensityOnPrimaryDark,
    errorContainer = SpamSoftDark,
    onErrorContainer = SpamStatusDark
)

@Composable
fun MyApplicationTheme(
    themeStyle: ThemeStyle = ThemeStyle.HIGH_DENSITY,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeStyle) {
        ThemeStyle.LAVENDER -> if (darkTheme) LavenderDark else LavenderLight
        ThemeStyle.SAGE -> if (darkTheme) SageDark else SageLight
        ThemeStyle.SLATE -> if (darkTheme) SlateDark else SlateLight
        ThemeStyle.BLUE -> if (darkTheme) BlueDark else BlueLight
        ThemeStyle.HIGH_DENSITY -> if (darkTheme) HighDensityDark else HighDensityLight
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
