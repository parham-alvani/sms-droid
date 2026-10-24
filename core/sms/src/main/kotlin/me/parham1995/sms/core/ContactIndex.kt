package me.parham1995.sms.core

/**
 * Finds the contact behind an SMS address.
 *
 * Built once from every phone entry in the contacts database, and asked many
 * times: the conversation list asks once for each conversation. Three rules,
 * in order:
 *
 * 1. **Sender names** match a contact whose "number" is the same name, by
 *    [Addresses.nameKey]. This is what `PhoneLookup` cannot do.
 * 2. **Numbers** match exactly, after [Addresses.normalize].
 * 3. **Numbers** match by their last digits, the way Android's loose compare
 *    does, so `+989121234567` finds a contact saved as `989-121-234567` or
 *    `09121234567`. At least [MIN_SUFFIX] digits must agree, and as many as
 *    the shorter number has up to [FULL_SUFFIX]: a short code only ever
 *    matches exactly. When two contacts share an ending, the one that agrees
 *    on more digits wins.
 */
class ContactIndex(
    entries: List<Contact>,
    private val callingCode: String,
) {
    /** One phone entry of one contact. A contact with three numbers is three of these. */
    data class Contact(
        val id: Long,
        val name: String,
        val number: String,
        val photoUri: String? = null,
    )

    private val byName = HashMap<String, Contact>()
    private val byNumber = HashMap<String, Contact>()
    private val bySuffix = HashMap<String, MutableList<Pair<String, Contact>>>()

    init {
        for (contact in entries) {
            if (contact.name.isBlank() || contact.number.isBlank()) continue
            if (Addresses.isPhoneNumber(contact.number)) {
                byNumber.putIfAbsent(Addresses.normalize(contact.number, callingCode), contact)
                val digits = Addresses.digits(contact.number)
                if (digits.length >= MIN_SUFFIX) {
                    bySuffix.getOrPut(digits.takeLast(MIN_SUFFIX)) { mutableListOf() } += digits to contact
                }
            } else {
                val key = Addresses.nameKey(contact.number)
                if (key.isNotEmpty()) byName.putIfAbsent(key, contact)
            }
        }
    }

    val size: Int get() = byName.size + byNumber.size

    fun lookup(address: String): Contact? {
        if (!Addresses.isPhoneNumber(address)) {
            return Addresses.nameKey(address).takeIf { it.isNotEmpty() }?.let(byName::get)
        }
        byNumber[Addresses.normalize(address, callingCode)]?.let { return it }
        val digits = Addresses.digits(address)
        if (digits.length < MIN_SUFFIX) return null
        return bySuffix[digits.takeLast(MIN_SUFFIX)]
            .orEmpty()
            .map { (candidate, contact) -> commonSuffix(candidate, digits) to (candidate to contact) }
            .filter { (common, pair) -> common >= minOf(FULL_SUFFIX, pair.first.length, digits.length) }
            .maxByOrNull { (common, _) -> common }
            ?.second
            ?.second
    }

    /** The name to show for [address]: the contact's, or the address itself. */
    fun displayName(address: String): String = lookup(address)?.name ?: address.trim()

    /** Contacts whose name or number contains [query], for picking a recipient. */
    fun search(
        query: String,
        limit: Int = SEARCH_LIMIT,
    ): List<Contact> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val qDigits = Addresses.digits(q)
        return (byNumber.values + byName.values)
            .asSequence()
            .filter { c ->
                c.name.contains(q, ignoreCase = true) ||
                    c.number.contains(q, ignoreCase = true) ||
                    (qDigits.length >= SEARCH_DIGITS && Addresses.digits(c.number).contains(qDigits))
            }.distinctBy { it.id to it.number }
            .sortedBy { it.name.lowercase() }
            .take(limit)
            .toList()
    }

    companion object {
        /** Android's `MIN_MATCH`: fewer digits than this say nothing about who it is. */
        const val MIN_SUFFIX = 7

        /** A national significant number in most countries, Iran's included. */
        const val FULL_SUFFIX = 10

        private const val SEARCH_LIMIT = 30
        private const val SEARCH_DIGITS = 3

        val EMPTY = ContactIndex(emptyList(), "")

        private fun commonSuffix(
            a: String,
            b: String,
        ): Int {
            var n = 0
            while (n < a.length && n < b.length && a[a.length - 1 - n] == b[b.length - 1 - n]) n++
            return n
        }
    }
}
