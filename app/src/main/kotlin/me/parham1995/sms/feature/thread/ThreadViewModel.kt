package me.parham1995.sms.feature.thread

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.parham1995.sms.Thread
import me.parham1995.sms.data.contacts.ContactsSource
import me.parham1995.sms.data.reason
import me.parham1995.sms.data.runCatchingUnlessCancelled
import me.parham1995.sms.data.sms.Message
import me.parham1995.sms.data.sms.SmsStore
import me.parham1995.sms.messaging.Notifications
import me.parham1995.sms.messaging.Sim
import me.parham1995.sms.messaging.Sims
import me.parham1995.sms.messaging.SmsSender
import javax.inject.Inject

@HiltViewModel
class ThreadViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        private val store: SmsStore,
        private val sender: SmsSender,
        private val notifications: Notifications,
        sims: Sims,
        contacts: ContactsSource,
    ) : ViewModel() {
        data class State(
            val name: String = "",
            val address: String = "",
            val photoUri: String? = null,
            val messages: List<Message>? = null,
            val sims: List<Sim> = emptyList(),
            val sim: Int = -1,
            val error: String? = null,
        )

        private val route = savedState.toRoute<Thread>()
        private val threadId = MutableStateFlow(route.threadId.takeIf { it >= 0 })
        private val extra = MutableStateFlow(Extra(sims = sims.active(), defaultSim = sims.defaultForSms()))

        /** What is being typed. Kept here so it survives rotation and the keyboard. */
        val draft = MutableStateFlow(route.draft)

        private data class Extra(
            val sims: List<Sim>,
            val defaultSim: Int,
            val chosenSim: Int? = null,
            val error: String? = null,
        )

        init {
            if (threadId.value == null) {
                route.address?.let { address -> viewModelScope.launch { threadId.value = store.threadIdFor(address) } }
            }
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        private val messages =
            threadId
                .filterNotNull()
                .flatMapLatest { id -> store.changes().map { id to store.messages(id) } }
                .onEach { (id, list) ->
                    // On screen means read, also for what arrives while it is open.
                    if (list.any { it.isIncoming && !it.read }) store.markRead(id)
                    notifications.cancel(id)
                }.map { (_, list) -> list }

        val state: StateFlow<State> =
            combine(messages, contacts.index, extra) { list, index, extra ->
                val address = route.address ?: list.lastOrNull()?.address.orEmpty()
                State(
                    name = index.displayName(address),
                    address = address,
                    photoUri = index.lookup(address)?.photoUri,
                    messages = list,
                    sims = extra.sims,
                    sim = extra.chosenSim ?: simFor(list, extra),
                    error = extra.error,
                )
            }.stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_MILLIS),
                State(address = route.address.orEmpty()),
            )

        /** The SIM the last message came in on, so a reply goes out the way the conversation came. */
        private fun simFor(
            list: List<Message>,
            extra: Extra,
        ): Int {
            val known = extra.sims.map { it.subscriptionId }
            return list.lastOrNull { it.isIncoming }?.subscriptionId?.takeIf { it in known }
                ?: extra.defaultSim.takeIf { known.isEmpty() || it in known }
                ?: known.first()
        }

        fun chooseSim(subscriptionId: Int) = extra.update { it.copy(chosenSim = subscriptionId) }

        fun dismissError() = extra.update { it.copy(error = null) }

        fun send() {
            val text = draft.value.trim()
            val current = state.value
            if (text.isEmpty() || current.address.isEmpty()) return
            draft.value = ""
            viewModelScope.launch {
                runCatchingUnlessCancelled { sender.send(current.address, text, current.sim) }
                    .onFailure { e ->
                        draft.value = text
                        extra.update { it.copy(error = "Not sent: ${e.reason()}") }
                    }
            }
        }

        fun retry(message: Message) =
            viewModelScope.launch {
                runCatchingUnlessCancelled { sender.resend(message) }
                    .onFailure { e -> extra.update { it.copy(error = "Not sent: ${e.reason()}") } }
            }

        fun delete(message: Message) = viewModelScope.launch { store.delete(message.id) }

        private companion object {
            const val STOP_MILLIS = 5_000L
        }
    }
