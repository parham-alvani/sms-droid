package me.parham1995.sms.data.backup

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.parham1995.sms.core.backup.BackupFile
import me.parham1995.sms.core.backup.BackupLayout
import me.parham1995.sms.core.backup.BackupPlan
import me.parham1995.sms.data.Settings
import me.parham1995.sms.data.SettingsStore
import me.parham1995.sms.data.git.BackupGit
import me.parham1995.sms.data.git.PushRefused
import me.parham1995.sms.data.git.SshKeyStore
import me.parham1995.sms.data.sms.SmsStore
import org.eclipse.jgit.lib.PersonIdent
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Moves messages between the phone and the backup repository.
 *
 * Both directions start with [BackupGit.refresh], so the work tree is always
 * the remote's and never something a failed run left behind. A backup then
 * merges the phone's messages into it and pushes; when the push is rejected
 * because the remote moved, it starts again from the fetch, which is all the
 * conflict handling an add-only format needs.
 *
 * One at a time: the worker and the "back up now" button drive the same
 * directory.
 */
@Singleton
class BackupRepository
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val settings: SettingsStore,
        private val sms: SmsStore,
        private val keys: SshKeyStore,
    ) {
        private val root = File(context.filesDir, "backup")
        private val configDir = File(context.filesDir, "git")

        /** Which repository [root] is a clone of; a different one starts from scratch. */
        private val remoteMarker = File(context.filesDir, "backup.remote")
        private val lock = Mutex()

        data class Result(
            val added: Int,
            val files: Int,
        )

        /** Backs up every message. Null when no repository is set. */
        suspend fun backup(): Result? =
            lock.withLock {
                val current = settings.current()
                if (!current.isConfigured) return@withLock null
                val git = open(current)
                val phone = sms.forBackup()
                repeat(PUSH_ATTEMPTS) {
                    git.refresh()
                    val writes = BackupPlan.backup(phone, current.callingCode, git::read)
                    withContext(Dispatchers.IO) { writes.forEach { git.write(it.path, it.text) } }
                    val added = writes.sumOf { it.added }
                    if (writes.isNotEmpty()) git.commit(BackupPlan.commitMessage(added, writes.size), author(current))
                    if (git.push()) {
                        settings.recordSuccess(System.currentTimeMillis(), added)
                        return@withLock Result(added, writes.size)
                    }
                }
                throw PushRefused("the backup repository kept changing while this phone pushed")
            }

        /** Puts back every message the backup has and the phone does not. Returns how many. */
        suspend fun restore(): Int =
            lock.withLock {
                val current = settings.current()
                if (!current.isConfigured) return@withLock 0
                val git = open(current)
                if (!git.refresh()) return@withLock 0
                val backup =
                    withContext(Dispatchers.IO) {
                        git
                            .list(BackupLayout.DIRECTORY)
                            .filter(BackupLayout::isBackupFile)
                            .flatMap { path -> BackupFile.parse(path, git.read(path).orEmpty()) }
                    }
                val missing = BackupPlan.restore(backup, sms.forBackup().map { it.key })
                sms.restore(missing)
            }

        private fun open(current: Settings): BackupGit {
            val identity = "${current.remoteUrl}\n${current.branch}"
            if (remoteMarker.takeIf { it.isFile }?.readText() != identity) {
                root.deleteRecursively()
                remoteMarker.writeText(identity)
            }
            return BackupGit(root, current.remoteUrl, current.branch, keys, configDir)
        }

        private fun author(current: Settings) =
            PersonIdent(
                current.authorName.ifBlank { "sms-droid" },
                current.authorEmail.ifBlank { "sms-droid@localhost" },
            )

        private companion object {
            const val PUSH_ATTEMPTS = 3
        }
    }
