package me.parham1995.sms.core

/**
 * Finds the one-time code in a message, so a notification can offer to copy it.
 *
 * A code is 4 to 8 digits that come after a word that announces one -- `code`,
 * `OTP`, `password`, or the Persian `کد`, `رمز`, `رمز پویا` -- with no more than
 * a few characters between them. Without that word a number is not offered:
 * an amount, a balance or a card's last digits would be copied as often as a
 * code.
 *
 * Persian and Arabic-Indic digits are read as digits and returned in ASCII,
 * which is what every form accepts.
 */
object OneTimeCodes {
    private val KEYWORD =
        Regex(
            """(?i)(code|otp|passcode|password|pin|کد|رمز(?:\s*پویا)?|رمز\s*یکبار\s*مصرف)""",
        )
    private val CODE = Regex("""(?<![0-9])[0-9]{4,8}(?![0-9])""")
    private const val MAX_GAP = 24

    fun find(body: String): String? {
        val text = asciiDigits(body)
        for (keyword in KEYWORD.findAll(text)) {
            val after = text.substring(keyword.range.last + 1).take(MAX_GAP + CODE_MAX)
            val code = CODE.find(after) ?: continue
            if (code.range.first <= MAX_GAP) return code.value
        }
        return null
    }

    private const val CODE_MAX = 8

    internal fun asciiDigits(text: String): String =
        buildString(text.length) {
            for (c in text) {
                append(
                    when (c) {
                        in '۰'..'۹' -> '0' + (c - '۰')
                        in '٠'..'٩' -> '0' + (c - '٠')
                        else -> c
                    },
                )
            }
        }
}
