package me.parham1995.sms.messaging

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** One active SIM, as the composer offers it. */
data class Sim(
    val subscriptionId: Int,
    val label: String,
    val slot: Int,
)

/**
 * The phone's active SIMs, for choosing which one sends.
 *
 * Listing them needs `READ_PHONE_STATE`; without it the list is empty and a
 * message goes out on the SIM Android's settings pick for SMS.
 */
@Singleton
class Sims
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun active(): List<Sim> {
            if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return emptyList()
            }
            val manager = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
            return manager.activeSubscriptionInfoList.orEmpty().map { info ->
                Sim(
                    subscriptionId = info.subscriptionId,
                    label = info.displayName?.toString()?.takeIf { it.isNotBlank() } ?: "SIM ${info.simSlotIndex + 1}",
                    slot = info.simSlotIndex,
                )
            }
        }

        /** The SIM Android's settings send SMS from, or [SubscriptionManager.INVALID_SUBSCRIPTION_ID]. */
        fun defaultForSms(): Int = SubscriptionManager.getDefaultSmsSubscriptionId()
    }
