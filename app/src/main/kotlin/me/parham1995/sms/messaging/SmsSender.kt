package me.parham1995.sms.messaging

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony.Sms
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import me.parham1995.sms.data.SettingsStore
import me.parham1995.sms.data.backup.BackupWorker
import me.parham1995.sms.data.sms.Message
import me.parham1995.sms.data.sms.SmsStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends a message: into the outbox first, then to the radio.
 *
 * The provider row is written before the radio is asked, so a message that
 * never gets an answer still shows -- as pending, then as failed -- rather
 * than vanishing. [SentReceiver] moves it to sent or failed; with delivery
 * reports on, [DeliveredReceiver] records what the network said.
 *
 * A long message goes as one multipart SMS, which the receiving phone joins.
 */
@Singleton
class SmsSender
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val store: SmsStore,
        private val settings: SettingsStore,
    ) {
        suspend fun send(
            address: String,
            body: String,
            subscriptionId: Int,
        ): Uri {
            require(body.isNotBlank()) { "an empty message" }
            val uri = store.insertOutgoing(address, body, subscriptionId)
            try {
                val manager = manager(subscriptionId)
                val parts = manager.divideMessage(body)
                val reports = settings.current().deliveryReports
                val sent = ArrayList(parts.indices.map { part -> statusIntent<SentReceiver>(uri, part) })
                val delivered =
                    ArrayList(
                        parts.indices.map { part ->
                            // The network reports on the whole message once, against its last part.
                            if (reports && part == parts.lastIndex) statusIntent<DeliveredReceiver>(uri, part) else null
                        },
                    )
                manager.sendMultipartTextMessage(address, null, parts, sent, delivered)
            } catch (e: IllegalArgumentException) {
                store.setType(uri, Sms.MESSAGE_TYPE_FAILED)
                throw e
            } catch (e: SecurityException) {
                store.setType(uri, Sms.MESSAGE_TYPE_FAILED)
                throw e
            }
            BackupWorker.scheduleSoon(context)
            return uri
        }

        /** Sends a failed message again, as a new message in its place. */
        suspend fun resend(message: Message) {
            store.delete(message.id)
            send(message.address, message.body, message.subscriptionId)
        }

        private fun manager(subscriptionId: Int): SmsManager {
            val manager = context.getSystemService(SmsManager::class.java)
            return if (SubscriptionManager.isUsableSubscriptionId(subscriptionId)) {
                manager.createForSubscriptionId(subscriptionId)
            } else {
                manager
            }
        }

        /**
         * One pending intent per part: the message's URI is the intent's data,
         * so parts of different messages never collapse into one, and the part
         * index is the request code, so parts of one message do not either.
         * Mutable, because the radio adds the error code to it.
         */
        private inline fun <reified T> statusIntent(
            uri: Uri,
            part: Int,
        ): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                part,
                Intent(context, T::class.java).setData(uri),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
    }
