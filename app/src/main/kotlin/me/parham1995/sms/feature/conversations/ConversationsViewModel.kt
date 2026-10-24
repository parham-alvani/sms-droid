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
import me.parham1995.sms.data.contacts.ContactsSource
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
    ) : ViewModel() {
        data class Item(
            val conversation: Conversation,
            val name: String,
        )

        data class State(
            val isDefault: Boolean,
            val items: List<Item>? = null,
        )

        private val isDefault = MutableStateFlow(DefaultSmsApp.isDefault(context))
        val query = MutableStateFlow("")

        @OptIn(ExperimentalCoroutinesApi::class)
        private val conversations =
            isDefault.flatMapLatest { allowed ->
                // Without the SMS role the provider refuses to be read at all.
                if (!allowed) flowOf(emptyList()) else store.changes().map { store.conversations() }
            }

        val state: StateFlow<State> =
            combine(isDefault, conversations, contacts.index, query) { allowed, list, index, q ->
                val items =
                    list
                        .map { Item(it, index.displayName(it.address)) }
                        .filter {
                            q.isBlank() ||
                                it.name.contains(
                                    q,
                                    true,
                                ) ||
                                it.conversation.snippet.contains(q, true)
                        }
                State(isDefault = allowed, items = items)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), State(isDefault = isDefault.value))

        /** After the role dialog or a permission dialog: either may have changed what is allowed. */
        fun recheck() {
            isDefault.value = DefaultSmsApp.isDefault(context)
            contacts.refresh()
        }

        private companion object {
            const val STOP_MILLIS = 5_000L
        }
    }
