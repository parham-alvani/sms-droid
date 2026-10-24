package me.parham1995.sms

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import me.parham1995.sms.feature.compose.NewMessageScreen
import me.parham1995.sms.feature.conversations.ConversationsScreen
import me.parham1995.sms.feature.settings.SettingsScreen
import me.parham1995.sms.feature.thread.ThreadScreen

@Serializable
object Conversations

/**
 * A conversation, by [threadId] when it is known; otherwise by [address], and
 * the screen finds or creates the thread. [draft] is typed in, not sent.
 */
@Serializable
data class Thread(
    val threadId: Long = -1,
    val address: String? = null,
    val draft: String = "",
)

@Serializable
data class NewMessage(
    val draft: String = "",
)

@Serializable
object Preferences

@Composable
fun SmsNavHost(
    launches: StateFlow<Launch?>,
    onLaunched: () -> Unit,
) {
    val nav = rememberNavController()
    val launch by launches.collectAsStateWithLifecycle()
    LaunchedEffect(launch) {
        launch?.let { nav.follow(it) }
        if (launch != null) onLaunched()
    }
    NavHost(navController = nav, startDestination = Conversations) {
        composable<Conversations> {
            ConversationsScreen(
                onOpen = { threadId, address -> nav.navigate(Thread(threadId, address)) },
                onNew = { nav.navigate(NewMessage()) },
                onSettings = { nav.navigate(Preferences) },
            )
        }
        composable<Thread> {
            ThreadScreen(onBack = { if (!nav.popBackStack()) nav.navigate(Conversations) })
        }
        composable<NewMessage> {
            NewMessageScreen(
                onBack = { nav.popBackStack() },
                onChosen = { address, draft ->
                    nav.navigate(Thread(address = address, draft = draft)) { popUpTo<NewMessage> { inclusive = true } }
                },
            )
        }
        composable<Preferences> {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}

/** Opens what another app asked for, on top of the conversation list so Back returns to it. */
private fun NavHostController.follow(launch: Launch) {
    val destination: Any =
        when (launch) {
            is Launch.OpenThread -> Thread(threadId = launch.threadId, address = launch.address)
            is Launch.Write -> Thread(address = launch.address, draft = launch.body)
            is Launch.Compose -> NewMessage(draft = launch.body)
        }
    navigate(destination) { popUpTo<Conversations>() }
}
