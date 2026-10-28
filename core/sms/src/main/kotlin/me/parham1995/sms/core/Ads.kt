package me.parham1995.sms.core

/**
 * Tells an advertisement from a message worth keeping.
 *
 * Iran's regulator makes every promotional SMS carry the opt-out instruction
 * `لغو11` ("send 11 to cancel"), so that is the test. Senders write it many
 * ways: `لغو11`, `لغو ۱۱`, `لغو: 11`, `لغو=11`, `لغو: ارسال 11 به …`, `لغو 111`, a
 * right-to-left `11لغو`, and `لغواا` with two alefs for the ones. A word that
 * only means "cancelled", as in `سفارش شما لغو شد`, is not the marker.
 *
 * Bulk lines put the marker on everything they send, one-time codes included,
 * so a message with a code (by [OneTimeCodes]) is never an ad.
 */
object Ads {
    private val MARKER =
        Regex("""لغو\s*[:=]?\s*(?:ارسال\s*)?(?:111|11|اا)(?![0-9])|(?<![0-9])11\s*لغو""")

    fun isAd(body: String): Boolean =
        MARKER.containsMatchIn(OneTimeCodes.asciiDigits(body)) && OneTimeCodes.find(body) == null
}
