package me.parham1995.sms.core.backup

import com.google.common.truth.Truth.assertThat
import me.parham1995.sms.core.AdRule
import me.parham1995.sms.core.Ads
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
                AdRule(),
                files.keys,
                files::get,
            )
        assertThat(writes.map { it.path }).containsExactly("messages/+989121234567.jsonl")
        assertThat(writes.single().added).isEqualTo(2)
    }

    @Test
    fun `ads are filed under ads, the rest under messages`() {
        val ad = message("ExampleShop", "حراج پاییزه لغو11", "2026-10-24T07:00:00Z")
        val code = message("ExampleShop", "کد ورود: 482913 لغو11", "2026-10-24T08:00:00Z")
        val writes = BackupPlan.backup(listOf(ad, code), "98", AdRule(), emptyList()) { null }
        assertThat(writes.associate { it.path to it.text }).containsExactly(
            "messages/exampleshop.jsonl",
            BackupFile.format(listOf(code)),
            "ads/exampleshop.jsonl",
            BackupFile.format(listOf(ad)),
        )
        assertThat(writes.sumOf { it.added }).isEqualTo(2)
    }

    @Test
    fun `ads already in messages move to ads, even when the phone deleted them`() {
        val ad = message("ExampleShop", "حراج پاییزه لغو11", "2026-10-24T07:00:00Z")
        val kept = message("ExampleShop", "سفارش شما ارسال شد", "2026-10-24T08:00:00Z")
        val files = mapOf("messages/exampleshop.jsonl" to BackupFile.format(listOf(ad, kept)))
        val writes = BackupPlan.backup(emptyList(), "98", AdRule(), files.keys, files::get)
        assertThat(writes.associate { it.path to it.text }).containsExactly(
            "messages/exampleshop.jsonl",
            BackupFile.format(listOf(kept)),
            "ads/exampleshop.jsonl",
            BackupFile.format(listOf(ad)),
        )
        assertThat(writes.sumOf { it.added }).isEqualTo(0)
    }

    @Test
    fun `a sender that stops being ads moves back, and the empty ads file goes`() {
        val line = message("ExampleShop", "تخفیف ویژه پاییز")
        val files = mapOf("ads/exampleshop.jsonl" to BackupFile.format(listOf(line)))
        val writes = BackupPlan.backup(emptyList(), "98", AdRule(), files.keys, files::get)
        assertThat(writes.associate { it.path to it.text }).containsExactly(
            "messages/exampleshop.jsonl",
            BackupFile.format(listOf(line)),
            "ads/exampleshop.jsonl",
            null,
        )
    }

    @Test
    fun `an ad sender's lines are filed as ads`() {
        val line = message("Example Shop", "تخفیف ویژه پاییز")
        val rule = AdRule(setOf(Ads.senderKey("Example Shop", "98")))
        val writes = BackupPlan.backup(listOf(line), "98", rule, emptyList()) { null }
        assertThat(writes.map { it.path }).containsExactly("ads/example_shop.jsonl")
    }

    @Test
    fun `filing that is already right writes nothing`() {
        val ad = message("ExampleShop", "حراج پاییزه لغو11")
        val files = mapOf("ads/exampleshop.jsonl" to BackupFile.format(listOf(ad)))
        assertThat(BackupPlan.backup(listOf(ad), "98", AdRule(), files.keys, files::get)).isEmpty()
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
