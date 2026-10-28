package me.parham1995.sms.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import me.parham1995.sms.core.AdRule
import me.parham1995.sms.core.Ads
import me.parham1995.sms.core.backup.AdSenders
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

/** Nothing secret is kept here; it is plain DataStore. The SSH key is sealed elsewhere. */
data class Settings(
    /** The backup repository, e.g. `git@github.com:you/sms-backup.git`. Empty: no backup. */
    val remoteUrl: String = "",
    val branch: String = "main",
    val authorName: String = "",
    val authorEmail: String = "",
    /**
     * The calling code a national number belongs to, without the `+`. It
     * decides that `0912…` and `+98912…` are one person, in the contact match
     * and in the backup's file names.
     */
    val callingCode: String = "98",
    val deliveryReports: Boolean = false,
    /**
     * Deletes every ad (by `Ads`) once it is backed up, and stores a new one
     * as read, without a notification.
     */
    val deleteAds: Boolean = true,
    /** Senders whose every message is an ad, by `Ads.senderKey`. */
    val adSenders: Set<String> = emptySet(),
    /** The ad senders as the last backup left them in git: the base the next sync merges against. */
    val adSendersSynced: Set<String> = emptySet(),
) {
    val isConfigured: Boolean get() = remoteUrl.isNotBlank()

    val adRule: AdRule get() = AdRule(adSenders, callingCode)
}

/** How the last backup went, for the settings screen. */
data class BackupStatus(
    /** Epoch millis of the last backup that pushed; 0 before the first. */
    val lastSuccess: Long = 0,
    val lastAdded: Int = 0,
    /** Why the last attempt failed, or null when it did not. */
    val lastError: String? = null,
)

@Singleton
class SettingsStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

        val backupStatus: Flow<BackupStatus> =
            context.dataStore.data.map { prefs ->
                BackupStatus(
                    lastSuccess = prefs[BACKUP_AT] ?: 0,
                    lastAdded = prefs[BACKUP_ADDED] ?: 0,
                    lastError = prefs[BACKUP_ERROR]?.takeIf { it.isNotEmpty() },
                )
            }

        suspend fun current(): Settings = settings.first()

        suspend fun update(transform: (Settings) -> Settings) {
            context.dataStore.edit { prefs ->
                val next = transform(prefs.toSettings())
                prefs[REMOTE] = next.remoteUrl
                prefs[BRANCH] = next.branch
                prefs[AUTHOR_NAME] = next.authorName
                prefs[AUTHOR_EMAIL] = next.authorEmail
                prefs[CALLING_CODE] = next.callingCode
                prefs[DELIVERY_REPORTS] = next.deliveryReports
                prefs[DELETE_ADS] = next.deleteAds
                prefs[AD_SENDERS] = next.adSenders
                prefs[AD_SENDERS_SYNCED] = next.adSendersSynced
            }
        }

        /** Makes [address] an ad sender, or stops it being one. */
        suspend fun setAdSender(
            address: String,
            isAd: Boolean,
        ) = update {
            val key = Ads.senderKey(address, it.callingCode)
            it.copy(adSenders = if (isAd) it.adSenders + key else it.adSenders - key)
        }

        /**
         * After a push that left [pushed] in git: the phone takes it, keeping
         * what changed on the phone since [started] (its list when the backup
         * read it), and [pushed] is the base for the next sync.
         */
        suspend fun syncAdSenders(
            started: Set<String>,
            pushed: Set<String>,
        ) = update {
            it.copy(adSenders = AdSenders.merge(started, it.adSenders, pushed), adSendersSynced = pushed)
        }

        suspend fun recordSuccess(
            at: Long,
            added: Int,
        ) {
            context.dataStore.edit { prefs ->
                prefs[BACKUP_AT] = at
                prefs[BACKUP_ADDED] = added
                prefs[BACKUP_ERROR] = ""
            }
        }

        suspend fun recordFailure(message: String) {
            context.dataStore.edit { prefs -> prefs[BACKUP_ERROR] = message }
        }

        private fun Preferences.toSettings(): Settings {
            val defaults = Settings()
            return Settings(
                remoteUrl = this[REMOTE] ?: defaults.remoteUrl,
                branch = this[BRANCH] ?: defaults.branch,
                authorName = this[AUTHOR_NAME] ?: defaults.authorName,
                authorEmail = this[AUTHOR_EMAIL] ?: defaults.authorEmail,
                callingCode = this[CALLING_CODE] ?: defaults.callingCode,
                deliveryReports = this[DELIVERY_REPORTS] ?: defaults.deliveryReports,
                deleteAds = this[DELETE_ADS] ?: defaults.deleteAds,
                adSenders = this[AD_SENDERS] ?: defaults.adSenders,
                adSendersSynced = this[AD_SENDERS_SYNCED] ?: defaults.adSendersSynced,
            )
        }

        private companion object {
            val REMOTE = stringPreferencesKey("remote_url")
            val BRANCH = stringPreferencesKey("branch")
            val AUTHOR_NAME = stringPreferencesKey("author_name")
            val AUTHOR_EMAIL = stringPreferencesKey("author_email")
            val CALLING_CODE = stringPreferencesKey("calling_code")
            val DELIVERY_REPORTS = booleanPreferencesKey("delivery_reports")
            val DELETE_ADS = booleanPreferencesKey("delete_ads")
            val AD_SENDERS = stringSetPreferencesKey("ad_senders")
            val AD_SENDERS_SYNCED = stringSetPreferencesKey("ad_senders_synced")
            val BACKUP_AT = longPreferencesKey("backup_at")
            val BACKUP_ADDED = intPreferencesKey("backup_added")
            val BACKUP_ERROR = stringPreferencesKey("backup_error")
        }
    }
