package me.parham1995.sms

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import me.parham1995.sms.messaging.Intents
import me.parham1995.sms.ui.theme.SmsTheme

/**
 * The one activity. Besides the launcher it is what other apps open: an
 * `sms:` link, text shared to it, a tap on a notification. Each becomes a
 * [Launch] that the navigation graph acts on once.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val launches = MutableStateFlow<Launch?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) launches.value = launchOf(intent)
        setContent {
            SmsTheme {
                SmsNavHost(launches = launches, onLaunched = { launches.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launches.value = launchOf(intent)
    }

    private fun launchOf(intent: Intent): Launch? =
        when (intent.action) {
            ACTION_OPEN_THREAD -> {
                val threadId = intent.getLongExtra(EXTRA_THREAD, -1)
                if (threadId < 0) null else Launch.OpenThread(threadId, intent.getStringExtra(EXTRA_ADDRESS))
            }
            Intent.ACTION_SENDTO, Intent.ACTION_VIEW -> {
                val uri = intent.data
                val recipients = uri?.let(Intents::recipients).orEmpty()
                val body =
                    intent.getStringExtra("sms_body")
                        ?: intent.getStringExtra(Intent.EXTRA_TEXT)
                        ?: uri?.let(Intents::body)
                if (recipients.isEmpty()) {
                    Launch.Compose(
                        body.orEmpty(),
                    )
                } else {
                    Launch.Write(recipients.first(), body.orEmpty())
                }
            }
            Intent.ACTION_SEND -> Launch.Compose(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
            else -> null
        }

    companion object {
        private const val ACTION_OPEN_THREAD = "me.parham1995.sms.OPEN_THREAD"
        private const val EXTRA_THREAD = "thread"
        private const val EXTRA_ADDRESS = "address"

        fun openThread(
            context: Context,
            threadId: Long,
            address: String?,
        ): Intent =
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_OPEN_THREAD)
                .putExtra(EXTRA_THREAD, threadId)
                .putExtra(EXTRA_ADDRESS, address)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}

/** Where the app was asked to go, from outside. */
sealed interface Launch {
    data class OpenThread(
        val threadId: Long,
        val address: String?,
    ) : Launch

    /** A message to [address], with [body] typed in but not sent. */
    data class Write(
        val address: String,
        val body: String,
    ) : Launch

    /** A new message to someone not chosen yet. */
    data class Compose(
        val body: String,
    ) : Launch
}
