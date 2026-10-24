package me.parham1995.sms.data

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import me.parham1995.sms.data.git.SshKeyStore
import me.parham1995.sms.data.security.KeystoreBox
import java.io.File
import javax.inject.Qualifier
import javax.inject.Singleton

/** Lives as long as the process; for state that outlives any one screen, like the contact index. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The deploy key, sealed by the at-rest Keystore key. */
    @Provides
    @Singleton
    fun sshKeys(
        @ApplicationContext context: Context,
    ): SshKeyStore = SshKeyStore(File(context.filesDir, "ssh"), KeystoreBox.atRest(), File(context.filesDir, "git"))
}
