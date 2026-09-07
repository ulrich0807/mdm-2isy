package com.mdm2isy.agent.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandModelsTest {
    @Test
    fun `locate proof carries the exact backend success evidence`() {
        val proof = CommandProof.locate(
            lat = 5.3599,
            lng = -4.0083,
            accuracyM = 12.5,
        )
        val result = CommandResultRequest.succeeded(proof)

        assertEquals(CommandResultStatus.SUCCEEDED, result.status)
        assertEquals(5.3599, result.proof?.lat ?: 0.0, 0.0)
        assertEquals(-4.0083, result.proof?.lng ?: 0.0, 0.0)
        assertNull(result.errorCode)
    }

    @Test
    fun `lock and wipe factories can only produce true success evidence`() {
        assertTrue(CommandProof.lock().locked == true)
        assertTrue(CommandProof.wipe().wipeStarted == true)
    }

    @Test
    fun `failure validates the backend error code grammar`() {
        val failure = CommandResultRequest.failed(
            errorCode = "DEVICE_POLICY_REJECTED",
            errorMessage = "Policy unavailable",
        )

        assertEquals(CommandResultStatus.FAILED, failure.status)
        assertEquals("DEVICE_POLICY_REJECTED", failure.errorCode)

        assertThrows(IllegalArgumentException::class.java) {
            CommandResultRequest.failed("lowercase-error")
        }
    }

    @Test
    fun `heartbeat coordinates must be paired`() {
        assertThrows(IllegalArgumentException::class.java) {
            HeartbeatRequest(latitude = 5.3599)
        }
    }
}
