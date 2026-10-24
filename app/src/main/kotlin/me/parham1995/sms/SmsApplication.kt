package me.parham1995.sms

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
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

    /** WorkManager starts on demand with Hilt's factory; its default initializer is removed in the manifest. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        notifications.createChannels()
        // KEEP: scheduling again on every start leaves the existing schedule alone.
        BackupWorker.scheduleDaily(this)
    }
}
