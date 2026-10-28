package me.parham1995.sms.core.backup

import me.parham1995.sms.core.AdRule

/**
 * The two directions a backup moves messages, as pure functions of what the
 * phone has and what the repository has, so both are tested without a phone.
 */
object BackupPlan {
    /** One file to write, or to remove when [text] is null, and how many messages it gained. */
    data class Write(
        val path: String,
        val text: String?,
        val added: Int,
    )

    /**
     * Merges the phone's messages into the repository and files each line by
     * [ads]: an ad under [BackupLayout.ADS], the rest under
     * [BackupLayout.DIRECTORY]. [existing] lists the repository's files, so a
     * line already in git moves when the rule changes, even when the phone
     * no longer has it. [read] returns a file's current text, or null when it
     * does not exist.
     *
     * Nothing is dropped: across the two folders every line stays, and a line
     * only moves between them. A file left with no lines is removed. Files
     * that would not change are left out.
     */
    fun backup(
        phone: Collection<BackupMessage>,
        callingCode: String,
        ads: AdRule,
        existing: Collection<String>,
        read: (path: String) -> String?,
    ): List<Write> {
        val byName = phone.groupBy { BackupLayout.fileName(it.address, callingCode) }
        val names = (byName.keys + existing.mapNotNull(BackupLayout::nameOf)).toSortedSet()
        return names.flatMap { name ->
            val messagesPath = BackupLayout.messagesPath(name)
            val adsPath = BackupLayout.adsPath(name)
            val oldMessages = read(messagesPath)?.let { BackupFile.parse(messagesPath, it) }
            val oldAds = read(adsPath)?.let { BackupFile.parse(adsPath, it) }
            val kept = oldMessages.orEmpty() + oldAds.orEmpty()
            val known = kept.mapTo(HashSet()) { it.key }
            val fresh = byName[name].orEmpty().filter { known.add(it.key) }
            val freshKeys = fresh.mapTo(HashSet()) { it.key }
            val (adLines, rest) =
                (kept + fresh)
                    .distinctBy { it.key }
                    .partition { it.direction == BackupMessage.Direction.IN && ads.isAd(it.address, it.body) }
            listOfNotNull(
                write(messagesPath, oldMessages, rest, rest.count { it.key in freshKeys }),
                write(adsPath, oldAds, adLines, adLines.count { it.key in freshKeys }),
            )
        }
    }

    /** The write that makes [path] hold [lines], or null when it already does. */
    private fun write(
        path: String,
        old: List<BackupMessage>?,
        lines: List<BackupMessage>,
        added: Int,
    ): Write? =
        when {
            old == null && lines.isEmpty() -> null
            old != null && old.mapTo(HashSet()) { it.key } == lines.mapTo(HashSet()) { it.key } -> null
            else -> Write(path, lines.takeIf { it.isNotEmpty() }?.let(BackupFile::format), added)
        }

    /** The messages in the backup that the phone does not have, oldest first. */
    fun restore(
        backup: Collection<BackupMessage>,
        phone: Collection<BackupMessage.Key>,
    ): List<BackupMessage> {
        val present = phone.toHashSet()
        return backup
            .filter { present.add(it.key) }
            .sortedWith(BackupMessage.ORDER)
    }

    /** The commit message for a backup that added [added] messages across [files] files. */
    fun commitMessage(
        added: Int,
        files: Int,
    ): String {
        if (added == 0) return "backup: file ads and ad senders"
        val messages = if (added == 1) "1 message" else "$added messages"
        val conversations = if (files == 1) "1 conversation" else "$files conversations"
        return "backup: $messages in $conversations"
    }
}
