package me.parham1995.sms.core.backup

import me.parham1995.sms.core.Addresses

/**
 * Where a message goes in the backup repository: one file per address, under
 * [DIRECTORY], so `git log -- messages/+989121234567.jsonl` is one person's
 * history. Ads (by `AdRule`) go to a file of the same name under [ADS], and
 * what blocked senders sent (by `BlockRule`) under [BLOCKED], out of the way
 * of the messages; restore reads only [DIRECTORY].
 *
 * A number is filed under its normalized form, so the national and the
 * international spelling of one number share a file. A sender name is filed
 * in lower case with everything but letters, digits, `.`, `+` and `-` turned
 * into `_`: macOS and Windows checkouts are case-insensitive, and
 * `Hamrahaval.jsonl` next to `HAMRAHAVAL.jsonl` would be one file there and
 * two in git. Each line keeps the address exactly as it came, so nothing is
 * lost by merging two spellings into one file.
 */
object BackupLayout {
    const val DIRECTORY = "messages"
    const val ADS = "ads"
    const val BLOCKED = "blocked"

    /** The call log, by `CallPlan`; not messages, so [nameOf] does not know it. */
    const val CALLS = "calls"
    const val EXTENSION = ".jsonl"

    private val UNSAFE = Regex("""[^\p{L}\p{N}.+\-]+""")
    private const val MAX_NAME = 100

    fun pathFor(
        address: String,
        callingCode: String,
    ): String = messagesPath(fileName(address, callingCode))

    fun messagesPath(name: String): String = "$DIRECTORY/$name$EXTENSION"

    fun adsPath(name: String): String = "$ADS/$name$EXTENSION"

    fun blockedPath(name: String): String = "$BLOCKED/$name$EXTENSION"

    fun callsPath(name: String): String = "$CALLS/$name$EXTENSION"

    internal fun fileName(
        address: String,
        callingCode: String,
    ): String {
        val name =
            if (Addresses.isPhoneNumber(address)) {
                Addresses.normalize(address, callingCode)
            } else {
                address
                    .trim()
                    .lowercase()
                    .replace(UNSAFE, "_")
                    .trim('_', '.')
            }
        return name.take(MAX_NAME).ifEmpty { "unknown" }
    }

    fun isBackupFile(path: String): Boolean = path.startsWith("$DIRECTORY/") && path.endsWith(EXTENSION)

    /** The file name a path in [DIRECTORY], [ADS] or [BLOCKED] is for, or null for any other path. */
    fun nameOf(path: String): String? {
        if (!path.endsWith(EXTENSION)) return null
        val name =
            when {
                path.startsWith("$DIRECTORY/") -> path.removePrefix("$DIRECTORY/")
                path.startsWith("$ADS/") -> path.removePrefix("$ADS/")
                path.startsWith("$BLOCKED/") -> path.removePrefix("$BLOCKED/")
                else -> return null
            }.removeSuffix(EXTENSION)
        return name.takeIf { it.isNotEmpty() && '/' !in it }
    }
}
