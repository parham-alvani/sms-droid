package me.parham1995.sms.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Contact photos, decoded once and kept in memory.
 *
 * The contacts database hands out thumbnail URIs (96 px), so there is nothing
 * to scale and no need for an image library. Software bitmaps, because the
 * same bitmap goes into a notification, which is parcelled to the system.
 */
object ContactPhotos {
    private const val ENTRIES = 300
    private val cache = LruCache<String, Bitmap>(ENTRIES)

    fun cached(uri: String?): Bitmap? = uri?.let(cache::get)

    /** The photo at [uri], or null when there is none or it does not decode. Call off the main thread. */
    fun load(
        context: Context,
        uri: String?,
    ): Bitmap? {
        if (uri == null) return null
        cache.get(uri)?.let { return it }
        return runCatching {
            ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(context.contentResolver, uri.toUri()),
            ) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }.getOrNull()?.also { cache.put(uri, it) }
    }
}

@Composable
fun rememberContactPhoto(uri: String?): ImageBitmap? {
    val context = LocalContext.current
    val photo by produceState(ContactPhotos.cached(uri)?.asImageBitmap(), uri) {
        if (value == null && uri != null) {
            value = withContext(Dispatchers.IO) { ContactPhotos.load(context, uri) }?.asImageBitmap()
        }
    }
    return photo
}
