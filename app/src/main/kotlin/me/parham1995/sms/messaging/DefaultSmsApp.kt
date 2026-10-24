package me.parham1995.sms.messaging

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent

/**
 * Whether this is the phone's SMS app. Until it is, Android does not let it
 * read the messages, store what arrives, or send as the phone's SMS app; the
 * conversation list says so and offers the switch.
 */
object DefaultSmsApp {
    fun isDefault(context: Context): Boolean =
        context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_SMS)

    /** The system dialog that asks to make this the SMS app. */
    fun request(context: Context): Intent =
        context.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_SMS)
}
