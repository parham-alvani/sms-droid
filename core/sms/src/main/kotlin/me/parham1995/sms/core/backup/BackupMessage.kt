package me.parham1995.sms.core.backup

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * One message as the backup keeps it: one JSON object on one line.
 *
 * ```json
 * {"at":"2026-10-24T06:45:03.120Z","direction":"in","address":"ResalatBank","body":"…","sentAt":"2026-10-24T06:45:01Z"}
 * ```
 *
 * Only what is needed to put the message back: who, when, which way, and the
 * text. The provider's row id, thread id and subscription id are this phone's
 * bookkeeping and mean nothing on another one. Times are UTC instants, so a
 * line reads the same in any time zone and parses back to the millisecond.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BackupMessage(
    /** When the phone received or sent it: the provider's `date`. */
    @Serializable(with = InstantSerializer::class)
    val at: Instant,
    val direction: Direction,
    /** As the provider has it, not normalized: a restore writes it back unchanged. */
    val address: String,
    val body: String,
    /** The sender's own timestamp on an incoming message, when the network gave one. */
    @Serializable(with = InstantSerializer::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val sentAt: Instant? = null,
) {
    @Serializable
    enum class Direction {
        @SerialName("in")
        IN,

        @SerialName("out")
        OUT,
    }

    /**
     * What makes two lines the same message. Not [sentAt]: it is missing on
     * some networks, and a message must not be stored twice because one copy
     * has it.
     */
    data class Key(
        val at: Long,
        val direction: Direction,
        val address: String,
        val body: String,
    )

    val key: Key get() = Key(at.toEpochMilli(), direction, address, body)

    companion object {
        /** File order: oldest first, then a stable tie-break so a rewrite never reorders lines. */
        val ORDER: Comparator<BackupMessage> =
            compareBy<BackupMessage> { it.at }
                .thenBy { it.direction }
                .thenBy { it.address }
                .thenBy { it.body }
    }
}
