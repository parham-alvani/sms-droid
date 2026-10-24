package me.parham1995.sms.core.backup

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Reads and writes one backup file, and merges the phone's messages into it.
 *
 * **The merge only adds.** A message that is in the file stays in the file,
 * even when the phone no longer has it: deleting a message on the phone does
 * not delete it from the backup. That is what a backup is for, and it also
 * makes every run safe to repeat -- a run that is interrupted, or that runs
 * twice, writes the same file.
 */
object BackupFile {
    private val json =
        Json {
            // Lines written by a newer version may carry fields this one does
            // not know; they must still read.
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    /** A line that is not a message. The file is left alone, so a bad line is never written over. */
    class Corrupt(
        val path: String,
        val line: Int,
        cause: Throwable,
    ) : Exception("$path:$line is not a backup line: ${cause.message}", cause)

    fun parse(
        path: String,
        text: String,
    ): List<BackupMessage> =
        text
            .lineSequence()
            .withIndex()
            .filter { (_, line) -> line.isNotBlank() }
            .map { (index, line) ->
                try {
                    json.decodeFromString(BackupMessage.serializer(), line)
                } catch (e: SerializationException) {
                    throw Corrupt(path, index + 1, e)
                } catch (e: IllegalArgumentException) {
                    throw Corrupt(path, index + 1, e)
                } catch (e: java.time.format.DateTimeParseException) {
                    throw Corrupt(path, index + 1, e)
                }
            }.toList()

    fun format(messages: Collection<BackupMessage>): String =
        buildString {
            for (message in messages.sortedWith(BackupMessage.ORDER)) {
                append(json.encodeToString(BackupMessage.serializer(), message))
                append('\n')
            }
        }

    /** The outcome of merging the phone's messages into one file. */
    data class Merged(
        /** The new file content, or null when the file already says all of it. */
        val text: String?,
        val added: Int,
    )

    fun merge(
        path: String,
        existing: String?,
        phone: Collection<BackupMessage>,
    ): Merged {
        val kept = existing?.let { parse(path, it) }.orEmpty()
        val known = kept.mapTo(HashSet()) { it.key }
        val fresh = phone.filter { known.add(it.key) }
        if (fresh.isEmpty()) return Merged(null, 0)
        return Merged(format(kept + fresh), fresh.size)
    }
}
