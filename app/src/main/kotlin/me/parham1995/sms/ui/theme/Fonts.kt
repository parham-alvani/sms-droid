package me.parham1995.sms.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import me.parham1995.sms.R

/**
 * Vazirmatn, for Persian messages, as in notes-droid.
 *
 * Android draws Persian in Noto Naskh Arabic, a calligraphic face; Vazirmatn
 * is a contemporary one, and a message set in it reads the way the sender
 * expects. Applied only to text with Persian in it, so an English message
 * keeps the interface's face.
 *
 * SIL Open Font License 1.1; the text is in `licenses/Vazirmatn-OFL.txt`.
 */
internal val Vazirmatn =
    FontFamily(
        Font(R.font.vazirmatn_regular, FontWeight.Normal),
        Font(R.font.vazirmatn_bold, FontWeight.Bold),
    )

/** Vazirmatn for text with Arabic-script letters in it, otherwise the theme's own. */
@Composable
fun fontFor(text: String): FontFamily? = if (text.any { it in '؀'..'ۿ' }) Vazirmatn else null
