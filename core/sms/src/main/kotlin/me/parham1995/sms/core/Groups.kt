package me.parham1995.sms.core

/**
 * The group filter of the conversation list.
 *
 * Each conversation carries the groups of its contact. [counts] lists the
 * groups that have at least one conversation, for the chips above the
 * list; [matches] says whether a conversation passes the chosen chip.
 */
object Groups {
    /** One chip: a group and how many conversations it has. */
    data class Count(
        val name: String,
        val conversations: Int,
    )

    /** Every group in [conversations], by name ignoring case, with how many conversations each has. */
    fun counts(conversations: List<List<String>>): List<Count> =
        conversations
            .flatMap { it.distinct() }
            .groupingBy { it }
            .eachCount()
            .map { (name, n) -> Count(name, n) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    /** Whether a conversation in [groups] passes the filter [chosen]; no filter passes everything. */
    fun matches(
        groups: List<String>,
        chosen: String?,
    ): Boolean = chosen == null || groups.any { it == chosen }

    /**
     * The filter to keep when the groups change: [chosen] while some
     * conversation is still in it, otherwise none, so a group that emptied
     * or was renamed does not leave an empty list behind.
     */
    fun keep(
        chosen: String?,
        counts: List<Count>,
    ): String? = chosen?.takeIf { name -> counts.any { it.name == name } }
}
