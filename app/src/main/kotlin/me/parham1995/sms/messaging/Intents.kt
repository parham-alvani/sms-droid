package me.parham1995.sms.messaging

import android.net.Uri

/**
 * Reads `sms:` and `smsto:` links, as other apps and web pages send them:
 * `sms:+989121234567`, `smsto:0912 123 4567;0935…`, `sms:+98912…?body=Hi`.
 */
object Intents {
    private val SCHEMES = setOf("sms", "smsto", "mms", "mmsto")

    /** The recipients, in order. A link may name several, split by `,` or `;`. */
    fun recipients(uri: Uri): List<String> {
        if (uri.scheme?.lowercase() !in SCHEMES) return emptyList()
        return recipients(uri.schemeSpecificPart.orEmpty())
    }

    /** The text after `?body=`, if the link carries one. */
    fun body(uri: Uri): String? =
        // Encoded, and decoded once below: an `&` or `%` typed in the body
        // must not split or decode again.
        uri.encodedSchemeSpecificPart
            ?.substringAfter('?', "")
            ?.split('&')
            ?.firstOrNull { it.startsWith("body=") }
            ?.removePrefix("body=")
            ?.let(Uri::decode)
            ?.takeIf { it.isNotEmpty() }

    internal fun recipients(schemeSpecificPart: String): List<String> =
        schemeSpecificPart
            .substringBefore('?')
            .split(',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}
