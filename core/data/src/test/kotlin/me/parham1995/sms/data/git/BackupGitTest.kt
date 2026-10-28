package me.parham1995.sms.data.git

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Against a bare repository on disk: the transport differs from GitHub's, the git work does not. */
class BackupGitTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val author = PersonIdent("test", "test@localhost")

    private val remote: File by lazy {
        temp.newFolder("remote.git").also {
            Git
                .init()
                .setBare(true)
                .setDirectory(it)
                .call()
                .close()
        }
    }

    private fun phone(name: String) =
        BackupGit(
            workTree = File(temp.root, name),
            remoteUrl = remote.toURI().toString(),
            branch = "main",
            keys = null,
            configDir = File(temp.root, "git-config"),
        )

    @Test
    fun `the first backup creates the branch in an empty repository`() =
        runTest {
            val git = phone("a")
            assertThat(git.refresh()).isFalse()

            git.write("messages/x.jsonl", "line\n")
            assertThat(git.commit("backup: 1 message in 1 conversation", author)).isTrue()
            assertThat(git.push()).isTrue()

            val other = phone("b")
            assertThat(other.refresh()).isTrue()
            assertThat(other.read("messages/x.jsonl")).isEqualTo("line\n")
            assertThat(other.list("messages")).containsExactly("messages/x.jsonl")
        }

    @Test
    fun `a removed file is committed and pushed as removed`() =
        runTest {
            val a = phone("a")
            a.refresh()
            a.write("messages/x.jsonl", "line\n")
            a.commit("first", author)
            a.delete("messages/x.jsonl")
            a.write("ads/x.jsonl", "line\n")
            assertThat(a.commit("moved", author)).isTrue()
            assertThat(a.push()).isTrue()

            val b = phone("b")
            assertThat(b.refresh()).isTrue()
            assertThat(b.list("messages")).isEmpty()
            assertThat(b.list("ads")).containsExactly("ads/x.jsonl")
        }

    @Test
    fun `nothing to commit is not a commit`() =
        runTest {
            val git = phone("a")
            git.refresh()
            git.write("messages/x.jsonl", "line\n")
            git.commit("first", author)

            git.write("messages/x.jsonl", "line\n")
            assertThat(git.commit("again", author)).isFalse()
        }

    @Test
    fun `a push behind the remote is rejected, and a refresh catches up`() =
        runTest {
            val a = phone("a")
            a.refresh()
            a.write("messages/x.jsonl", "a\n")
            a.commit("a", author)
            a.push()

            val b = phone("b")
            b.refresh()
            b.write("messages/y.jsonl", "b\n")
            b.commit("b", author)
            assertThat(b.push()).isTrue()

            a.write("messages/x.jsonl", "a\na2\n")
            a.commit("a2", author)
            assertThat(a.push()).isFalse()

            a.refresh()
            assertThat(a.read("messages/y.jsonl")).isEqualTo("b\n")
            assertThat(a.read("messages/x.jsonl")).isEqualTo("a\n")
        }

    @Test
    fun `a refresh throws away what a failed run left behind`() =
        runTest {
            val git = phone("a")
            git.refresh()
            git.write("messages/x.jsonl", "kept\n")
            git.commit("first", author)
            git.push()

            git.write("messages/x.jsonl", "half written\n")
            git.write("messages/stray.jsonl.tmp", "junk")
            git.commit("never pushed", author)

            assertThat(git.refresh()).isTrue()
            assertThat(git.read("messages/x.jsonl")).isEqualTo("kept\n")
            assertThat(git.list("messages")).containsExactly("messages/x.jsonl")
        }
}
