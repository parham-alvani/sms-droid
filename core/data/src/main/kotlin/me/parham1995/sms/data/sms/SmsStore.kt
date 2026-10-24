package me.parham1995.sms.data.sms

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony.Sms
import android.provider.Telephony.Threads
import androidx.core.content.contentValuesOf
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import me.parham1995.sms.core.backup.BackupMessage
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The system's SMS provider, read and written directly.
 *
 * Fossify Messages copies every message into a Room database of its own and
 * spends a lot of code keeping the two in step. Here the provider is the only
 * copy: screens query it and [changes] tells them when to query again. Only
 * the default SMS app may write to it, and only an app holding `READ_SMS`
 * may read it; both come with the SMS role.
 */
@Singleton
class SmsStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        private val resolver get() = context.contentResolver

        /** Emits once at once, then whenever any message or thread changes. */
        fun changes(): Flow<Unit> =
            callbackFlow {
                val observer =
                    object : ContentObserver(Handler(Looper.getMainLooper())) {
                        override fun onChange(selfChange: Boolean) {
                            trySend(Unit)
                        }
                    }
                // The SMS provider notifies its own URI; the threads and
                // conversations views notify the combined one.
                resolver.registerContentObserver(Sms.CONTENT_URI, true, observer)
                resolver.registerContentObserver(MMS_SMS, true, observer)
                trySend(Unit)
                awaitClose { resolver.unregisterContentObserver(observer) }
            }.conflate()

        /** One row per thread, newest first. */
        suspend fun conversations(): List<Conversation> =
            withContext(Dispatchers.IO) {
                val byThread = LinkedHashMap<Long, Conversation>()
                query(Sms.CONTENT_URI, null, null, "${Sms.DATE} DESC") { message ->
                    val known = byThread[message.threadId]
                    val unread = if (message.isIncoming && !message.read) 1 else 0
                    byThread[message.threadId] =
                        known?.copy(unread = known.unread + unread)
                            ?: Conversation(
                                threadId = message.threadId,
                                address = message.address,
                                snippet = message.body,
                                date = message.date,
                                unread = unread,
                                lastFailed = message.isFailed,
                            )
                }
                byThread.values.toList()
            }

        /** A thread's messages, oldest first. */
        suspend fun messages(threadId: Long): List<Message> =
            withContext(Dispatchers.IO) {
                buildList {
                    query(Sms.CONTENT_URI, "${Sms.THREAD_ID} = ?", arrayOf(threadId.toString()), "${Sms.DATE} ASC") {
                        add(it)
                    }
                }
            }

        suspend fun message(uri: Uri): Message? =
            withContext(Dispatchers.IO) {
                var found: Message? = null
                query(uri, null, null, null) { found = it }
                found
            }

        /** The thread for [address], created when there is none yet. */
        suspend fun threadIdFor(address: String): Long =
            withContext(Dispatchers.IO) { Threads.getOrCreateThreadId(context, address) }

        /** Stores a message that arrived. Called by the default SMS app, which is the only one that may. */
        suspend fun insertIncoming(
            address: String,
            body: String,
            date: Long,
            dateSent: Long,
            subscriptionId: Int,
        ): Uri? =
            withContext(Dispatchers.IO) {
                resolver.insert(
                    Sms.Inbox.CONTENT_URI,
                    contentValuesOf(
                        Sms.ADDRESS to address,
                        Sms.BODY to body,
                        Sms.DATE to date,
                        Sms.DATE_SENT to dateSent,
                        Sms.READ to 0,
                        Sms.SEEN to 0,
                        Sms.SUBSCRIPTION_ID to subscriptionId,
                    ),
                )
            }

        /** Stores a message about to be sent, in the outbox; the sent receiver moves it on. */
        suspend fun insertOutgoing(
            address: String,
            body: String,
            subscriptionId: Int,
        ): Uri =
            withContext(Dispatchers.IO) {
                checkNotNull(
                    resolver.insert(
                        Sms.CONTENT_URI,
                        contentValuesOf(
                            Sms.ADDRESS to address,
                            Sms.BODY to body,
                            Sms.DATE to System.currentTimeMillis(),
                            Sms.READ to 1,
                            Sms.SEEN to 1,
                            Sms.TYPE to Sms.MESSAGE_TYPE_OUTBOX,
                            Sms.SUBSCRIPTION_ID to subscriptionId,
                        ),
                    ),
                ) { "the SMS provider refused the message" }
            }

        suspend fun setType(
            uri: Uri,
            type: Int,
        ) = update(uri, contentValuesOf(Sms.TYPE to type))

        suspend fun setDelivery(
            uri: Uri,
            status: Int,
        ) = update(uri, contentValuesOf(Sms.STATUS to status))

        suspend fun markRead(threadId: Long) =
            withContext(Dispatchers.IO) {
                resolver.update(
                    Sms.CONTENT_URI,
                    contentValuesOf(Sms.READ to 1, Sms.SEEN to 1),
                    "${Sms.THREAD_ID} = ? AND ${Sms.READ} = 0",
                    arrayOf(threadId.toString()),
                )
            }

        suspend fun delete(messageId: Long) =
            withContext(Dispatchers.IO) {
                resolver.delete(Uri.withAppendedPath(Sms.CONTENT_URI, messageId.toString()), null, null)
            }

        /** Deletes a whole thread: its messages and the thread itself. Returns how many rows went. */
        suspend fun deleteThread(threadId: Long) =
            withContext(Dispatchers.IO) {
                resolver.delete(ContentUris.withAppendedId(Threads.CONTENT_URI, threadId), null, null)
            }

        /** Every message worth keeping, as the backup writes it: received and sent, not drafts or failures. */
        suspend fun forBackup(): List<BackupMessage> =
            withContext(Dispatchers.IO) {
                buildList {
                    query(
                        Sms.CONTENT_URI,
                        "${Sms.TYPE} IN (?, ?)",
                        arrayOf(Sms.MESSAGE_TYPE_INBOX.toString(), Sms.MESSAGE_TYPE_SENT.toString()),
                        "${Sms.DATE} ASC",
                    ) { add(it.toBackup()) }
                }
            }

        /** Puts messages from the backup back, as read. Returns how many the provider took. */
        suspend fun restore(messages: List<BackupMessage>): Int =
            withContext(Dispatchers.IO) {
                messages.chunked(RESTORE_CHUNK).sumOf { chunk ->
                    resolver.bulkInsert(Sms.CONTENT_URI, chunk.map { it.toValues() }.toTypedArray())
                }
            }

        private suspend fun update(
            uri: Uri,
            values: ContentValues,
        ) = withContext(Dispatchers.IO) { resolver.update(uri, values, null, null) }

        private inline fun query(
            uri: Uri,
            selection: String?,
            args: Array<String>?,
            order: String?,
            each: (Message) -> Unit,
        ) {
            resolver.query(uri, PROJECTION, selection, args, order)?.use { cursor ->
                val columns = Columns(cursor)
                while (cursor.moveToNext()) each(columns.read(cursor))
            }
        }

        private class Columns(
            cursor: Cursor,
        ) {
            private val id = cursor.getColumnIndexOrThrow(Sms._ID)
            private val thread = cursor.getColumnIndexOrThrow(Sms.THREAD_ID)
            private val address = cursor.getColumnIndexOrThrow(Sms.ADDRESS)
            private val body = cursor.getColumnIndexOrThrow(Sms.BODY)
            private val date = cursor.getColumnIndexOrThrow(Sms.DATE)
            private val dateSent = cursor.getColumnIndexOrThrow(Sms.DATE_SENT)
            private val type = cursor.getColumnIndexOrThrow(Sms.TYPE)
            private val status = cursor.getColumnIndexOrThrow(Sms.STATUS)
            private val read = cursor.getColumnIndexOrThrow(Sms.READ)
            private val subscription = cursor.getColumnIndexOrThrow(Sms.SUBSCRIPTION_ID)

            fun read(cursor: Cursor) =
                Message(
                    id = cursor.getLong(id),
                    threadId = cursor.getLong(thread),
                    address = cursor.getString(address).orEmpty(),
                    body = cursor.getString(body).orEmpty(),
                    date = cursor.getLong(date),
                    dateSent = cursor.getLong(dateSent),
                    type = cursor.getInt(type),
                    status = cursor.getInt(status),
                    read = cursor.getInt(read) != 0,
                    subscriptionId = cursor.getInt(subscription),
                )
        }

        private fun Message.toBackup() =
            BackupMessage(
                at = Instant.ofEpochMilli(date),
                direction = if (isIncoming) BackupMessage.Direction.IN else BackupMessage.Direction.OUT,
                address = address,
                body = body,
                sentAt = if (isIncoming && dateSent > 0) Instant.ofEpochMilli(dateSent) else null,
            )

        private fun BackupMessage.toValues() =
            contentValuesOf(
                Sms.ADDRESS to address,
                Sms.BODY to body,
                Sms.DATE to at.toEpochMilli(),
                Sms.DATE_SENT to (sentAt?.toEpochMilli() ?: 0L),
                Sms.TYPE to
                    if (direction == BackupMessage.Direction.IN) Sms.MESSAGE_TYPE_INBOX else Sms.MESSAGE_TYPE_SENT,
                Sms.READ to 1,
                Sms.SEEN to 1,
            )

        private companion object {
            val MMS_SMS: Uri = "content://mms-sms/".toUri()
            const val RESTORE_CHUNK = 200

            val PROJECTION =
                arrayOf(
                    Sms._ID,
                    Sms.THREAD_ID,
                    Sms.ADDRESS,
                    Sms.BODY,
                    Sms.DATE,
                    Sms.DATE_SENT,
                    Sms.TYPE,
                    Sms.STATUS,
                    Sms.READ,
                    Sms.SUBSCRIPTION_ID,
                )
        }
    }

/** A thread as the conversation list shows it: the newest message, and how many are unread. */
data class Conversation(
    val threadId: Long,
    val address: String,
    val snippet: String,
    val date: Long,
    val unread: Int,
    val lastFailed: Boolean,
)

data class Message(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val dateSent: Long,
    val type: Int,
    val status: Int,
    val read: Boolean,
    val subscriptionId: Int,
) {
    val isIncoming: Boolean get() = type == Sms.MESSAGE_TYPE_INBOX
    val isFailed: Boolean get() = type == Sms.MESSAGE_TYPE_FAILED
    val isPending: Boolean get() = type == Sms.MESSAGE_TYPE_OUTBOX || type == Sms.MESSAGE_TYPE_QUEUED
    val isDelivered: Boolean get() = status == Sms.STATUS_COMPLETE
}
