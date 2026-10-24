package me.parham1995.sms.feature.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import me.parham1995.sms.NewMessage
import me.parham1995.sms.core.ContactIndex
import me.parham1995.sms.data.contacts.ContactsSource
import me.parham1995.sms.ui.Avatar
import me.parham1995.sms.ui.mutedText
import javax.inject.Inject

@HiltViewModel
class NewMessageViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        contacts: ContactsSource,
    ) : ViewModel() {
        val draft: String = savedState.toRoute<NewMessage>().draft
        val query = MutableStateFlow("")

        val matches: StateFlow<List<ContactIndex.Contact>> =
            combine(query, contacts.index) { q, index -> index.search(q) }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_MILLIS), emptyList())

        private companion object {
            const val STOP_MILLIS = 5_000L
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewMessageScreen(
    onBack: () -> Unit,
    onChosen: (address: String, draft: String) -> Unit,
    viewModel: NewMessageViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val matches by viewModel.matches.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val typed = query.trim()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New message") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding(),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.query.value = it },
                label = { Text("To") },
                placeholder = { Text("Name or number") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, autoCorrectEnabled = false),
                keyboardActions =
                    KeyboardActions(
                        onNext = { if (typed.isNotEmpty()) onChosen(typed, viewModel.draft) },
                    ),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .focusRequester(focus),
            )
            LazyColumn(Modifier.fillMaxSize()) {
                // What was typed is always an option: a number nobody saved,
                // or a short code.
                if (typed.isNotEmpty()) {
                    item(key = "typed") {
                        Recipient(name = "Send to $typed", number = null, address = typed, photoUri = null) {
                            onChosen(typed, viewModel.draft)
                        }
                    }
                }
                items(matches, key = { "${it.id}:${it.number}" }) { contact ->
                    Recipient(
                        name = contact.name,
                        number = contact.number,
                        address = contact.number,
                        photoUri = contact.photoUri,
                    ) {
                        onChosen(contact.number, viewModel.draft)
                    }
                }
            }
        }
    }
}

@Composable
private fun Recipient(
    name: String,
    number: String?,
    address: String,
    photoUri: String?,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(name, address, photoUri, size = 40.dp)
        Column {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            number?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = mutedText) }
        }
    }
}
