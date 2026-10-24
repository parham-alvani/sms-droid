package me.parham1995.sms.core.backup

import com.google.common.truth.Truth.assertThat
import me.parham1995.sms.core.backup.BackupMessage.Direction
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

class BackupFileTest {
    private fun message(
        at: String,
        body: String,
        direction: Direction = Direction.IN,
        address: String = "+989121234567",
        sentAt: String? = null,
    ) = BackupMessage(Instant.parse(at), direction, address, body, sentAt?.let(Instant::parse))

    @Test
    fun `a line is one JSON object with UTC times`() {
        val text =
            BackupFile.format(
                listOf(message("2026-10-24T06:45:03.120Z", "سلام\nخوبی؟", sentAt = "2026-10-24T06:45:01Z")),
            )
        assertThat(text).isEqualTo(
            """{"at":"2026-10-24T06:45:03.120Z","direction":"in","address":"+989121234567",""" +
                """"body":"سلام\nخوبی؟","sentAt":"2026-10-24T06:45:01Z"}""" + "\n",
        )
    }

    @Test
    fun `a missing sender time is left out of the line`() {
        val text = BackupFile.format(listOf(message("2026-10-24T06:45:03Z", "hi", Direction.OUT)))
        assertThat(text).doesNotContain("sentAt")
    }

    @Test
    fun `format and parse round-trip`() {
        val messages =
            listOf(
                message("2026-10-24T06:45:03.120Z", "first", sentAt = "2026-10-24T06:45:01Z"),
                message("2026-10-24T07:00:00Z", "reply", Direction.OUT),
            )
        assertThat(BackupFile.parse("p", BackupFile.format(messages))).isEqualTo(messages)
    }

    @Test
    fun `lines are oldest first whatever order they came in`() {
        val text =
            BackupFile.format(
                listOf(message("2026-10-24T08:00:00Z", "later"), message("2026-10-24T07:00:00Z", "earlier")),
            )
        assertThat(BackupFile.parse("p", text).map { it.body }).containsExactly("earlier", "later").inOrder()
    }

    @Test
    fun `merge adds only what the file does not have`() {
        val existing = BackupFile.format(listOf(message("2026-10-24T07:00:00Z", "kept")))
        val merged =
            BackupFile.merge(
                "p",
                existing,
                listOf(message("2026-10-24T07:00:00Z", "kept"), message("2026-10-24T08:00:00Z", "new")),
            )
        assertThat(merged.added).isEqualTo(1)
        assertThat(BackupFile.parse("p", merged.text!!).map { it.body }).containsExactly("kept", "new").inOrder()
    }

    @Test
    fun `merge never removes a message the phone no longer has`() {
        val existing =
            BackupFile.format(listOf(message("2026-10-24T07:00:00Z", "deleted on the phone")))
        val merged = BackupFile.merge("p", existing, listOf(message("2026-10-24T08:00:00Z", "new")))
        assertThat(BackupFile.parse("p", merged.text!!).map { it.body })
            .containsExactly("deleted on the phone", "new")
            .inOrder()
    }

    @Test
    fun `merge with nothing new changes nothing`() {
        val messages = listOf(message("2026-10-24T07:00:00Z", "same"))
        val merged = BackupFile.merge("p", BackupFile.format(messages), messages)
        assertThat(merged.text).isNull()
        assertThat(merged.added).isEqualTo(0)
    }

    @Test
    fun `a sender time that appears later does not make a second copy`() {
        val existing = BackupFile.format(listOf(message("2026-10-24T07:00:00Z", "same")))
        val merged =
            BackupFile.merge(
                "p",
                existing,
                listOf(message("2026-10-24T07:00:00Z", "same", sentAt = "2026-10-24T06:59:58Z")),
            )
        assertThat(merged.text).isNull()
    }

    @Test
    fun `unknown fields from a newer version still read`() {
        val line = """{"at":"2026-10-24T07:00:00Z","direction":"in","address":"x","body":"b","sim":2}"""
        assertThat(BackupFile.parse("p", line).single().body).isEqualTo("b")
    }

    @Test
    fun `a bad line stops the merge and names the line`() {
        val text = BackupFile.format(listOf(message("2026-10-24T07:00:00Z", "ok"))) + "not json\n"
        val error =
            assertThrows(BackupFile.Corrupt::class.java) { BackupFile.merge("messages/x.jsonl", text, emptyList()) }
        assertThat(error.line).isEqualTo(2)
        assertThat(error.message).startsWith("messages/x.jsonl:2")
    }

    @Test
    fun `a bad time is a bad line too`() {
        val line = """{"at":"yesterday","direction":"in","address":"x","body":"b"}"""
        assertThrows(BackupFile.Corrupt::class.java) { BackupFile.parse("p", line) }
    }
}
