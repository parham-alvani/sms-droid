package me.parham1995.sms.data.backup

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import me.parham1995.sms.core.backup.BackupFile
import me.parham1995.sms.data.SettingsStore
import me.parham1995.sms.data.git.PushRefused
import me.parham1995.sms.data.reason
import me.parham1995.sms.data.runCatchingUnlessCancelled
import java.util.concurrent.TimeUnit

/**
 * Runs [BackupRepository.backup] in the background: once a day, and a few
 * minutes after a message arrives or is sent. The run is
 * [ConversationDeleter.sweep], which backs up first and then deletes what
 * blocked senders sent, and the ads when they are to go.
 *
 * With the call log backup on, a call that ends arms one more run a few
 * minutes later ([armForCalls]); each such run arms the next.
 *
 * The delay after a message is there to batch: a conversation of ten quick
 * messages is one commit, not ten. KEEP, so a message that arrives while a
 * backup is waiting joins it instead of pushing the wait back -- the backup
 * reads every message when it runs, so it misses none.
 */
@HiltWorker
class BackupWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val deleter: ConversationDeleter,
        private val settings: SettingsStore,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val outcome =
                runCatchingUnlessCancelled {
                    deleter.sweep(ads = settings.current().deleteAds)
                }
            if (CALLS in tags && settings.current().backupCalls) armForCalls(applicationContext, after = true)
            val error = outcome.exceptionOrNull() ?: return Result.success()
            Log.w(TAG, "backup failed", error)
            settings.recordFailure(error.reason())
            return when (error) {
                // Neither gets better by trying again: a read-only key, a bad line.
                is PushRefused, is BackupFile.Corrupt -> Result.failure()
                else -> Result.retry()
            }
        }

        companion object {
            private const val TAG = "BackupWorker"
            private const val DAILY = "backup-daily"
            private const val SOON = "backup-soon"
            private const val SOON_MINUTES = 5L
            private const val CALLS = "backup-calls"
            private const val BACKOFF_MINUTES = 10L

            private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

            fun scheduleDaily(context: Context) {
                val request =
                    PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
                        .setConstraints(network)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
                        .build()
                WorkManager
                    .getInstance(context)
                    .enqueueUniquePeriodicWork(DAILY, ExistingPeriodicWorkPolicy.KEEP, request)
            }

            /**
             * A backup that waits for the call log to change, then five more
             * minutes, so a run of calls is one commit. [after] queues it
             * behind the run that is calling, which cannot replace itself.
             * Watching the call log needs the permission, so without it
             * nothing is armed.
             */
            fun armForCalls(
                context: Context,
                after: Boolean = false,
            ) {
                if (context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) !=
                    PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
                val constraints =
                    Constraints
                        .Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .addContentUriTrigger(CallLog.Calls.CONTENT_URI, true)
                        .setTriggerContentUpdateDelay(SOON_MINUTES, TimeUnit.MINUTES)
                        .build()
                val request =
                    OneTimeWorkRequestBuilder<BackupWorker>()
                        .setConstraints(constraints)
                        .addTag(CALLS)
                        .build()
                val policy = if (after) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP
                WorkManager.getInstance(context).enqueueUniqueWork(CALLS, policy, request)
            }

            /** Stops [armForCalls], when the call log backup is turned off. */
            fun disarmForCalls(context: Context) {
                WorkManager.getInstance(context).cancelUniqueWork(CALLS)
            }

            fun scheduleSoon(context: Context) {
                val request =
                    OneTimeWorkRequestBuilder<BackupWorker>()
                        .setInitialDelay(SOON_MINUTES, TimeUnit.MINUTES)
                        .setConstraints(network)
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
                        .build()
                WorkManager.getInstance(context).enqueueUniqueWork(SOON, ExistingWorkPolicy.KEEP, request)
            }
        }
    }
