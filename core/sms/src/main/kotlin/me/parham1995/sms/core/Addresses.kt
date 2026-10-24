package me.parham1995.sms.core

/**
 * What an SMS address is, and the one canonical spelling of it.
 *
 * An address is either a phone number (`+989121234567`, `0912 123 4567`,
 * `500-047-47`) or a sender name that a bank or an operator registered
 * (`ResalatBank`, `HAMRAH_AVAL`, `MCI Club`). Android's own lookup only knows
 * the first kind: `PhoneLookup` reduces the address to digits, so a sender name
 * reaches the contacts database as nothing at all and never matches. That is
 * the bug that started this app, and it is why the two kinds are told apart
 * here and matched by different rules.
 */
object Addresses {
    private val PHONE = Regex("""^\+?[0-9 ()\-.]+$""")
    private val SEPARATORS = Regex("""[ ()\-.]""")

    /** Digits with an optional leading `+`, and the separators people type between them. */
    fun isPhoneNumber(address: String): Boolean {
        val trimmed = address.trim()
        return PHONE.matches(trimmed) && trimmed.any(Char::isDigit)
    }

    /**
     * The canonical spelling: for a number, the separators go and a local
     * number gets the country code, so `0912 123 4567` and `+989121234567`
     * are one address. A sender name is only trimmed; its case stays, because
     * it is shown.
     *
     * [callingCode] is the country's calling code without the `+` (`98` for
     * Iran). A number with a single leading `0` is a national number and
     * gets it; `00` is the international prefix and becomes `+`. Anything
     * else -- short codes, numbers saved without a `+` -- is left as it is,
     * because guessing would merge numbers that are different.
     */
    fun normalize(
        address: String,
        callingCode: String,
    ): String {
        val trimmed = address.trim()
        if (!isPhoneNumber(trimmed)) return trimmed
        val digits = trimmed.replace(SEPARATORS, "")
        return when {
            digits.startsWith("+") -> digits
            digits.startsWith("00") -> "+" + digits.removePrefix("00")
            digits.startsWith("0") && digits.length >= NATIONAL_MIN_LENGTH && callingCode.isNotBlank() ->
                "+" + callingCode + digits.removePrefix("0")
            else -> digits
        }
    }

    /**
     * The form two sender names are compared in: letters and digits only, in
     * lower case. `HAMRAH_AVAL`, `HAMRAH AVAL` and `Hamrah-Aval` are one
     * sender; operators are not consistent about it, and neither are the
     * contacts people save for them.
     */
    fun nameKey(address: String): String = address.filter(Char::isLetterOrDigit).lowercase()

    /** The digits alone, for comparing the ends of two numbers. */
    internal fun digits(address: String): String = address.filter(Char::isDigit)

    /** `0` and at least nine more digits: shorter numbers are service codes, not national numbers. */
    private const val NATIONAL_MIN_LENGTH = 10
}
