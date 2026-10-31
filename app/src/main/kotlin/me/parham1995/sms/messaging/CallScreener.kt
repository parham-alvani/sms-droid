package me.parham1995.sms.messaging

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.parham1995.sms.data.ApplicationScope
import me.parham1995.sms.data.SettingsStore
import me.parham1995.sms.data.runCatchingUnlessCancelled
import javax.inject.Inject

/**
 * Rejects calls from blocked numbers, by the same `BlockRule` that quiets
 * their messages, so one list in git blocks both.
 *
 * This app is not the dialer and does not need to be: Android asks the app
 * holding the call screening role about every incoming call from a number
 * that is not a contact. A rejected call never rings and shows no
 * notification, but it stays in the call log, so a number blocked by mistake
 * is found there. Any doubt -- a hidden number, settings that do not load --
 * lets the call through.
 */
@AndroidEntryPoint
class CallScreener : CallScreeningService() {
    @Inject lateinit var settings: SettingsStore

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onScreenCall(details: Call.Details) {
        val number = details.handle?.schemeSpecificPart.orEmpty()
        if (details.callDirection != Call.Details.DIRECTION_INCOMING || number.isBlank()) {
            respondToCall(details, CallResponse.Builder().build())
            return
        }
        scope.launch {
            val blocked =
                runCatchingUnlessCancelled { settings.current().blockRule.isBlocked(number) }
                    .onFailure { Log.w(TAG, "could not read the blocked list; letting the call through", it) }
                    .getOrDefault(false)
            val response =
                if (blocked) {
                    CallResponse
                        .Builder()
                        .setDisallowCall(true)
                        .setRejectCall(true)
                        .setSkipNotification(true)
                        .setSkipCallLog(false)
                        .build()
                } else {
                    CallResponse.Builder().build()
                }
            respondToCall(details, response)
        }
    }

    companion object {
        private const val TAG = "CallScreener"

        fun isScreening(context: Context): Boolean =
            context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_CALL_SCREENING)

        /** The system dialog that lets this app screen calls. */
        fun request(context: Context): Intent =
            context.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
    }
}
