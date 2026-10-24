package me.parham1995.sms.core

import com.google.common.truth.Truth.assertThat
import me.parham1995.sms.core.ContactIndex.Contact
import org.junit.Test

class ContactIndexTest {
    private val index =
        ContactIndex(
            listOf(
                Contact(1, "Resalat Bank", "ResalatBank"),
                Contact(1, "Resalat Bank", "+9820004747"),
                Contact(2, "Hamrahaval", "HAMRAH AVAL"),
                Contact(3, "Communications Regulatory Authority", "CRA.ir"),
                Contact(4, "Saman Bank", "989-999-920000"),
                Contact(5, "Maryam", "+989125705417"),
                Contact(6, "Elaheh", "+989205705417"),
                Contact(7, "Short", "7007"),
                Contact(8, "Ali", "09121234567"),
            ),
            callingCode = "98",
        )

    @Test
    fun `a sender name finds the contact saved with that name as its number`() {
        assertThat(index.lookup("ResalatBank")?.name).isEqualTo("Resalat Bank")
        assertThat(index.lookup("CRA.ir")?.name).isEqualTo("Communications Regulatory Authority")
    }

    @Test
    fun `a sender name matches across spaces, underscores and case`() {
        assertThat(index.lookup("HAMRAH_AVAL")?.name).isEqualTo("Hamrahaval")
        assertThat(index.lookup("hamrahaval")?.name).isEqualTo("Hamrahaval")
    }

    @Test
    fun `a sender name nobody saved stays unknown`() {
        assertThat(index.lookup("MCI Club")).isNull()
        assertThat(index.displayName("MCI Club")).isEqualTo("MCI Club")
    }

    @Test
    fun `a number matches the same number saved without a plus or with separators`() {
        assertThat(index.lookup("+989999920000")?.name).isEqualTo("Saman Bank")
    }

    @Test
    fun `a number matches a contact saved in national form`() {
        assertThat(index.lookup("+989121234567")?.name).isEqualTo("Ali")
    }

    @Test
    fun `two numbers that share an ending find the right contact`() {
        assertThat(index.lookup("+989125705417")?.name).isEqualTo("Maryam")
        assertThat(index.lookup("+989205705417")?.name).isEqualTo("Elaheh")
        // Neither: the last seven digits agree, the tenth from the end does not.
        assertThat(index.lookup("+989135705417")).isNull()
    }

    @Test
    fun `a short code only matches exactly`() {
        assertThat(index.lookup("7007")?.name).isEqualTo("Short")
        assertThat(index.lookup("17007")).isNull()
    }

    @Test
    fun `search finds by name and by digits`() {
        assertThat(index.search("resalat").map { it.number }).containsExactly("ResalatBank", "+9820004747")
        assertThat(index.search("5705417").map { it.name }).containsExactly("Elaheh", "Maryam").inOrder()
        assertThat(index.search(" ")).isEmpty()
    }
}
