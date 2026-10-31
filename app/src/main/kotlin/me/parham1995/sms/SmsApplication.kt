package me.parham1995.sms

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.parham1995.sms.data.ApplicationScope
import me.parham1995.sms.data.SettingsStore
import me.parham1995.sms.data.backup.BackupWorker
import me.parham1995.sms.messaging.Notifications
import javax.inject.Inject

@HiltAndroidApp
class SmsApplication :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var notifications: Notifications

    @Inject
    lateinit var settings: SettingsStore

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    /** WorkManager starts on demand with Hilt's factory; its default initializer is removed in the manifest. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        notifications.createChannels()
        // KEEP: scheduling again on every start leaves the existing schedule alone.
        BackupWorker.scheduleDaily(this)
        // Each call-log run arms the next; this re-arms a chain an update or a
        // force stop broke, and KEEP leaves a live one alone.
        scope.launch { if (settings.current().backupCalls) BackupWorker.armForCalls(this@SmsApplication) }
    }
}
