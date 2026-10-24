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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.parham1995.sms.data.SettingsStore
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
        settings: SettingsStore,
    ) : ViewModel() {
        data class Item(
            val conversation: Conversation,
            val name: String,
            val photoUri: String?,
        )

        data class State(
            val isDefault: Boolean,
            val items: List<Item>? = null,
            val deleting: Boolean = false,
            val error: String? = null,
        )

        private data class Action(
            val deleting: Boolean = false,
            val error: String? = null,
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
                if (!allowed) flowOf(emptyList()) else store.changes().map { store.conversations() }
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
                State(isDefault = allowed, items = items, deleting = action.deleting, error = action.error)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), State(isDefault = isDefault.value))

        /** After the role dialog or a permission dialog: either may have changed what is allowed. */
        fun recheck() {
            isDefault.value = DefaultSmsApp.isDefault(context)
            contacts.refresh()
        }

        fun delete(threadId: Long) =
            viewModelScope.launch {
                action.value = Action(deleting = true)
                val result = runCatchingUnlessCancelled { deleter.delete(threadId) }
                action.value = Action(error = result.exceptionOrNull()?.let { "Not deleted: ${it.reason()}" })
            }

        fun dismissError() = action.update { it.copy(error = null) }

        private companion object {
            const val STOP_MILLIS = 5_000L
        }
    }
