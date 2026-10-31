package me.parham1995.sms.data.calls

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog.Calls
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.parham1995.sms.core.backup.CallRecord
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The phone's call log, read for the backup. Reading it needs
 * `READ_CALL_LOG`, which settings asks for when the call log backup is
 * turned on; without it this reads nothing.
 */
@Singleton
class CallLogStore
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun isAllowed(): Boolean =
            context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED

        /** Every call the log has, oldest first. Types the backup does not know are left out. */
        suspend fun forBackup(): List<CallRecord> =
            withContext(Dispatchers.IO) {
                if (!isAllowed()) return@withContext emptyList()
                buildList {
                    context.contentResolver
                        .query(
                            Calls.CONTENT_URI,
                            arrayOf(Calls.DATE, Calls.TYPE, Calls.NUMBER, Calls.DURATION),
                            null,
                            null,
                            "${Calls.DATE} ASC",
                        )?.use { cursor ->
                            while (cursor.moveToNext()) {
                                val type = typeOf(cursor.getInt(1)) ?: continue
                                add(
                                    CallRecord(
                                        at = Instant.ofEpochMilli(cursor.getLong(0)),
                                        type = type,
                                        number = cursor.getString(2).orEmpty(),
                                        seconds = cursor.getLong(3),
                                    ),
                                )
                            }
                        }
                }
            }

        private fun typeOf(type: Int): CallRecord.Type? =
            when (type) {
                Calls.INCOMING_TYPE -> CallRecord.Type.IN
                Calls.OUTGOING_TYPE -> CallRecord.Type.OUT
                Calls.MISSED_TYPE -> CallRecord.Type.MISSED
                Calls.VOICEMAIL_TYPE -> CallRecord.Type.VOICEMAIL
                Calls.REJECTED_TYPE -> CallRecord.Type.REJECTED
                Calls.BLOCKED_TYPE -> CallRecord.Type.BLOCKED
                Calls.ANSWERED_EXTERNALLY_TYPE -> CallRecord.Type.ANSWERED_ELSEWHERE
                else -> null
            }
    }
