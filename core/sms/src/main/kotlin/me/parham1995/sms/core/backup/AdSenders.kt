package me.parham1995.sms.core.backup

/**
 * The ad senders as the backup repository keeps them: [PATH], one
 * `Ads.senderKey` per line, sorted, so the list can be read and edited in git.
 *
 * The phone and the repository may both change the list between two backups,
 * so they are joined by a three-way [merge] against what the last backup
 * agreed on. A sender added on either side is kept; a sender removed on
 * either side stays removed instead of coming back from the other.
 */
object AdSenders {
    const val PATH = "ad-senders.txt"

    fun parse(text: String): Set<String> =
        text
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toSet()

    fun format(senders: Set<String>): String = senders.sorted().joinToString("") { "$it\n" }

    /**
     * [base] is what both sides had after the last sync. A sender is in the
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
