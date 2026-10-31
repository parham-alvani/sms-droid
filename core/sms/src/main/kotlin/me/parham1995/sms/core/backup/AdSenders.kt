package me.parham1995.sms.core.backup

import me.parham1995.sms.core.Blocking

/**
 * A list of senders as the backup repository keeps it: one entry per line,
 * sorted, so the list can be read and edited in git.
 *
 * The phone and the repository may both change the list between two backups,
 * so they are joined by a three-way [merge] against what the last backup
 * agreed on. An entry added on either side is kept; an entry removed on
 * either side stays removed instead of coming back from the other.
 */
abstract class SenderList {
    fun parse(text: String): Set<String> =
        text
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toSet()

    fun format(senders: Set<String>): String = senders.sorted().joinToString("") { "$it\n" }

    /**
     * [base] is what both sides had after the last sync. An entry is in the
     * result when both sides have it, or when one side added it since.
     */
    fun merge(
        base: Set<String>,
        phone: Set<String>,
        repository: Set<String>,
    ): Set<String> =
        (phone + repository)
            .filter { (it in phone && it in repository) || it !in base }
            .toSet()
}

/** The ad senders, [PATH], one `Ads.senderKey` per line. */
object AdSenders : SenderList() {
    const val PATH = "ad-senders.txt"
}

/**
 * The blocked senders and ranges, [PATH], one `Blocking.entry` per line.
 *
 * The file is meant to be edited by hand too, so [parseEntries] spells each
 * line the canonical way: `0942 200 0176` is `+989422000176` and `0942*` is
 * `+98942*`. The next backup writes the file back in that spelling, sorted,
 * without its comments.
 */
object BlockedSenders : SenderList() {
    const val PATH = "blocked.txt"

    fun parseEntries(
        text: String,
        callingCode: String,
    ): Set<String> = parse(text).mapTo(LinkedHashSet()) { Blocking.entry(it, callingCode) }
}
