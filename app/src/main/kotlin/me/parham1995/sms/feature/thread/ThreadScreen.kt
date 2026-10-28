package me.parham1995.sms.feature.thread

import android.content.ClipData
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.parham1995.sms.data.sms.Message
import me.parham1995.sms.messaging.Sim
import me.parham1995.sms.ui.Avatar
import me.parham1995.sms.ui.DeleteConversationDialog
import me.parham1995.sms.ui.DeletingDialog
import me.parham1995.sms.ui.clockTime
import me.parham1995.sms.ui.dayLabel
import me.parham1995.sms.ui.dayOf
import me.parham1995.sms.ui.mutedText
import me.parham1995.sms.ui.theme.fontFor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(
    onBack: () -> Unit,
    viewModel: ThreadViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val keepsBackup by viewModel.keepsBackup.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
    if (confirmDelete) {
        DeleteConversationDialog(
            title = "Delete the conversation with ${state.name}?",
            keepsBackup = keepsBackup,
            onConfirm = {
                confirmDelete = false
                viewModel.deleteConversation()
            },
            onDismiss = { confirmDelete = false },
        )
    }
    if (state.deleting) DeletingDialog()

    state.error?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("OK") } },
            text = { Text(error) },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Avatar(state.name, state.address, state.photoUri, size = 36.dp)
                        Column {
                            Text(state.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (state.name != state.address && state.address.isNotEmpty()) {
                                Text(state.address, style = MaterialTheme.typography.labelMedium, color = mutedText)
                            }
                        }
                    }
                },
                actions = {
                    if (!state.messages.isNullOrEmpty()) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, "Delete conversation")
                        }
                    }
                    if (state.address.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (state.isAdSender) "Not ads" else "Treat as ads") },
                                    onClick = {
                                        menu = false
                                        viewModel.setAdSender(!state.isAdSender)
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            Composer(
                draft = draft,
                onDraft = { viewModel.draft.value = it },
                sims = state.sims,
                sim = state.sim,
                onSim = viewModel::chooseSim,
                onSend = viewModel::send,
            )
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            val messages = state.messages
            if (messages == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                Messages(messages, onRetry = viewModel::retry, onDelete = viewModel::delete)
            }
        }
    }
}

/** A message, or the day the messages below it were sent on. */
private sealed interface Line {
    val key: String

    data class Day(
        val label: String,
    ) : Line {
        override val key get() = "day:$label"
    }

    data class Bubble(
        val message: Message,
    ) : Line {
        override val key get() = "msg:${message.id}"
    }
}

@Composable
private fun Messages(
    messages: List<Message>,
    onRetry: (Message) -> Unit,
    onDelete: (Message) -> Unit,
) {
    // Newest first, drawn bottom up, so the list opens at the newest message
    // and stays there when one arrives.
    val rows =
        remember(messages) {
            buildList {
                messages.asReversed().forEachIndexed { index, message ->
                    add(Line.Bubble(message))
                    val older = messages.asReversed().getOrNull(index + 1)
                    if (older == null ||
                        dayOf(older.date) != dayOf(message.date)
                    ) {
                        add(Line.Day(dayLabel(dayOf(message.date))))
                    }
                }
            }
        }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.lastOrNull()?.id) { listState.scrollToItem(0) }
    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(rows, key = { it.key }) { row ->
            when (row) {
                is Line.Day ->
                    Text(
                        row.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = mutedText,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                                .wrapContentWidth(Alignment.CenterHorizontally),
                    )
                is Line.Bubble -> Bubble(row.message, onRetry = onRetry, onDelete = onDelete)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(
    message: Message,
    onRetry: (Message) -> Unit,
    onDelete: (Message) -> Unit,
) {
    val incoming = message.isIncoming
    var menu by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = if (incoming) Arrangement.Start else Arrangement.End,
    ) {
        Column(horizontalAlignment = if (incoming) Alignment.Start else Alignment.End) {
            Box {
                Surface(
                    color =
                        when {
                            message.isFailed -> MaterialTheme.colorScheme.errorContainer
                            incoming -> MaterialTheme.colorScheme.surfaceContainerHigh
                            else -> MaterialTheme.colorScheme.primaryContainer
                        },
                    shape = RoundedCornerShape(18.dp),
                    modifier =
                        Modifier
                            .widthIn(max = 300.dp)
                            .combinedClickable(
                                onClick = { if (message.isFailed) onRetry(message) },
                                onLongClick = { menu = true },
                            ),
                ) {
                    Text(
                        message.body,
                        fontFamily = fontFor(message.body),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Copy") }, onClick = {
                        menu = false
                        scope.launch {
                            clipboard.setClipEntry(
                                ClipEntry(ClipData.newPlainText("Message", message.body)),
                            )
                        }
                    })
                    if (message.isFailed) {
                        DropdownMenuItem(text = { Text("Send again") }, onClick = {
                            menu = false
                            onRetry(message)
                        })
                    }
                    DropdownMenuItem(text = { Text("Delete") }, onClick = {
                        menu = false
                        onDelete(message)
                    })
                }
            }
            Text(
                status(message),
                style = MaterialTheme.typography.labelSmall,
                color = if (message.isFailed) MaterialTheme.colorScheme.error else mutedText,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

private fun status(message: Message): String {
    val time = clockTime(message.date)
    return when {
        message.isIncoming -> time
        message.isFailed -> "Not sent · tap to try again"
        message.isPending -> "Sending…"
        message.isDelivered -> "$time · Delivered"
        else -> time
    }
}

@Composable
private fun Composer(
    draft: String,
    onDraft: (String) -> Unit,
    sims: List<Sim>,
    sim: Int,
    onSim: (Int) -> Unit,
    onSend: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(
            Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(8.dp),
        ) {
            // Only a phone with two SIMs has a choice to make.
            if (sims.size > 1) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 4.dp),
                ) {
                    sims.forEach { candidate ->
                        FilterChip(
                            selected = candidate.subscriptionId == sim,
                            onClick = { onSim(candidate.subscriptionId) },
                            label = { Text(candidate.label) },
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = onDraft,
                    placeholder = { Text("Message") },
                    maxLines = 6,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onSend, enabled = draft.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, "Send")
                }
            }
        }
    }
}
