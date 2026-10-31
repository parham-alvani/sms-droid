package me.parham1995.sms.core

/**
 * What a blocked entry is, and its one canonical spelling.
 *
 * An entry is a sender, stored as [Ads.senderKey] stores one -- a number in
 * its canonical spelling, a sender name by [Addresses.nameKey] -- or a range
 * of numbers: digits ending in `*`, as `+98942*` or `0942*`, which takes every
 * number that starts with them. A sender name has no range; `HAMRAH_AVAL*` is
 * the sender `hamrahaval`.
 */
object Blocking {
    private const val WILDCARD = '*'

    /** [text] as it is stored: a range keeps its `*`, anything else is [Ads.senderKey]. */
    fun entry(
        text: String,
        callingCode: String,
    ): String {
        val trimmed = text.trim()
        val stem = trimmed.removeSuffix(WILDCARD.toString()).trim()
        return if (isRange(trimmed)) {
            rangePrefix(stem, callingCode) + WILDCARD
        } else {
            Ads.senderKey(stem, callingCode)
        }
    }

    /** Digits ending in `*`; at least one digit, so a lone `*` blocks nothing. */
    fun isRange(entry: String): Boolean {
        val trimmed = entry.trim()
        return trimmed.endsWith(WILDCARD) && Addresses.isPhoneNumber(trimmed.dropLast(1))
    }

    /**
     * A range's digits spelled the way [Addresses.normalize] spells the start
     * of a number: `0942` becomes `+98942` and `00` becomes `+`. A prefix is
     * too short for normalize's own national rule, so it is applied here.
     */
    internal fun rangePrefix(
        stem: String,
        callingCode: String,
    ): String {
        val digits = stem.filter { it.isDigit() || it == '+' }
        return when {
            digits.startsWith("+") -> digits
            digits.startsWith("00") -> "+" + digits.removePrefix("00")
            digits.startsWith("0") && callingCode.isNotBlank() -> "+" + callingCode + digits.removePrefix("0")
            else -> digits
        }
    }
}

/**
 * The user's blocked entries, by [Blocking.entry]. Unlike [AdRule], a block
 * has no exception: a one-time code from a blocked sender is blocked too.
 *
 * Calls are tested by the same rule. A call never comes from a sender name,
 * so a name only ever stops messages.
 */
data class BlockRule(
    val entries: Set<String> = emptySet(),
    val callingCode: String = "98",
) {
    private val senders = entries.filterNotTo(HashSet(), Blocking::isRange)
    private val ranges = entries.filter(Blocking::isRange).map { it.trim().dropLast(1) }

    fun isBlocked(address: String): Boolean {
        if (entries.isEmpty() || address.isBlank()) return false
        if (Ads.senderKey(address, callingCode) in senders) return true
        if (ranges.isEmpty() || !Addresses.isPhoneNumber(address)) return false
        val number = Addresses.normalize(address, callingCode)
        return ranges.any(number::startsWith)
    }

    /** The entry that blocks exactly [address], as the menus add and remove it. */
    fun entryFor(address: String): String = Blocking.entry(address, callingCode)
}
