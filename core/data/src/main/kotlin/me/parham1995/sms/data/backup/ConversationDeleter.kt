package me.parham1995.sms.data.backup

import me.parham1995.sms.data.sms.SmsStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deletes conversations from the phone without losing what the backup does
 * not have yet.
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
        suspend fun delete(threadId: Long) {
            backup.backup()
            sms.deleteThread(threadId)
        }
    }
