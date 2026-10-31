package me.parham1995.sms.feature.conversations

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.parham1995.sms.data.SettingsStore
import me.parham1995.sms.data.backup.BackupWorker
import me.parham1995.sms.data.backup.ConversationDeleter
import me.parham1995.sms.data.contacts.ContactsSource
import me.parham1995.sms.data.reason
import me.parham1995.sms.data.runCatchingUnlessCancelled
import me.parham1995.sms.data.sms.Conversation
import me.parham1995.sms.data.sms.SmsStore
import me.parham1995.sms.messaging.DefaultSmsApp
import javax.inject.Inject

@HiltViewModel
class ConversationsViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        store: SmsStore,
        private val contacts: ContactsSource,
        private val deleter: ConversationDeleter,
        private val settings: SettingsStore,
    ) : ViewModel() {
        data class Item(
            val conversation: Conversation,
            val name: String,
            val photoUri: String?,
        )

        data class State(
            val isDefault: Boolean,
            val items: List<Item>? = null,
            /** Ads on the phone, in every conversation, whatever the search. */
            val ads: Int = 0,
            val deleting: Boolean = false,
            /** What the last delete did, or why it did not. */
            val message: String? = null,
        )

        private data class Action(
            val deleting: Boolean = false,
            val message: String? = null,
        )

        private val action = MutableStateFlow(Action())

        /** Whether a deleted conversation stays in a backup repository, which the confirmation says. */
        val keepsBackup: StateFlow<Boolean> =
            settings.settings
                .map { it.isConfigured }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), false)

        private val isDefault = MutableStateFlow(DefaultSmsApp.isDefault(context))
        val query = MutableStateFlow("")

        @OptIn(ExperimentalCoroutinesApi::class)
        private val conversations =
            isDefault.flatMapLatest { allowed ->
                // Without the SMS role the provider refuses to be read at all.
                if (!allowed) {
                    flowOf(emptyList())
                } else {
                    // A new ad sender recounts the ads, and a new block hides a
                    // conversation, without a message changing.
                    combine(
                        store.changes(),
                        settings.settings.map { it.adRule to it.blockRule }.distinctUntilChanged(),
                    ) { _, (ads, blocked) ->
                        store.conversations(ads).filterNot { blocked.isBlocked(it.address) }
                    }
                }
            }

        val state: StateFlow<State> =
            combine(isDefault, conversations, contacts.index, query, action) { allowed, list, index, q, action ->
                val items =
                    list
                        .map { conversation ->
                            val contact = index.lookup(conversation.address)
                            Item(conversation, contact?.name ?: conversation.address, contact?.photoUri)
                        }.filter {
                            q.isBlank() ||
                                it.name.contains(
                                    q,
                                    true,
                                ) ||
                                it.conversation.snippet.contains(q, true)
                        }
                State(
                    isDefault = allowed,
                    items = items,
                    ads = list.sumOf { it.ads },
                    deleting = action.deleting,
                    message = action.message,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), State(isDefault = isDefault.value))

        /** After the role dialog or a permission dialog: either may have changed what is allowed. */
        fun recheck() {
            isDefault.value = DefaultSmsApp.isDefault(context)
            contacts.refresh()
        }

        fun delete(threadIds: Collection<Long>) = run(done = { null }) { deleter.delete(threadIds) }

        fun deleteAds() =
            run(done = { count -> if (count == 1) "Deleted 1 ad" else "Deleted $count ads" }) { deleter.deleteAds() }

        /** Every message from these conversations' senders is an ad from now on; the next backup deletes them. */
        fun markAdSenders(addresses: Collection<String>) =
            viewModelScope.launch {
                addresses.forEach { settings.setAdSender(it, isAd = true) }
                if (settings.current().deleteAds) BackupWorker.scheduleSoon(context)
            }

        /** Blocks these conversations' senders; they leave the list, and the next backup deletes them. */
        fun block(addresses: Collection<String>) =
            viewModelScope.launch {
                addresses.forEach { settings.setBlocked(it, isBlocked = true) }
                BackupWorker.scheduleSoon(context)
            }

        fun dismissMessage() = action.update { it.copy(message = null) }

        private fun <T> run(
            done: (T) -> String?,
            block: suspend () -> T,
        ) = viewModelScope.launch {
            action.value = Action(deleting = true)
            val result = runCatchingUnlessCancelled { block() }
            action.value =
                Action(message = result.fold(done) { "Not deleted: ${it.reason()}" })
        }

        private companion object {
            const val STOP_MILLIS = 5_000L
        }
    }
