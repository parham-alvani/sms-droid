package me.parham1995.sms.messaging

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.TelephonyManager
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.parham1995.sms.data.ApplicationScope
import javax.inject.Inject

/**
 * "Reply with a message" on an incoming call: the dialer hands over a number
 * and a canned text, and this sends it with no screen. Required for the SMS
 * role.
 */
@AndroidEntryPoint
class HeadlessSmsSendService : Service() {
    @Inject lateinit var sender: SmsSender

    @Inject lateinit var sims: Sims

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val address = intent?.data?.let { Intents.recipients(it).firstOrNull() }
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (intent?.action != TelephonyManager.ACTION_RESPOND_VIA_MESSAGE || address == null || text.isNullOrBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        scope.launch {
            try {
                sender.send(address, text, sims.defaultForSms())
            } catch (e: Exception) {
                Log.e("HeadlessSmsSend", "could not send the quick reply", e)
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }
}
