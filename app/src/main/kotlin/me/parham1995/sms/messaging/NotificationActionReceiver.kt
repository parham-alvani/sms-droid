package me.parham1995.sms.messaging

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import android.util.Log
import androidx.core.app.RemoteInput
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.parham1995.sms.data.ApplicationScope
import me.parham1995.sms.data.sms.SmsStore
import javax.inject.Inject

/** The buttons on a message notification: reply, mark as read, copy the code. */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {
    @Inject lateinit var store: SmsStore

    @Inject lateinit var sender: SmsSender

    @Inject lateinit var sims: Sims

    @Inject lateinit var notifications: Notifications

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val threadId = intent.getLongExtra(EXTRA_THREAD, -1)
        val address = intent.getStringExtra(EXTRA_ADDRESS)
        if (threadId < 0 || address == null) return
        when (intent.action) {
            COPY_CODE -> {
                intent.getStringExtra(EXTRA_CODE)?.let { copy(context, it) }
                return
            }
            MARK_READ, REPLY -> Unit
            else -> return
        }
        val reply =
            RemoteInput
                .getResultsFromIntent(intent)
                ?.getCharSequence(EXTRA_REPLY)
                ?.toString()
                ?.takeIf { it.isNotBlank() }
        val pending = goAsync()
        scope.launch {
            try {
                if (reply != null) {
                    // The SIM the conversation came in on, so a reply to a
                    // message on the second SIM does not go out on the first.
                    val sim =
                        store
                            .messages(threadId)
                            .lastOrNull { it.isIncoming }
                            ?.subscriptionId
                            ?.takeIf { id -> sims.active().any { it.subscriptionId == id } }
                            ?: sims.defaultForSms()
                    sender.send(address, reply, sim)
                }
                store.markRead(threadId)
                notifications.cancel(threadId)
            } catch (e: Exception) {
                Log.e(TAG, "notification action ${intent.action} failed", e)
                notifications.showSendFailed(threadId)
            } finally {
                pending.finish()
            }
        }
    }

    private fun copy(
        context: Context,
        code: String,
    ) {
        val clip = ClipData.newPlainText("Code", code)
        // A one-time code is a secret for a minute: keep it out of the clipboard's preview.
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    }

    companion object {
        private const val TAG = "NotificationAction"
        const val REPLY = "me.parham1995.sms.REPLY"
        const val MARK_READ = "me.parham1995.sms.MARK_READ"
        const val COPY_CODE = "me.parham1995.sms.COPY_CODE"
        const val EXTRA_THREAD = "thread"
        const val EXTRA_ADDRESS = "address"
        const val EXTRA_REPLY = "reply"
        const val EXTRA_CODE = "code"
    }
}
