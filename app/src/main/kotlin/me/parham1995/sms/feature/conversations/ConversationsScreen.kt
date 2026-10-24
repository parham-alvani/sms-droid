package me.parham1995.sms.feature.conversations

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.parham1995.sms.messaging.DefaultSmsApp
import me.parham1995.sms.ui.Avatar
import me.parham1995.sms.ui.mutedText
import me.parham1995.sms.ui.shortTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    onOpen: (threadId: Long, address: String) -> Unit,
    onNew: () -> Unit,
    onSettings: () -> Unit,
    viewModel: ConversationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    var searching by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    val permissions =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { viewModel.recheck() }
    val role =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.recheck() }
    LifecycleResumeEffect(Unit) {
        viewModel.recheck()
        onPauseOrDispose { }
    }
    // Asked once the app is the SMS app, which is when they start to matter:
    // names for the list, notifications for what arrives, SIMs for sending.
    LaunchedEffect(state.isDefault) {
        if (state.isDefault) {
            permissions.launch(
                arrayOf(
                    Manifest.permission.READ_CONTACTS,
                    Manifest.permission.POST_NOTIFICATIONS,
                    Manifest.permission.READ_PHONE_STATE,
                ),
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        TextField(
                            value = query,
                            onValueChange = { viewModel.query.value = it },
                            placeholder = { Text("Search") },
                            singleLine = true,
                            colors =
                                TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text("Messages")
                    }
                },
                actions = {
                    if (searching) {
                        IconButton(onClick = {
                            searching = false
                            viewModel.query.value = ""
                        }) { Icon(Icons.Filled.Close, "Close search") }
                    } else {
                        IconButton(onClick = { searching = true }) { Icon(Icons.Filled.Search, "Search") }
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings") }
                },
            )
        },
        floatingActionButton = {
            if (state.isDefault) {
                ExtendedFloatingActionButton(
                    onClick = onNew,
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("New message") },
                )
            }
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            val items = state.items
            when {
                !state.isDefault ->
                    NotDefault(onSwitch = { role.launch(DefaultSmsApp.request(context)) })
                items == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                items.isEmpty() ->
                    Text(
                        if (query.isBlank()) "No messages yet" else "Nothing matches",
                        color = mutedText,
                        modifier = Modifier.align(Alignment.Center),
                    )
                else ->
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(items, key = { it.conversation.threadId }) { item ->
                            ConversationRow(
                                item,
                                onClick = { onOpen(item.conversation.threadId, item.conversation.address) },
                            )
                        }
                    }
            }
        }
    }
}

@Composable
private fun NotDefault(onSwitch: () -> Unit) {
    ElevatedCard(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Make this your SMS app", style = MaterialTheme.typography.titleMedium)
            Text(
                "Android lets only the phone's SMS app read your messages, store the ones that arrive, " +
                    "and send. The app you use now keeps working until you switch, and you can switch back " +
                    "in Settings > Apps > Default apps.",
            )
            Button(onClick = onSwitch) { Text("Make default") }
        }
    }
}

@Composable
private fun ConversationRow(
    item: ConversationsViewModel.Item,
    onClick: () -> Unit,
) {
    val conversation = item.conversation
    val unread = conversation.unread > 0
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(item.name, conversation.address, item.photoUri)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(shortTime(conversation.date), style = MaterialTheme.typography.labelMedium, color = mutedText)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (conversation.lastFailed) {
                    Icon(Icons.Filled.Warning, "Not sent", tint = MaterialTheme.colorScheme.error)
                }
                Text(
                    conversation.snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (unread) MaterialTheme.colorScheme.onSurface else mutedText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (unread) Badge { Text(conversation.unread.toString()) }
            }
        }
    }
}
