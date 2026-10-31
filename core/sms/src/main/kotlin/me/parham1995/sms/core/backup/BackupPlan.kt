package me.parham1995.sms.core.backup

import me.parham1995.sms.core.AdRule
import me.parham1995.sms.core.BlockRule

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
     * Merges the phone's messages into the repository and files each line:
     * what a [blocked] sender sent under [BackupLayout.BLOCKED], an ad (by
     * [ads]) under [BackupLayout.ADS], the rest under
     * [BackupLayout.DIRECTORY]. [existing] lists the repository's files, so a
     * line already in git moves when a rule changes, even when the phone no
     * longer has it. [read] returns a file's current text, or null when it
     * does not exist.
     *
     * Nothing is dropped: across the three folders every line stays, and a
     * line only moves between them. A file left with no lines is removed.
     * Files that would not change are left out.
     */
    fun backup(
        phone: Collection<BackupMessage>,
        callingCode: String,
        ads: AdRule,
        blocked: BlockRule,
        existing: Collection<String>,
        read: (path: String) -> String?,
    ): List<Write> {
        val byName = phone.groupBy { BackupLayout.fileName(it.address, callingCode) }
        val names = (byName.keys + existing.mapNotNull(BackupLayout::nameOf)).toSortedSet()
        return names.flatMap { name ->
            val messagesPath = BackupLayout.messagesPath(name)
            val adsPath = BackupLayout.adsPath(name)
            val blockedPath = BackupLayout.blockedPath(name)
            val oldMessages = read(messagesPath)?.let { BackupFile.parse(messagesPath, it) }
            val oldAds = read(adsPath)?.let { BackupFile.parse(adsPath, it) }
            val oldBlocked = read(blockedPath)?.let { BackupFile.parse(blockedPath, it) }
            val kept = oldMessages.orEmpty() + oldAds.orEmpty() + oldBlocked.orEmpty()
            val known = kept.mapTo(HashSet()) { it.key }
            val fresh = byName[name].orEmpty().filter { known.add(it.key) }
            val freshKeys = fresh.mapTo(HashSet()) { it.key }
            val incoming = { line: BackupMessage -> line.direction == BackupMessage.Direction.IN }
            val (blockedLines, unblocked) =
                (kept + fresh)
                    .distinctBy { it.key }
                    .partition { incoming(it) && blocked.isBlocked(it.address) }
            val (adLines, rest) = unblocked.partition { incoming(it) && ads.isAd(it.address, it.body) }
            listOfNotNull(
                write(messagesPath, oldMessages, rest, rest.count { it.key in freshKeys }),
                write(adsPath, oldAds, adLines, adLines.count { it.key in freshKeys }),
                write(blockedPath, oldBlocked, blockedLines, blockedLines.count { it.key in freshKeys }),
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

    /** The commit message for a backup that added [added] messages across [files] files, and [calls] calls. */
    fun commitMessage(
        added: Int,
        files: Int,
        calls: Int = 0,
    ): String {
        val callsPart = if (calls == 1) "1 call" else "$calls calls"
        if (added ==
            0
        ) {
            return if (calls == 0) "backup: file ads, blocked senders and their lists" else "backup: $callsPart"
        }
        val messages = if (added == 1) "1 message" else "$added messages"
        val conversations = if (files == 1) "1 conversation" else "$files conversations"
        return "backup: $messages in $conversations" + if (calls == 0) "" else ", $callsPart"
    }
}
