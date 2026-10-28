package me.parham1995.sms.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import me.parham1995.sms.data.ThemeChoice

/**
 * notes-droid's naz scheme: the roles follow the highlight groups naz
 * assigns, so anything to act on is orange, headings and highlights yellow,
 * secondary text grey. A container is its accent washed into the background,
 * so a selected conversation reads as the colour of its role.
 */
private val NazScheme =
    darkColorScheme(
        background = Naz.Black,
        onBackground = Naz.White,
        surface = Naz.Black,
        onSurface = Naz.White,
        surfaceVariant = Naz.DarkBlack,
        onSurfaceVariant = Naz.Grey,
        surfaceContainer = Naz.LightBlack,
        surfaceContainerHigh = lerp(Naz.LightBlack, Naz.White, 0.04f),
        surfaceContainerHighest = lerp(Naz.LightBlack, Naz.White, 0.08f),
        surfaceContainerLow = Naz.DarkBlack,
        surfaceContainerLowest = Naz.DarkBlack,
        primary = Naz.Orange,
        onPrimary = Naz.Black,
        primaryContainer = Naz.Orange.wash(Naz.Black),
        onPrimaryContainer = Naz.Orange,
        secondary = Naz.Blue,
        onSecondary = Naz.Black,
        secondaryContainer = Naz.Blue.wash(Naz.Black),
        onSecondaryContainer = Naz.Aqua,
        tertiary = Naz.Yellow,
        onTertiary = Naz.Black,
        tertiaryContainer = Naz.Yellow,
        onTertiaryContainer = Naz.Black,
        error = Naz.ErrorText,
        onError = Naz.Black,
        errorContainer = Naz.Red.wash(Naz.Black),
        onErrorContainer = Naz.ErrorText,
        outline = Naz.LightGrey,
        outlineVariant = Naz.DarkGrey,
        inverseSurface = Naz.White,
        inverseOnSurface = Naz.Black,
        inversePrimary = Naz.WarmGrey,
        scrim = Naz.DarkBlack,
    )

/** naz's accents on paper, as notes-droid's light scheme has them. */
private val DaylightScheme =
    lightColorScheme(
        background = Daylight.Paper,
        onBackground = Daylight.Ink,
        surface = Daylight.Paper,
        onSurface = Daylight.Ink,
        surfaceVariant = Daylight.Panel,
        onSurfaceVariant = Daylight.FadedInk,
        surfaceContainer = Daylight.Panel,
        surfaceContainerHigh = Daylight.Panel,
        surfaceContainerHighest = Daylight.Panel,
        surfaceContainerLow = Daylight.Paper,
        surfaceContainerLowest = Daylight.Paper,
        primary = Daylight.Orange,
        onPrimary = Daylight.Paper,
        primaryContainer = Daylight.Orange.wash(Daylight.Paper, PAPER_WASH),
        onPrimaryContainer = Daylight.Orange,
        secondary = Daylight.Blue,
        onSecondary = Daylight.Paper,
        secondaryContainer = Daylight.Blue.wash(Daylight.Paper, PAPER_WASH),
        onSecondaryContainer = Daylight.Blue,
        tertiary = Daylight.Amber,
        onTertiary = Daylight.Paper,
        tertiaryContainer = Daylight.Amber,
        onTertiaryContainer = Daylight.Ink,
        error = Daylight.Red,
        onError = Daylight.Paper,
        errorContainer = Daylight.Red.wash(Daylight.Paper, PAPER_WASH),
        onErrorContainer = Daylight.Red,
        outline = Daylight.FadedInk,
        outlineVariant = Daylight.Rule,
        inverseSurface = Naz.Black,
        inverseOnSurface = Naz.White,
        inversePrimary = Daylight.Orange,
        scrim = Daylight.Rule,
    )

/** An accent mixed into a background, for the container behind that accent's own text. */
private fun Color.wash(
    background: Color,
    amount: Float = INK_WASH,
): Color = lerp(background, this, amount)

private const val INK_WASH = 0.24f

/** Paper takes a tint more readily than dark grey does. */
private const val PAPER_WASH = 0.14f

/** notes-droid's corners, rounder than Material's defaults. */
private val NazShapes =
    Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(32.dp),
    )

/** Whether this choice comes out dark, here and now. */
@Composable
fun ThemeChoice.isDark(): Boolean =
    when (this) {
        ThemeChoice.DARK -> true
        ThemeChoice.LIGHT -> false
        ThemeChoice.SYSTEM, ThemeChoice.WALLPAPER -> isSystemInDarkTheme()
    }

/**
 * Material 3 Expressive, as notes-droid wears it: naz or Daylight with its
 * shapes, or the wallpaper's colours for [ThemeChoice.WALLPAPER], all with
 * the spring-based motion scheme.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SmsTheme(
    theme: ThemeChoice = ThemeChoice.DARK,
    content: @Composable () -> Unit,
) {
    val dark = theme.isDark()
    val context = LocalContext.current
    val scheme =
        when {
            theme == ThemeChoice.WALLPAPER ->
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            dark -> NazScheme
            else -> DaylightScheme
        }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        shapes = if (theme == ThemeChoice.WALLPAPER) Shapes() else NazShapes,
        content = content,
    )
}
