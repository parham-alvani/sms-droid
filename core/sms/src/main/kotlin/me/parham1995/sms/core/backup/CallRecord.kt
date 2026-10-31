package me.parham1995.sms.core.backup

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * One call from the phone's call log as the backup keeps it: one JSON object
 * on one line, in `calls/<number>.jsonl` next to the messages.
 *
 * ```json
 * {"at":"2026-10-31T13:02:11.480Z","type":"missed","number":"+989422000176"}
 * {"at":"2026-10-31T13:40:05.002Z","type":"in","number":"09121234567","seconds":312}
 * ```
 *
 * Who, when, which way and how long; the call log's row id and account are
 * this phone's bookkeeping. A hidden number is an empty [number].
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class CallRecord(
    @Serializable(with = InstantSerializer::class)
    val at: Instant,
    val type: Type,
    /** As the call log has it, not normalized. */
    val number: String,
    /** How long it was talked; missed, rejected and blocked calls have none. */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val seconds: Long = 0,
) {
    /** `CallLog.Calls.TYPE`, by name. */
    @Serializable
    enum class Type {
        @SerialName("in")
        IN,

        @SerialName("out")
        OUT,

        @SerialName("missed")
        MISSED,

        @SerialName("voicemail")
        VOICEMAIL,

        @SerialName("rejected")
        REJECTED,

        @SerialName("blocked")
        BLOCKED,

        @SerialName("answered-elsewhere")
        ANSWERED_ELSEWHERE,
    }

    /** What makes two lines the same call. Not [seconds]: a line is written once the call is over. */
    data class Key(
        val at: Long,
        val type: Type,
        val number: String,
    )

    val key: Key get() = Key(at.toEpochMilli(), type, number)

    companion object {
        val ORDER: Comparator<CallRecord> =
            compareBy<CallRecord> { it.at }
                .thenBy { it.type }
                .thenBy { it.number }
    }
}

/**
 * The call log's side of the backup, as pure functions like [BackupPlan]:
 * one file per number under [BackupLayout.CALLS], filed by the messages'
 * rule, so `calls/+989121234567.jsonl` sits beside that person's messages.
 *
 * It only adds, as the messages do: a call cleared from the phone's log stays
 * in git. There is no restore; putting calls back needs the dialer's write
 * permission, which this app does not ask for.
 */
object CallPlan {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    fun parse(
        path: String,
        text: String,
    ): List<CallRecord> =
        text
            .lineSequence()
            .withIndex()
            .filter { (_, line) -> line.isNotBlank() }
            .map { (index, line) ->
                try {
                    json.decodeFromString(CallRecord.serializer(), line)
                } catch (e: SerializationException) {
                    throw BackupFile.Corrupt(path, index + 1, e)
                } catch (e: IllegalArgumentException) {
                    throw BackupFile.Corrupt(path, index + 1, e)
                } catch (e: java.time.format.DateTimeParseException) {
                    throw BackupFile.Corrupt(path, index + 1, e)
                }
            }.toList()

    fun format(calls: Collection<CallRecord>): String =
        buildString {
            for (call in calls.sortedWith(CallRecord.ORDER)) {
                append(json.encodeToString(CallRecord.serializer(), call))
                append('\n')
            }
        }

    /**
     * Merges the phone's [calls] into the repository. [read] returns a
     * file's current text, or null when it does not exist. Only files that
     * gain a call are written.
     */
    fun backup(
        calls: Collection<CallRecord>,
        callingCode: String,
        read: (path: String) -> String?,
    ): List<BackupPlan.Write> =
        calls
            .groupBy { BackupLayout.callsPath(BackupLayout.fileName(it.number, callingCode)) }
            .toSortedMap()
            .mapNotNull { (path, phone) ->
                val kept = read(path)?.let { parse(path, it) }.orEmpty()
                val known = kept.mapTo(HashSet()) { it.key }
                val fresh = phone.filter { known.add(it.key) }
                if (fresh.isEmpty()) null else BackupPlan.Write(path, format(kept + fresh), fresh.size)
            }
}
