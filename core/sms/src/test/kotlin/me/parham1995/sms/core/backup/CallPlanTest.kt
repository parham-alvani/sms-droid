package me.parham1995.sms.core.backup

import com.google.common.truth.Truth.assertThat
import me.parham1995.sms.core.backup.CallRecord.Type
import org.junit.Test
import java.time.Instant

class CallPlanTest {
    private fun call(
        number: String,
        type: Type = Type.IN,
        at: String = "2026-10-31T13:40:05Z",
        seconds: Long = 0,
    ) = CallRecord(Instant.parse(at), type, number, seconds)

    @Test
    fun `a line reads back, and a call with no talk has no seconds`() {
        val calls =
            listOf(call("+989121234567", seconds = 312), call("09422000176", Type.MISSED, "2026-10-31T13:02:11Z"))
        val text = CallPlan.format(calls)
        assertThat(
            text.lines().first(),
        ).isEqualTo("""{"at":"2026-10-31T13:02:11Z","type":"missed","number":"09422000176"}""")
        assertThat(CallPlan.parse("calls/x.jsonl", text)).containsExactlyElementsIn(calls)
    }

    @Test
    fun `calls are filed by number, beside the messages`() {
        val writes =
            CallPlan.backup(
                listOf(call("0912 123 4567"), call("+989121234567", Type.OUT, "2026-10-31T14:00:00Z"), call("")),
                "98",
            ) {
                null
            }
        assertThat(writes.map { it.path }).containsExactly("calls/+989121234567.jsonl", "calls/unknown.jsonl")
        assertThat(writes.first { it.path == "calls/+989121234567.jsonl" }.added).isEqualTo(2)
    }

    @Test
    fun `only new calls are added, and nothing is dropped`() {
        val old = call("+989121234567", at = "2026-10-01T10:00:00Z")
        val files = mapOf("calls/+989121234567.jsonl" to CallPlan.format(listOf(old)))
        val fresh = call("+989121234567", Type.OUT)
        val writes = CallPlan.backup(listOf(fresh), "98", files::get)
        assertThat(CallPlan.parse("p", writes.single().text!!)).containsExactly(old, fresh).inOrder()
        assertThat(writes.single().added).isEqualTo(1)
    }

    @Test
    fun `a backup with nothing new writes nothing`() {
        val known = call("+989121234567")
        val files = mapOf("calls/+989121234567.jsonl" to CallPlan.format(listOf(known)))
        assertThat(CallPlan.backup(listOf(known), "98", files::get)).isEmpty()
    }

    @Test
    fun `a line that is not a call stops the backup`() {
        val files = mapOf("calls/+989121234567.jsonl" to "not json\n")
        val thrown = runCatching { CallPlan.backup(listOf(call("+989121234567")), "98", files::get) }.exceptionOrNull()
        assertThat(thrown).isInstanceOf(BackupFile.Corrupt::class.java)
    }

    @Test
    fun `the commit message counts calls`() {
        assertThat(BackupPlan.commitMessage(0, 0, 3)).isEqualTo("backup: 3 calls")
        assertThat(BackupPlan.commitMessage(2, 1, 1)).isEqualTo("backup: 2 messages in 1 conversation, 1 call")
    }
}
