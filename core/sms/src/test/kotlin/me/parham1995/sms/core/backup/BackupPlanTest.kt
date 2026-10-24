package me.parham1995.sms.core.backup

import com.google.common.truth.Truth.assertThat
import me.parham1995.sms.core.backup.BackupMessage.Direction
import org.junit.Test
import java.time.Instant

class BackupPlanTest {
    private fun message(
        address: String,
        body: String,
        at: String = "2026-10-24T07:00:00Z",
    ) = BackupMessage(Instant.parse(at), Direction.IN, address, body)

    @Test
    fun `messages are filed by address`() {
        assertThat(BackupLayout.pathFor("0912 123 4567", "98")).isEqualTo("messages/+989121234567.jsonl")
        assertThat(BackupLayout.pathFor("+989121234567", "98")).isEqualTo("messages/+989121234567.jsonl")
        assertThat(BackupLayout.pathFor("ResalatBank", "98")).isEqualTo("messages/resalatbank.jsonl")
        assertThat(BackupLayout.pathFor("MCI Club", "98")).isEqualTo("messages/mci_club.jsonl")
        assertThat(BackupLayout.pathFor("CRA.ir", "98")).isEqualTo("messages/cra.ir.jsonl")
        assertThat(BackupLayout.pathFor("../../etc", "98")).isEqualTo("messages/etc.jsonl")
        assertThat(BackupLayout.pathFor("  ", "98")).isEqualTo("messages/unknown.jsonl")
    }

    @Test
    fun `a sender name differing only in case shares a file`() {
        assertThat(BackupLayout.pathFor("Hamrahaval", "98")).isEqualTo(BackupLayout.pathFor("HAMRAHAVAL", "98"))
    }

    @Test
    fun `backup writes only the files that change`() {
        val unchanged = listOf(message("ResalatBank", "old"))
        val files = mapOf("messages/resalatbank.jsonl" to BackupFile.format(unchanged))
        val writes =
            BackupPlan.backup(
                unchanged + message("+989121234567", "new") + message("09121234567", "national"),
                "98",
                files::get,
            )
        assertThat(writes.map { it.path }).containsExactly("messages/+989121234567.jsonl")
        assertThat(writes.single().added).isEqualTo(2)
    }

    @Test
    fun `restore returns what the phone is missing, once`() {
        val a = message("x", "a", "2026-10-24T07:00:00Z")
        val b = message("x", "b", "2026-10-24T08:00:00Z")
        val missing = BackupPlan.restore(listOf(b, a, b), phone = listOf(a.key))
        assertThat(missing).containsExactly(b)
    }

    @Test
    fun `commit messages count what was added`() {
        assertThat(BackupPlan.commitMessage(1, 1)).isEqualTo("backup: 1 message in 1 conversation")
        assertThat(BackupPlan.commitMessage(12, 3)).isEqualTo("backup: 12 messages in 3 conversations")
    }
}
