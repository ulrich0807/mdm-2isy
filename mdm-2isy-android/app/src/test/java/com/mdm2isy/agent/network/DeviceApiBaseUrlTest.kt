package com.mdm2isy.agent.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DeviceApiBaseUrlTest {
    @Test
    fun `release mode accepts and normalizes an HTTPS device API URL`() {
        val baseUrl = DeviceApiBaseUrl.parse(
            "  https://mdm.example.test/platform/api/v1/device/  ",
            allowCleartext = false,
        )

        assertEquals(
            "https://mdm.example.test/platform/api/v1/device",
            baseUrl.value,
        )
        assertEquals(
            "https://mdm.example.test/platform/api/v1/device/commands",
            baseUrl.endpoint("commands").toString(),
        )
    }

    @Test
    fun `release mode rejects cleartext HTTP`() {
        assertThrows(IllegalArgumentException::class.java) {
            DeviceApiBaseUrl.parse(
                "http://mdm.example.test/api/v1/device",
                allowCleartext = false,
            )
        }
    }

    @Test
    fun `debug mode permits emulator cleartext HTTP`() {
        val baseUrl = DeviceApiBaseUrl.parse(
            "http://10.0.2.2:8000/api/v1/device",
            allowCleartext = true,
        )

        assertEquals("http://10.0.2.2:8000/api/v1/device", baseUrl.value)
    }

    @Test
    fun `debug mode rejects cleartext hosts outside the emulator`() {
        assertThrows(IllegalArgumentException::class.java) {
            DeviceApiBaseUrl.parse(
                "http://192.168.1.50:8000/api/v1/device",
                allowCleartext = true,
            )
        }
    }

    @Test
    fun `base URL rejects wrong API path and URL credentials`() {
        assertThrows(IllegalArgumentException::class.java) {
            DeviceApiBaseUrl.parse("https://mdm.example.test/api", allowCleartext = false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeviceApiBaseUrl.parse(
                "https://user:secret@mdm.example.test/api/v1/device",
                allowCleartext = false,
            )
        }
    }
}
