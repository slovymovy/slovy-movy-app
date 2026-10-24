package com.slovy.slovymovyapp.share

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SharedTextReceiverTest {

    @Test
    fun offer_ignores_blank_text() = runBlocking {
        val receiver = SharedTextReceiver()
        receiver.offer("   ", SharedTextSource.PROCESS_TEXT)
        receiver.offer("woord", SharedTextSource.PROCESS_TEXT)
        assertEquals("woord", receiver.requests.first().text, "blank text must not become a request")
    }

    @Test
    fun identical_texts_produce_distinct_requests() = runBlocking {
        val receiver = SharedTextReceiver()
        receiver.offer("woord", SharedTextSource.PROCESS_TEXT)
        val first = receiver.requests.first()
        receiver.offer("woord", SharedTextSource.PROCESS_TEXT)
        val second = receiver.requests.first()
        assertNotEquals(first.serial, second.serial, "a repeated share of the same text must get its own serial")
    }

    @Test
    fun undelivered_request_is_replaced_by_a_later_one() = runBlocking {
        val receiver = SharedTextReceiver()
        receiver.offer("eerste", SharedTextSource.SHARE)
        receiver.offer("tweede", SharedTextSource.SHARE)
        assertEquals("tweede", receiver.requests.first().text, "only the latest undelivered share must be kept")
    }
}
