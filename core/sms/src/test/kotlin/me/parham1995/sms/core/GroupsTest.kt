package me.parham1995.sms.core

import com.google.common.truth.Truth.assertThat
import me.parham1995.sms.core.ContactIndex.Contact
import org.junit.Test

class GroupsTest {
    @Test
    fun `counts lists each group once per conversation, sorted by name`() {
        val counts =
            Groups.counts(
                listOf(
                    listOf("Home Services", "family"),
                    listOf("Home Services", "Home Services"),
                    emptyList(),
                    listOf("Banks"),
                ),
            )

        assertThat(counts)
            .containsExactly(
                Groups.Count("Banks", 1),
                Groups.Count("family", 1),
                Groups.Count("Home Services", 2),
            ).inOrder()
    }

    @Test
    fun `no filter passes every conversation, a filter only its members`() {
        assertThat(Groups.matches(emptyList(), null)).isTrue()
        assertThat(Groups.matches(listOf("Family"), "Family")).isTrue()
        assertThat(Groups.matches(listOf("Family"), "Banks")).isFalse()
        assertThat(Groups.matches(emptyList(), "Banks")).isFalse()
    }

    @Test
    fun `a filter whose group emptied is dropped`() {
        val counts = listOf(Groups.Count("Family", 3))
        assertThat(Groups.keep("Family", counts)).isEqualTo("Family")
        assertThat(Groups.keep("Banks", counts)).isNull()
        assertThat(Groups.keep(null, counts)).isNull()
    }

    @Test
    fun `an address carries the groups of its contact`() {
        val index =
            ContactIndex(
                listOf(
                    Contact(1, "Hamid Khosrojerdi", "+989126230198", groups = listOf("Home Services")),
                    Contact(2, "Resalat Bank", "ResalatBank", groups = listOf("Banks")),
                ),
                callingCode = "98",
            )

        assertThat(index.groupsOf("09126230198")).containsExactly("Home Services")
        assertThat(index.groupsOf("RESALAT_BANK")).containsExactly("Banks")
        assertThat(index.groupsOf("+989120000000")).isEmpty()
    }
}
