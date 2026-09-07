package com.mdm2isy.agent.network

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RetryAfterParserTest {
    @Test
    fun `parses delta seconds`() {
        assertEquals(45L, RetryAfterParser.parseSeconds("45"))
        assertEquals(0L, RetryAfterParser.parseSeconds("-5"))
    }

    @Test
    fun `parses an RFC 1123 retry date and rounds up`() {
        val now = Instant.parse("2026-08-08T11:59:55.250Z").toEpochMilli()

        assertEquals(
            5L,
            RetryAfterParser.parseSeconds("Sat, 8 Aug 2026 12:00:00 GMT", now),
        )
    }

    @Test
    fun `returns null for an unsupported value`() {
        assertNull(RetryAfterParser.parseSeconds(null))
        assertNull(RetryAfterParser.parseSeconds("later"))
    }
}
