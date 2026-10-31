package me.parham1995.sms.core.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BlockedSendersTest {
    @Test
    fun `a hand-written file reads back in the canonical spelling`() {
        val text = "# spam\n0942 200 0176\n0942*\nResalat Bank\n\n"
        assertThat(BlockedSenders.parseEntries(text, "98"))
            .containsExactly("+989422000176", "+98942*", "resalatbank")
    }

    @Test
    fun `the file is one sorted entry per line`() {
        assertThat(BlockedSenders.format(setOf("resalatbank", "+98942*"))).isEqualTo("+98942*\nresalatbank\n")
    }

    @Test
    fun `an unblock on either side stays`() {
        val base = setOf("+98942*", "shop")
        assertThat(BlockedSenders.merge(base, setOf("shop"), base)).containsExactly("shop")
    }
}
