package me.parham1995.sms.messaging

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.provider.Telephony.Sms
import android.telephony.SmsMessage
import android.telephony.SubscriptionManager
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.parham1995.sms.data.ApplicationScope
import me.parham1995.sms.data.backup.BackupWorker
import me.parham1995.sms.data.sms.SmsStore
import javax.inject.Inject

/** Runs [block] off the main thread and keeps the receiver alive until it ends. */
private fun BroadcastReceiver.work(
    scope: CoroutineScope,
    block: suspend () -> Unit,
) {
    val pending = goAsync()
    scope.launch {
        try {
            block()
        } catch (e: Exception) {
            Log.e("Receiver", "${this@work::class.simpleName} failed", e)
        } finally {
            pending.finish()
        }
    }
}

/**
 * A message arrived. The default SMS app is the only one that gets this, and
 * it is the app's job to store it: if this does not write the row, the
 * message is gone.
 */
@AndroidEntryPoint
class SmsDeliverReceiver : BroadcastReceiver() {
    @Inject lateinit var store: SmsStore

    @Inject lateinit var notifications: Notifications

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val parts =
            Telephony.Sms.Intents
                .getMessagesFromIntent(intent)
                .orEmpty()
                .filterNotNull()
        if (parts.isEmpty()) return
        val subscriptionId =
            intent.getIntExtra(
                SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
                SubscriptionManager.INVALID_SUBSCRIPTION_ID,
            )
        work(scope) {
            // One intent is one message, split into parts by the network; the
            // parts share a sender.
            val address = parts.first().displayOriginatingAddress.orEmpty()
            val body = parts.joinToString("") { it.displayMessageBody.orEmpty() }
            val uri =
                store.insertIncoming(
                    address = address,
                    body = body,
                    date = System.currentTimeMillis(),
                    dateSent = parts.first().timestampMillis,
                    subscriptionId = subscriptionId,
                )
            val threadId = uri?.let { store.message(it)?.threadId } ?: store.threadIdFor(address)
            notifications.showIncoming(threadId)
            BackupWorker.scheduleSoon(context)
        }
    }
}

/** Required for the SMS role. This phone's network has no MMS, so there is nothing to do with it. */
class MmsPushReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        Log.i("MmsPushReceiver", "dropped an MMS notification; MMS is not supported")
    }
}

/** The radio's answer for one part of a sent message. */
@AndroidEntryPoint
class SentReceiver : BroadcastReceiver() {
    @Inject lateinit var store: SmsStore

    @Inject lateinit var notifications: Notifications

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val uri = intent.data ?: return
        val ok = resultCode == Activity.RESULT_OK
        work(scope) {
            val message = store.message(uri) ?: return@work
            when {
                // One failed part fails the message; a later part that went
                // through must not mark it sent again.
                !ok -> {
                    store.setType(uri, Sms.MESSAGE_TYPE_FAILED)
                    notifications.showSendFailed(message.threadId)
                }
                message.isPending -> store.setType(uri, Sms.MESSAGE_TYPE_SENT)
            }
        }
    }
}

/** The network's delivery report, when delivery reports are on. */
@AndroidEntryPoint
class DeliveredReceiver : BroadcastReceiver() {
    @Inject lateinit var store: SmsStore

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val uri = intent.data ?: return
        val pdu = intent.getByteArrayExtra("pdu") ?: return
        val report = SmsMessage.createFromPdu(pdu, intent.getStringExtra("format")) ?: return
        // TP-Status (3GPP TS 23.040 9.2.3.15): below 0x20 delivered, below
        // 0x40 still trying, the rest failed.
        val status =
            when {
                report.status < TEMPORARY -> Sms.STATUS_COMPLETE
                report.status < PERMANENT -> Sms.STATUS_PENDING
                else -> Sms.STATUS_FAILED
            }
        work(scope) { store.setDelivery(uri, status) }
    }

    private companion object {
        const val TEMPORARY = 0x20
        const val PERMANENT = 0x40
    }
}
