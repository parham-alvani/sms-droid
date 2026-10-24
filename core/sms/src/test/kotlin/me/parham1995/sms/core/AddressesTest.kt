package me.parham1995.sms.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AddressesTest {
    @Test
    fun `numbers and sender names are told apart`() {
        assertThat(Addresses.isPhoneNumber("+989121234567")).isTrue()
        assertThat(Addresses.isPhoneNumber("0912 123 4567")).isTrue()
        assertThat(Addresses.isPhoneNumber("500-047-47")).isTrue()
        assertThat(Addresses.isPhoneNumber("(021) 8888.1234")).isTrue()

        assertThat(Addresses.isPhoneNumber("ResalatBank")).isFalse()
        assertThat(Addresses.isPhoneNumber("HAMRAH_AVAL")).isFalse()
        assertThat(Addresses.isPhoneNumber("MCI Club")).isFalse()
        assertThat(Addresses.isPhoneNumber("CRA.ir")).isFalse()
        assertThat(Addresses.isPhoneNumber("+")).isFalse()
        assertThat(Addresses.isPhoneNumber("")).isFalse()
    }

    @Test
    fun `a national number gets the country code`() {
        assertThat(Addresses.normalize("0912 123 4567", "98")).isEqualTo("+989121234567")
        assertThat(Addresses.normalize("00989121234567", "98")).isEqualTo("+989121234567")
        assertThat(Addresses.normalize("+98 912-123-4567", "98")).isEqualTo("+989121234567")
    }

    @Test
    fun `short codes and numbers without a plus are not guessed at`() {
        assertThat(Addresses.normalize("500-047-47", "98")).isEqualTo("50004747")
        assertThat(Addresses.normalize("0912", "98")).isEqualTo("0912")
        assertThat(Addresses.normalize("989121234567", "98")).isEqualTo("989121234567")
    }

    @Test
    fun `sender names keep their spelling`() {
        assertThat(Addresses.normalize("  HAMRAH_AVAL ", "98")).isEqualTo("HAMRAH_AVAL")
    }

    @Test
    fun `sender names compare by letters and digits alone`() {
        assertThat(Addresses.nameKey("HAMRAH_AVAL")).isEqualTo(Addresses.nameKey("HAMRAH AVAL"))
        assertThat(Addresses.nameKey("Hamrah-Aval")).isEqualTo(Addresses.nameKey("HAMRAHAVAL"))
        assertThat(Addresses.nameKey("بانک ملت")).isEqualTo("بانکملت")
    }
}
