package me.parham1995.sms.messaging

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.parham1995.sms.MainActivity
import me.parham1995.sms.R
import me.parham1995.sms.core.OneTimeCodes
import me.parham1995.sms.data.contacts.ContactsSource
import me.parham1995.sms.data.sms.SmsStore
import me.parham1995.sms.ui.ContactPhotos
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One notification per conversation, showing its unread messages, with a
 * reply box, "Mark as read", and "Copy code" when the newest message carries
 * a one-time code.
 *
 * The notification id is the thread id, so a new message replaces the
 * conversation's notification instead of adding a second one, and opening
 * the conversation can cancel it by the same number.
 */
@Singleton
class Notifications
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val store: SmsStore,
        private val contacts: ContactsSource,
    ) {
        private val manager = NotificationManagerCompat.from(context)

        fun createChannels() {
            val system = context.getSystemService(NotificationManager::class.java)
            system.createNotificationChannel(
                NotificationChannel(MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH),
            )
            system.createNotificationChannel(
                NotificationChannel(FAILURES, "Messages not sent", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }

        suspend fun showIncoming(threadId: Long) {
            if (!allowed()) return
            val unread = store.messages(threadId).filter { it.isIncoming && !it.read }.takeLast(SHOWN)
            if (unread.isEmpty()) {
                cancel(threadId)
                return
            }
            val address = unread.last().address
            val contact = contacts.index.value.lookup(address)
            val name = contact?.name ?: address
            val photo = withContext(Dispatchers.IO) { ContactPhotos.load(context, contact?.photoUri) }
            val sender =
                Person
                    .Builder()
                    .setName(name)
                    .setKey(address)
                    .apply { photo?.let { setIcon(IconCompat.createWithBitmap(it)) } }
                    .build()
            val style = NotificationCompat.MessagingStyle(Person.Builder().setName("You").build())
            unread.forEach { style.addMessage(it.body, it.date, sender) }

            val builder =
                NotificationCompat
                    .Builder(context, MESSAGES)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setStyle(style)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                    .setContentIntent(openThread(threadId, address))
                    .setAutoCancel(true)
                    .addAction(replyAction(threadId, address))
                    .addAction(
                        NotificationCompat.Action
                            .Builder(0, "Mark as read", action(NotificationActionReceiver.MARK_READ, threadId, address))
                            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                            .setShowsUserInterface(false)
                            .build(),
                    )
            OneTimeCodes.find(unread.last().body)?.let { code ->
                builder.addAction(
                    NotificationCompat.Action.Builder(0, "Copy $code", codeAction(threadId, address, code)).build(),
                )
            }
            notify(threadId.toInt(), builder)
        }

        fun showSendFailed(threadId: Long) {
            if (!allowed()) return
            val builder =
                NotificationCompat
                    .Builder(context, FAILURES)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("Message not sent")
                    .setContentText("Open the conversation to try again")
                    .setContentIntent(openThread(threadId, null))
                    .setAutoCancel(true)
            notify(FAILURE_OFFSET + threadId.toInt(), builder)
        }

        fun cancel(threadId: Long) {
            manager.cancel(threadId.toInt())
            manager.cancel(FAILURE_OFFSET + threadId.toInt())
        }

        private fun allowed(): Boolean =
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        // Checked by allowed() at every entry point; lint cannot see through it.
        @Suppress("MissingPermission")
        private fun notify(
            id: Int,
            builder: NotificationCompat.Builder,
        ) {
            if (allowed()) manager.notify(id, builder.build())
        }

        private fun openThread(
            threadId: Long,
            address: String?,
        ): PendingIntent =
            PendingIntent.getActivity(
                context,
                threadId.toInt(),
                MainActivity.openThread(context, threadId, address),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun replyAction(
            threadId: Long,
            address: String,
        ): NotificationCompat.Action {
            val input = RemoteInput.Builder(NotificationActionReceiver.EXTRA_REPLY).setLabel("Reply").build()
            // Mutable: the system writes the typed reply into it.
            val intent =
                PendingIntent.getBroadcast(
                    context,
                    threadId.toInt(),
                    actionIntent(NotificationActionReceiver.REPLY, threadId, address),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
            return NotificationCompat.Action
                .Builder(0, "Reply", intent)
                .addRemoteInput(input)
                .setAllowGeneratedReplies(true)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                .setShowsUserInterface(false)
                .build()
        }

        private fun codeAction(
            threadId: Long,
            address: String,
            code: String,
        ): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                threadId.toInt(),
                actionIntent(NotificationActionReceiver.COPY_CODE, threadId, address)
                    .putExtra(NotificationActionReceiver.EXTRA_CODE, code),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun action(
            action: String,
            threadId: Long,
            address: String,
        ): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                threadId.toInt(),
                actionIntent(action, threadId, address),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun actionIntent(
            action: String,
            threadId: Long,
            address: String,
        ) = Intent(context, NotificationActionReceiver::class.java)
            .setAction(action)
            .putExtra(NotificationActionReceiver.EXTRA_THREAD, threadId)
            .putExtra(NotificationActionReceiver.EXTRA_ADDRESS, address)

        private companion object {
            const val MESSAGES = "messages"
            const val FAILURES = "failures"
            const val SHOWN = 6

            /** Failure notifications get their own ids, next to the thread's. */
            const val FAILURE_OFFSET = 1 shl 30
        }
    }
