package me.parham1995.sms.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The palette from `1995parham/naz.vim`, as notes-droid has it.
 *
 * Values are taken verbatim from `colors/naz.lua` so the three apps and the
 * editor agree. Nothing here is invented or adjusted.
 */
internal object Naz {
    val White = Color(0xFFF5F5F0)
    val Black = Color(0xFF323232)
    val LightBlack = Color(0xFF3A3A35)
    val DarkBlack = Color(0xFF282826)
    val Grey = Color(0xFFA8A8A0)
    val LightGrey = Color(0xFF707080)
    val DarkGrey = Color(0xFF4A4A4A)
    val WarmGrey = Color(0xFFB09070)

    val Yellow = Color(0xFFF0E890)
    val Orange = Color(0xFFFFB040)
    val Blue = Color(0xFF40D0FF)
    val Aqua = Color(0xFF80E5FF)
    val Red = Color(0xFFFF5070)

    /** [Red] lightened to 4.8:1 on [Black], for error text at body size. */
    val ErrorText = Color(0xFFFF7088)
}

/**
 * The light scheme's palette, as notes-droid has it.
 *
 * Not naz -- naz has no light variant. These are its accents darkened until
 * they read on paper.
 */
internal object Daylight {
    val Paper = Color(0xFFFAF9F5)
    val Panel = Color(0xFFEFEDE6)
    val Ink = Color(0xFF2A2A28)
    val FadedInk = Color(0xFF6A6A62)
    val Rule = Color(0xFFD8D5CC)
    val Orange = Color(0xFFA85200)
    val Red = Color(0xFFC01030)
    val Blue = Color(0xFF0070A0)
    val Amber = Color(0xFF8A6A00)
}
