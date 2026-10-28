package me.parham1995.sms.data.backup

import me.parham1995.sms.data.sms.SmsStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deletes conversations and ads from the phone without losing what the
 * backup does not have yet.
 *
 * The backup only adds, so a message deleted after it was backed up stays in
 * the repository. A message that arrived since the last run would not: when a
 * repository is set, a backup runs first, and when it fails nothing is deleted.
 */
@Singleton
class ConversationDeleter
    @Inject
    constructor(
        private val backup: BackupRepository,
        private val sms: SmsStore,
    ) {
        /** Backs up when a repository is set, then deletes; throws, deleting nothing, when the backup fails. */
        suspend fun delete(threadId: Long) = delete(listOf(threadId))

        /** [delete] for many threads, with one backup for all of them. */
        suspend fun delete(threadIds: Collection<Long>) {
            if (threadIds.isEmpty()) return
            backup.backup()
            threadIds.forEach { sms.deleteThread(it) }
        }

        /**
         * Backs up, then deletes every ad the backup read. An ad that arrives
         * while the backup runs is newer than the cut-off and waits for the
         * next run. Returns how many went.
         */
        suspend fun deleteAds(): Int {
            val upTo = System.currentTimeMillis()
            backup.backup()
            return sms.deleteMessages(sms.adIds(upTo))
        }
    }
