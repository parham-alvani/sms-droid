package me.parham1995.sms.core

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

class AdsTest {
    @Test
    fun `the opt-out marker makes an ad`() {
        assertThat(Ads.isAd("۳۰٪ تخفیف پاییزه فقط تا جمعه shop.example/a لغو11")).isTrue()
    }

    @Test
    fun `every way the marker is written is found`() {
        listOf(
            "حراج لغو11",
            "حراج لغو 11",
            "حراج لغو۱۱",
            "حراج لغو ۱۱",
            "حراج لغو١١",
            "حراج لغو: 11",
            "حراج لغو=11",
            "حراج لغو: ارسال 11 به 5000",
            "حراج لغو:۱۱به۲۰۰۰",
            "حراج 11لغو",
            "حراج ۱۱ لغو",
            "حراج لغواا",
            "حراج لغو 11l.eu/s",
            "حراج لغو 111",
        ).forEach { assertWithMessage(it).that(Ads.isAd(it)).isTrue() }
    }

    @Test
    fun `cancelled is not the marker`() {
        assertThat(Ads.isAd("سفارش شما لغو شد. مبلغ به کیف پول برمی‌گردد.")).isFalse()
        assertThat(Ads.isAd("نوبت شما در تاریخ 11 آبان لغو گردیده است")).isFalse()
    }

    @Test
    fun `another number after the word is not the marker`() {
        assertThat(Ads.isAd("حراج لغو 1111")).isFalse()
        assertThat(Ads.isAd("حراج لغو1")).isFalse()
    }

    @Test
    fun `a one-time code is kept even with the marker`() {
        assertThat(Ads.isAd("رمز یکبار مصرف شما: 704944 لغو11")).isFalse()
        assertThat(Ads.isAd("کد تایید: ۵۳۵۱۵۲ لغو۱۱")).isFalse()
    }

    @Test
    fun `a message without the marker is not an ad`() {
        assertThat(Ads.isAd("سلام، فردا ساعت ۱۱ می‌بینمت")).isFalse()
        assertThat(Ads.isAd("Your order 11 has shipped")).isFalse()
    }
}
