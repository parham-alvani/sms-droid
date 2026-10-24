package me.parham1995.sms.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OneTimeCodesTest {
    @Test
    fun `a Persian dynamic password is found`() {
        assertThat(OneTimeCodes.find("بانک رسالت\nرمز پویا: 482913\nمبلغ: 1,250,000 ریال")).isEqualTo("482913")
    }

    @Test
    fun `Persian digits come back as ASCII`() {
        assertThat(OneTimeCodes.find("کد تایید شما ۵۸۲۱۴۷ است")).isEqualTo("582147")
    }

    @Test
    fun `English codes are found`() {
        assertThat(OneTimeCodes.find("Your verification code is 7712. Do not share it.")).isEqualTo("7712")
        assertThat(OneTimeCodes.find("OTP: 00412233")).isEqualTo("00412233")
    }

    @Test
    fun `a number without a code word is not a code`() {
        assertThat(OneTimeCodes.find("مانده حساب: 125000 ریال")).isNull()
        assertThat(OneTimeCodes.find("Your order 99812 has shipped")).isNull()
    }

    @Test
    fun `a number far from the code word is not taken`() {
        assertThat(OneTimeCodes.find("Never share your code with anyone, including our staff. Ref 482913")).isNull()
    }

    @Test
    fun `too short and too long are not codes`() {
        assertThat(OneTimeCodes.find("code 123")).isNull()
        assertThat(OneTimeCodes.find("code 123456789")).isNull()
    }
}
