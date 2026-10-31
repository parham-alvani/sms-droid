package me.parham1995.sms.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BlockingTest {
    @Test
    fun `an entry is spelled the canonical way`() {
        assertThat(Blocking.entry("0942 200 0176", "98")).isEqualTo("+989422000176")
        assertThat(Blocking.entry("+989422000176", "98")).isEqualTo("+989422000176")
        assertThat(Blocking.entry("Resalat Bank", "98")).isEqualTo("resalatbank")
        assertThat(Blocking.entry("0942*", "98")).isEqualTo("+98942*")
        assertThat(Blocking.entry("+98 942 *", "98")).isEqualTo("+98942*")
        assertThat(Blocking.entry("0098942*", "98")).isEqualTo("+98942*")
        assertThat(Blocking.entry("3000*", "98")).isEqualTo("3000*")
    }

    @Test
    fun `a sender name has no range`() {
        assertThat(Blocking.isRange("HAMRAH_AVAL*")).isFalse()
        assertThat(Blocking.entry("HAMRAH_AVAL*", "98")).isEqualTo("hamrahaval")
        assertThat(Blocking.isRange("*")).isFalse()
    }

    @Test
    fun `a number is blocked in any spelling`() {
        val rule = BlockRule(setOf("+989422000176"))
        assertThat(rule.isBlocked("09422000176")).isTrue()
        assertThat(rule.isBlocked("+98 942 200 0176")).isTrue()
        assertThat(rule.isBlocked("09422000177")).isFalse()
    }

    @Test
    fun `a sender name is blocked in any spelling`() {
        val rule = BlockRule(setOf(Blocking.entry("MCI Academy", "98")))
        assertThat(rule.isBlocked("MCI_ACADEMY")).isTrue()
        assertThat(rule.isBlocked("mci-academy")).isTrue()
        assertThat(rule.isBlocked("MCI Club")).isFalse()
    }

    @Test
    fun `a range blocks every number that starts with it`() {
        val rule = BlockRule(setOf("+98942*"))
        assertThat(rule.isBlocked("09422000176")).isTrue()
        assertThat(rule.isBlocked("+989422010170")).isTrue()
        assertThat(rule.isBlocked("09121234567")).isFalse()
        assertThat(rule.isBlocked("ResalatBank")).isFalse()
    }

    @Test
    fun `a short code range matches short codes`() {
        val rule = BlockRule(setOf("3000*"))
        assertThat(rule.isBlocked("300012345")).isTrue()
        assertThat(rule.isBlocked("+13000123456")).isFalse()
    }

    @Test
    fun `nothing is blocked by an empty list or an empty address`() {
        assertThat(BlockRule().isBlocked("09422000176")).isFalse()
        assertThat(BlockRule(setOf("+98942*")).isBlocked("")).isFalse()
    }
}
