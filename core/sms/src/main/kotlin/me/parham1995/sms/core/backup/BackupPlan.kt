package me.parham1995.sms.core.backup

/**
 * The two directions a backup moves messages, as pure functions of what the
 * phone has and what the repository has, so both are tested without a phone.
 */
object BackupPlan {
    /** One file to write, and how many messages it gained. */
    data class Write(
        val path: String,
        val text: String,
        val added: Int,
    )

    /**
     * Groups the phone's messages by file and merges each group into what the
     * repository has there. [read] returns a file's current text, or null when
     * it does not exist. Files that would not change are left out.
     */
    fun backup(
        phone: Collection<BackupMessage>,
        callingCode: String,
        read: (path: String) -> String?,
    ): List<Write> =
        phone
            .groupBy { BackupLayout.pathFor(it.address, callingCode) }
            .toSortedMap()
            .mapNotNull { (path, messages) ->
                val merged = BackupFile.merge(path, read(path), messages)
                merged.text?.let { Write(path, it, merged.added) }
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
        val messages = if (added == 1) "1 message" else "$added messages"
        val conversations = if (files == 1) "1 conversation" else "$files conversations"
        return "backup: $messages in $conversations"
    }
}
