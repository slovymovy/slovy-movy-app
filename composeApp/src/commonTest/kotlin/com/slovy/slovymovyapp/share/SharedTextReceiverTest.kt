package com.slovy.slovymovyapp.share

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SharedTextReceiverTest {

    @Test
    fun offer_ignores_blank_text() {
        val receiver = SharedTextReceiver()
        receiver.offer("   ", SharedTextSource.PROCESS_TEXT)
        assertNull(receiver.pending, "blank text must not become a pending request")
    }

    @Test
    fun identical_texts_produce_distinct_requests() {
        val receiver = SharedTextReceiver()
        receiver.offer("woord", SharedTextSource.PROCESS_TEXT)
        val first = assertNotNull(receiver.pending)
        receiver.consume(first)
        assertNull(receiver.pending, "consumed request must clear pending")

        receiver.offer("woord", SharedTextSource.PROCESS_TEXT)
        val second = assertNotNull(receiver.pending)
        assertNotEquals(first, second, "a repeated share of the same text must be a new request")
        assertEquals(first.text, second.text)
    }

    @Test
    fun consume_of_stale_request_keeps_newer_pending() {
        val receiver = SharedTextReceiver()
        receiver.offer("eerste", SharedTextSource.SHARE)
        val stale = assertNotNull(receiver.pending)
        receiver.offer("tweede", SharedTextSource.SHARE)
        receiver.consume(stale)
        assertEquals("tweede", receiver.pending?.text, "consuming a replaced request must not drop the newer one")
    }
}
