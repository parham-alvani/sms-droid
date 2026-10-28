package me.parham1995.sms.feature.settings

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.parham1995.sms.BuildConfig
import me.parham1995.sms.data.BackupStatus
import me.parham1995.sms.data.Settings
import me.parham1995.sms.messaging.DefaultSmsApp
import me.parham1995.sms.ui.mutedText
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var confirmRestore by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val role =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.recheck() }
    LifecycleResumeEffect(Unit) {
        viewModel.recheck()
        onPauseOrDispose { }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restore from the backup?") },
            text = {
                Text(
                    "Messages in the backup that this phone does not have are added back, as read. " +
                        "Nothing on the phone is changed or removed.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    viewModel.restore()
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        val current = settings ?: return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!ui.isDefault) {
                Section("SMS app") {
                    Text("This is not the phone's SMS app, so it cannot read, receive or send messages.")
                    Button(onClick = { role.launch(DefaultSmsApp.request(context)) }) { Text("Make default") }
                }
            }

            Section("Messages") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Delivery reports")
                        Text(
                            "Ask the network to confirm each message arrived. Some operators charge for it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = mutedText,
                        )
                    }
                    Switch(checked = current.deliveryReports, onCheckedChange = viewModel::setDeliveryReports)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Delete ads")
                        Text(
                            "An ad is a message with the opt-out line لغو11 and no one-time code. It arrives " +
                                "without a notification and is deleted after the next backup, which keeps it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = mutedText,
                        )
                    }
                    Switch(checked = current.deleteAds, onCheckedChange = viewModel::setDeleteAds)
                }
                CallingCode(current, onSave = viewModel::saveCallingCode)
            }

            Section("Backup to git") {
                Text(
                    "Every message is written to a git repository over SSH: one file per conversation, " +
                        "added to and never trimmed. Use a private repository unless you want the messages public.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Repository(current, onSave = viewModel::saveRepository)
                DeployKey(ui.sshPublicKey, current.remoteUrl, busy = ui.busy, onGenerate = viewModel::generateKey)
                GitAuthor(current, onSave = viewModel::saveAuthor)
                Status(status)
                ui.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = viewModel::backUpNow,
                        enabled = !ui.busy && current.isConfigured && ui.sshPublicKey != null,
                    ) { Text("Back up now") }
                    OutlinedButton(
                        onClick = { confirmRestore = true },
                        enabled = !ui.busy && current.isConfigured && ui.sshPublicKey != null && ui.isDefault,
                    ) { Text("Restore") }
                }
            }

            Section("About") {
                Text("sms-droid ${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_SHA})")
                TextButton(
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, BuildConfig.REPOSITORY.toUri())) },
                ) {
                    Text(BuildConfig.REPOSITORY)
                }
            }
        }
    }
}

@Composable
private fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun CallingCode(
    settings: Settings,
    onSave: (String) -> Unit,
) {
    var code by rememberSaveable(settings.callingCode) { mutableStateOf(settings.callingCode) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = code,
            onValueChange = { code = it.filter(Char::isDigit) },
            label = { Text("Country code") },
            prefix = { Text("+") },
            supportingText = { Text("Makes 0912… and +98912… one number") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = { onSave(code) }, enabled = code != settings.callingCode) { Text("Save") }
    }
}

@Composable
private fun Repository(
    settings: Settings,
    onSave: (remoteUrl: String, branch: String) -> Unit,
) {
    var url by rememberSaveable(settings.remoteUrl) { mutableStateOf(settings.remoteUrl) }
    var branch by rememberSaveable(settings.branch) { mutableStateOf(settings.branch) }
    OutlinedTextField(
        value = url,
        onValueChange = { url = it },
        label = { Text("Repository") },
        placeholder = { Text("git@github.com:you/sms-backup.git") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = branch,
            onValueChange = { branch = it },
            label = { Text("Branch") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(
            onClick = { onSave(url, branch) },
            enabled = url.trim() != settings.remoteUrl || branch.trim() != settings.branch,
        ) { Text("Save") }
    }
}

@Composable
private fun DeployKey(
    publicKey: String?,
    remoteUrl: String,
    busy: Boolean,
    onGenerate: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Text("SSH deploy key", style = MaterialTheme.typography.labelLarge)
    if (publicKey == null) {
        Text(
            "Made on this phone; the private half never leaves it. Add the public half to the repository " +
                "as a deploy key and tick \"Allow write access\".",
            style = MaterialTheme.typography.bodySmall,
            color = mutedText,
        )
        OutlinedButton(onClick = onGenerate, enabled = !busy) { Text("Generate key") }
        return
    }
    SelectionContainer {
        Text(publicKey, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("SSH public key", publicKey))) }
        }) { Text("Copy") }
        SettingsViewModel.githubRepository(remoteUrl)?.let { repository ->
            OutlinedButton(onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, "https://github.com/$repository/settings/keys/new".toUri()),
                )
            }) { Text("Add on GitHub") }
        }
    }
}

/** Who the phone's commits are by. Left empty, they are by `sms-droid`. */
@Composable
private fun GitAuthor(
    settings: Settings,
    onSave: (name: String, email: String) -> Unit,
) {
    var name by rememberSaveable(settings.authorName) { mutableStateOf(settings.authorName) }
    var email by rememberSaveable(settings.authorEmail) { mutableStateOf(settings.authorEmail) }
    val emailValid = email.isBlank() || EMAIL.matches(email.trim())
    Text("Commit author", style = MaterialTheme.typography.labelLarge)
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Name") },
        placeholder = { Text("sms-droid") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text("Email") },
        placeholder = { Text("sms-droid@localhost") },
        singleLine = true,
        isError = !emailValid,
        supportingText = { if (!emailValid) Text("Not an email address") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedButton(
        onClick = { onSave(name.trim(), email.trim()) },
        enabled = emailValid && (name.trim() != settings.authorName || email.trim() != settings.authorEmail),
    ) { Text("Save author") }
}

@Composable
private fun Status(status: BackupStatus) {
    val last =
        if (status.lastSuccess == 0L) {
            "Never backed up"
        } else {
            val time =
                DateFormat
                    .getDateTimeInstance(
                        DateFormat.MEDIUM,
                        DateFormat.SHORT,
                    ).format(Date(status.lastSuccess))
            "Last backup $time, ${status.lastAdded} new messages"
        }
    Text(last, style = MaterialTheme.typography.bodyMedium, color = mutedText)
    status.lastError?.let { Text("Last attempt failed: $it", color = MaterialTheme.colorScheme.error) }
}

private val EMAIL = Regex("""^[^@\s]+@[^@\s]+$""")
