package me.parham1995.sms.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.parham1995.sms.data.BackupStatus
import me.parham1995.sms.data.Settings
import me.parham1995.sms.data.SettingsStore
import me.parham1995.sms.data.backup.BackupRepository
import me.parham1995.sms.data.backup.BackupWorker
import me.parham1995.sms.data.git.SshKeyStore
import me.parham1995.sms.data.reason
import me.parham1995.sms.data.runCatchingUnlessCancelled
import me.parham1995.sms.messaging.DefaultSmsApp
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val store: SettingsStore,
        private val keys: SshKeyStore,
        private val backups: BackupRepository,
    ) : ViewModel() {
        data class Ui(
            val sshPublicKey: String? = null,
            val busy: Boolean = false,
            val message: String? = null,
            val isDefault: Boolean = true,
        )

        val settings: StateFlow<Settings?> = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
        val status: StateFlow<BackupStatus> =
            store.backupStatus.stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                BackupStatus(),
            )

        private val _ui =
            MutableStateFlow(Ui(sshPublicKey = keys.publicKeyLine(), isDefault = DefaultSmsApp.isDefault(context)))
        val ui: StateFlow<Ui> = _ui.asStateFlow()

        fun recheck() = _ui.update { it.copy(isDefault = DefaultSmsApp.isDefault(context)) }

        fun saveRepository(
            remoteUrl: String,
            branch: String,
        ) = viewModelScope.launch {
            store.update { it.copy(remoteUrl = remoteUrl.trim(), branch = branch.trim().ifEmpty { "main" }) }
        }

        fun saveAuthor(
            name: String,
            email: String,
        ) = viewModelScope.launch { store.update { it.copy(authorName = name, authorEmail = email) } }

        fun saveCallingCode(code: String) =
            viewModelScope.launch { store.update { it.copy(callingCode = code.trim().removePrefix("+")) } }

        fun setDeliveryReports(on: Boolean) = viewModelScope.launch { store.update { it.copy(deliveryReports = on) } }

        /** Turning it on clears the ads already on the phone with the next backup. */
        fun setDeleteAds(on: Boolean) =
            viewModelScope.launch {
                store.update { it.copy(deleteAds = on) }
                if (on) BackupWorker.scheduleSoon(context)
            }

        fun removeAdSender(key: String) =
            viewModelScope.launch { store.update { it.copy(adSenders = it.adSenders - key) } }

        fun generateKey() =
            viewModelScope.launch {
                _ui.update { it.copy(busy = true, message = null) }
                runCatchingUnlessCancelled { keys.generate() }
                    .onSuccess { line -> _ui.update { it.copy(busy = false, sshPublicKey = line) } }
                    .onFailure { e ->
                        _ui.update { it.copy(busy = false, message = "Could not make a key: ${e.reason()}") }
                    }
            }

        fun backUpNow() =
            run("Backing up…") {
                val result = backups.backup()
                when {
                    result == null -> "Set a repository first"
                    result.added == 0 -> "Up to date: nothing new to back up"
                    else -> "Backed up ${result.added} messages from ${result.files} conversations"
                }
            }

        fun restore() =
            run("Restoring…") {
                val restored = backups.restore()
                if (restored == 0) "Nothing to restore: the phone has every message" else "Restored $restored messages"
            }

        fun dismissMessage() = _ui.update { it.copy(message = null) }

        private fun run(
            working: String,
            block: suspend () -> String,
        ) = viewModelScope.launch {
            _ui.update { it.copy(busy = true, message = working) }
            runCatchingUnlessCancelled { block() }
                .onSuccess { done -> _ui.update { it.copy(busy = false, message = done) } }
                .onFailure { e ->
                    store.recordFailure(e.reason())
                    _ui.update { it.copy(busy = false, message = "Failed: ${e.reason()}") }
                }
        }

        companion object {
            private val GITHUB =
                Regex("""(?:git@|ssh://git@)(?:ssh\.)?github\.com[:/](?:443/)?([^/]+/[^/]+?)(?:\.git)?/?$""")

            /** `owner/name` for a GitHub SSH URL, for the "add deploy key" link. */
            fun githubRepository(url: String): String? = GITHUB.find(url.trim())?.groupValues?.get(1)
        }
    }
