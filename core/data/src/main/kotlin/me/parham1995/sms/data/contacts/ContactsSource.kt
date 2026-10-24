package me.parham1995.sms.data.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import me.parham1995.sms.core.ContactIndex
import me.parham1995.sms.data.ApplicationScope
import me.parham1995.sms.data.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every phone entry in the contacts database, as one [ContactIndex].
 *
 * Read in one query and matched in memory, instead of one `PhoneLookup` per
 * address: `PhoneLookup` is what cannot match a sender name, and a query per
 * conversation is slow on a long list anyway. A phone has a few thousand
 * entries at most, which is a few hundred kilobytes.
 *
 * Built again when the contacts change, when the calling code changes, and
 * when [refresh] is called after the contacts permission is granted.
 */
@Singleton
class ContactsSource
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        settings: SettingsStore,
        @ApplicationScope scope: CoroutineScope,
    ) {
        private val granted = MutableStateFlow(hasPermission())

        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        val index: StateFlow<ContactIndex> =
            combine(granted, settings.settings.map { it.callingCode }.distinctUntilChanged()) { allowed, code ->
                allowed to code
            }.flatMapLatest { (allowed, callingCode) ->
                // Observing the contacts needs the permission as much as
                // reading them does: registering without it throws.
                if (!allowed) {
                    flowOf(ContactIndex.EMPTY)
                } else {
                    contactChanges().mapLatest { ContactIndex(load(), callingCode) }
                }
            }.stateIn(scope, SharingStarted.Eagerly, ContactIndex.EMPTY)

        /** Call after the contacts permission may have changed. */
        fun refresh() {
            granted.value = hasPermission()
        }

        private fun hasPermission(): Boolean =
            context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

        private fun contactChanges(): Flow<Unit> =
            callbackFlow {
                val observer =
                    object : ContentObserver(Handler(Looper.getMainLooper())) {
                        override fun onChange(selfChange: Boolean) {
                            trySend(Unit)
                        }
                    }
                context.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
                trySend(Unit)
                awaitClose { context.contentResolver.unregisterContentObserver(observer) }
            }.conflate()

        private suspend fun load(): List<ContactIndex.Contact> =
            withContext(Dispatchers.IO) {
                val projection =
                    arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME_PRIMARY, Phone.NUMBER, Phone.PHOTO_THUMBNAIL_URI)
                runCatching {
                    context.contentResolver.query(Phone.CONTENT_URI, projection, null, null, null)?.use { cursor ->
                        val id = cursor.getColumnIndexOrThrow(Phone.CONTACT_ID)
                        val name = cursor.getColumnIndexOrThrow(Phone.DISPLAY_NAME_PRIMARY)
                        val number = cursor.getColumnIndexOrThrow(Phone.NUMBER)
                        val photo = cursor.getColumnIndexOrThrow(Phone.PHOTO_THUMBNAIL_URI)
                        buildList {
                            while (cursor.moveToNext()) {
                                add(
                                    ContactIndex.Contact(
                                        id = cursor.getLong(id),
                                        name = cursor.getString(name).orEmpty(),
                                        number = cursor.getString(number).orEmpty(),
                                        photoUri = cursor.getString(photo),
                                    ),
                                )
                            }
                        }
                    }
                }.getOrNull().orEmpty()
            }
    }
