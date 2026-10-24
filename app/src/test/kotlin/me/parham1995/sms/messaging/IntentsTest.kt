package me.parham1995.sms.messaging

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IntentsTest {
    @Test
    fun `one recipient`() {
        assertThat(Intents.recipients("+989121234567")).containsExactly("+989121234567")
    }

    @Test
    fun `several recipients split by comma or semicolon`() {
        assertThat(
            Intents.recipients("0912 123 4567; +98935,  ResalatBank"),
        ).containsExactly("0912 123 4567", "+98935", "ResalatBank").inOrder()
    }

    @Test
    fun `the query is not a recipient`() {
        assertThat(Intents.recipients("+989121234567?body=hi")).containsExactly("+989121234567")
        assertThat(Intents.recipients("?body=hi")).isEmpty()
    }
}
