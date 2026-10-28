package me.parham1995.sms.core.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AdSendersTest {
    @Test
    fun `the file is one sorted sender per line, and reads back`() {
        val senders = setOf("mciacademy", "+989001234567")
        val text = AdSenders.format(senders)
        assertThat(text).isEqualTo("+989001234567\nmciacademy\n")
        assertThat(AdSenders.parse(text)).isEqualTo(senders)
    }

    @Test
    fun `blank lines and comments are skipped`() {
        assertThat(AdSenders.parse("# senders\n\n  shop  \n")).containsExactly("shop")
    }

    @Test
    fun `the first sync takes both sides`() {
        assertThat(AdSenders.merge(emptySet(), setOf("a"), setOf("b"))).containsExactly("a", "b")
    }

    @Test
    fun `an addition on either side is kept`() {
        val base = setOf("a")
        assertThat(AdSenders.merge(base, setOf("a", "p"), setOf("a", "g"))).containsExactly("a", "p", "g")
    }

    @Test
    fun `a removal on either side stays removed`() {
        val base = setOf("a", "b")
        assertThat(AdSenders.merge(base, setOf("b"), setOf("a", "b"))).containsExactly("b")
        assertThat(AdSenders.merge(base, setOf("a", "b"), setOf("a"))).containsExactly("a")
    }
}
