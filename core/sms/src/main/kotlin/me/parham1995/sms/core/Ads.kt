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
 * Some senders leave the marker out. Those the user names are ad senders
 * ([AdRule]), and everything they send is an ad.
 *
 * Bulk lines put the marker on everything they send, one-time codes included,
 * so a message with a code (by [OneTimeCodes]) is never an ad, from any sender.
 */
object Ads {
    private val MARKER =
        Regex("""لغو\s*[:=]?\s*(?:ارسال\s*)?(?:111|11|اا)(?![0-9])|(?<![0-9])11\s*لغو""")

    fun isAd(
        body: String,
        fromAdSender: Boolean = false,
    ): Boolean =
        (fromAdSender || MARKER.containsMatchIn(OneTimeCodes.asciiDigits(body))) && OneTimeCodes.find(body) == null

    /**
     * How an ad sender is stored and compared: a number in its canonical
     * spelling, a sender name by [Addresses.nameKey], so `MCI Academy` and
     * `MCI_ACADEMY` are one sender.
     */
    fun senderKey(
        address: String,
        callingCode: String,
    ): String =
        if (Addresses.isPhoneNumber(address)) {
            Addresses.normalize(address, callingCode)
        } else {
            Addresses.nameKey(address)
        }
}

/** [Ads] with the user's ad senders, by [Ads.senderKey]. */
data class AdRule(
    val senders: Set<String> = emptySet(),
    val callingCode: String = "98",
) {
    fun isAdSender(address: String): Boolean = Ads.senderKey(address, callingCode) in senders

    fun isAd(
        address: String,
        body: String,
    ): Boolean = Ads.isAd(body, fromAdSender = senders.isNotEmpty() && isAdSender(address))
}
