package me.parham1995.sms.data.git

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.api.TransportConfigCallback
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.PersonIdent
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.SshSessionFactory
import org.eclipse.jgit.transport.SshTransport
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.sshd.ServerKeyDatabase
import org.eclipse.jgit.transport.sshd.SshdSessionFactoryBuilder
import java.io.File

/**
 * The backup repository: one branch, written only by this phone.
 *
 * Every backup starts from the remote. [refresh] fetches and makes the work
 * tree exactly the remote branch, throwing away whatever a failed run left
 * behind; the messages are merged in again from the phone, so nothing that
 * was not pushed is lost. That keeps this class free of the rebase and
 * conflict handling passwords-droid needs: there is no local state worth
 * keeping between runs.
 *
 * A new, empty repository is the normal start, and has no branch to fetch.
 * The first commit then creates it, and [push] sends `HEAD` to the branch by
 * name, so the local branch's own name never matters.
 */
class BackupGit(
    private val workTree: File,
    private val remoteUrl: String,
    private val branch: String,
    private val keys: SshKeyStore?,
    configDir: File,
    private val hostKeys: ServerKeyDatabase = PinnedHostKeys(),
) {
    init {
        AndroidGitEnvironment.install(configDir)
    }

    private val sessions: SshSessionFactory? by lazy {
        keys?.let { store ->
            SshdSessionFactoryBuilder()
                .setHomeDirectory(store.directory.parentFile)
                .setSshDirectory(store.directory)
                .setPreferredAuthentications("publickey")
                // The private key is sealed on disk; sshd gets it from memory
                // and never looks for an identity file.
                .setDefaultIdentities { emptyList() }
                .setDefaultKeysProvider { listOfNotNull(store.keyPair()) }
                .setConfigFile { store.configFile() }
                // GitHub's published host keys and nothing else: there is no
                // one on a phone to ask whether a new key is fine.
                .setServerKeyDatabase { _, _ -> hostKeys }
                .build(null)
        }
    }

    private val transport =
        TransportConfigCallback { transport ->
            val factory = sessions
            if (transport is SshTransport && factory != null) transport.sshSessionFactory = factory
        }

    private val remoteBranch: String get() = "refs/remotes/${Constants.DEFAULT_REMOTE_NAME}/$branch"

    /**
     * Makes the work tree the remote branch's tip, or leaves it as it is when
     * the remote has no such branch yet. Returns whether the remote had one.
     */
    suspend fun refresh(): Boolean =
        withContext(Dispatchers.IO) {
            open().use { git ->
                git
                    .remoteSetUrl()
                    .setRemoteName(Constants.DEFAULT_REMOTE_NAME)
                    .setRemoteUri(URIish(remoteUrl))
                    .call()
                // Every branch, by wildcard: naming one the remote does not
                // have yet fails the whole fetch, and an empty repository has
                // none.
                git
                    .fetch()
                    .setRemote(Constants.DEFAULT_REMOTE_NAME)
                    .setRefSpecs(RefSpec("+refs/heads/*:refs/remotes/${Constants.DEFAULT_REMOTE_NAME}/*"))
                    .setRemoveDeletedRefs(true)
                    .setTransportConfigCallback(transport)
                    .setTimeout(TIMEOUT_SECONDS)
                    .call()
                if (git.repository.resolve(remoteBranch) == null) return@use false
                git
                    .reset()
                    .setMode(ResetCommand.ResetType.HARD)
                    .setRef(remoteBranch)
                    .call()
                git
                    .clean()
                    .setCleanDirectories(true)
                    .setForce(true)
                    .call()
                true
            }
        }

    /** Reads a file as the work tree has it, or null when there is none. */
    fun read(path: String): String? = File(workTree, path).takeIf { it.isFile }?.readText()

    /** Every file under [directory], as paths relative to the work tree. */
    fun list(directory: String): List<String> =
        File(workTree, directory)
            .walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(workTree).invariantSeparatorsPath }
            .sorted()
            .toList()

    fun write(
        path: String,
        text: String,
    ) {
        val file = File(workTree, path)
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        check(tmp.renameTo(file)) { "could not write $path" }
    }

    /** Removes a file from the work tree; the next [commit] stages the removal. */
    fun delete(path: String) {
        val file = File(workTree, path)
        check(!file.exists() || file.delete()) { "could not remove $path" }
    }

    /** Stages everything, removals included, and commits it. Returns false when there was nothing to commit. */
    suspend fun commit(
        message: String,
        author: PersonIdent,
    ): Boolean =
        withContext(Dispatchers.IO) {
            open().use { git ->
                git.add().addFilepattern(".").call()
                // A plain add stages new and changed files only; update stages removals.
                git
                    .add()
                    .addFilepattern(".")
                    .setUpdate(true)
                    .call()
                if (git.status().call().isClean) return@use false
                git
                    .commit()
                    .setMessage(message)
                    .setAuthor(author)
                    .setCommitter(author)
                    .setSign(false)
                    .call()
                true
            }
        }

    /**
     * Pushes `HEAD` to the branch. A rejection is returned, not thrown: the
     * caller starts again from [refresh], which is the whole conflict story.
     */
    suspend fun push(): Boolean =
        withContext(Dispatchers.IO) {
            open().use { git ->
                if (git.repository.resolve(Constants.HEAD) == null) return@use true
                val rejection =
                    git
                        .push()
                        .setRemote(Constants.DEFAULT_REMOTE_NAME)
                        .setRefSpecs(RefSpec("${Constants.HEAD}:refs/heads/$branch"))
                        .setTransportConfigCallback(transport)
                        .setTimeout(TIMEOUT_SECONDS)
                        .call()
                        .flatMap { it.remoteUpdates }
                        .firstOrNull {
                            it.status != RemoteRefUpdate.Status.OK && it.status != RemoteRefUpdate.Status.UP_TO_DATE
                        }
                when (rejection?.status) {
                    null -> true
                    RemoteRefUpdate.Status.REJECTED_NONFASTFORWARD,
                    RemoteRefUpdate.Status.REJECTED_REMOTE_CHANGED,
                    -> false
                    else -> throw PushRefused("push refused: ${rejection.status} ${rejection.message.orEmpty()}".trim())
                }
            }
        }

    private fun open(): Git {
        if (!File(workTree, Constants.DOT_GIT).isDirectory) {
            workTree.mkdirs()
            Git
                .init()
                .setDirectory(workTree)
                .setInitialBranch(branch)
                .call()
                .use { git ->
                    git
                        .remoteAdd()
                        .setName(Constants.DEFAULT_REMOTE_NAME)
                        .setUri(URIish(remoteUrl))
                        .call()
                }
        }
        return Git.open(workTree)
    }

    private companion object {
        const val TIMEOUT_SECONDS = 120
    }
}

/** The remote refused the push for a reason that trying again will not fix, such as a read-only deploy key. */
class PushRefused(
    message: String,
) : Exception(message)
