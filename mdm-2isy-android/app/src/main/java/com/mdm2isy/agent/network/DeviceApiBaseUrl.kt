package com.mdm2isy.agent.network

import com.mdm2isy.agent.BuildConfig
import java.net.URI
import java.net.URL

class DeviceApiBaseUrl private constructor(
    val value: String,
) {
    fun endpoint(relativePath: String): URL {
        require(relativePath.isNotBlank() && !relativePath.startsWith('/')) {
            "An API-relative path is required."
        }
        require(".." !in relativePath && '?' !in relativePath && '#' !in relativePath) {
            "Unsafe API-relative path."
        }
        return URI.create("$value/$relativePath").toURL()
    }

    override fun toString(): String = value

    companion object {
        private const val REQUIRED_SUFFIX = "/api/v1/device"
        private val DEBUG_CLEARTEXT_HOSTS = setOf("10.0.2.2", "127.0.0.1", "localhost")

        fun from(rawUrl: String): DeviceApiBaseUrl = parse(
            rawUrl = rawUrl,
            allowCleartext = BuildConfig.DEBUG,
        )

        internal fun parse(rawUrl: String, allowCleartext: Boolean): DeviceApiBaseUrl {
            val candidate = rawUrl.trim().trimEnd('/')
            require(candidate.isNotEmpty()) { "The device API URL is required." }

            val uri = runCatching { URI(candidate) }.getOrElse {
                throw IllegalArgumentException("The device API URL is malformed.", it)
            }
            val scheme = uri.scheme?.lowercase()
            require(scheme == "https" || (allowCleartext && scheme == "http")) {
                "HTTPS is required for the device API in this build."
            }
            require(!uri.host.isNullOrBlank()) { "The device API URL must contain a host." }
            if (scheme == "http") {
                require(uri.host.lowercase() in DEBUG_CLEARTEXT_HOSTS) {
                    "Cleartext HTTP is restricted to the local Android emulator host."
                }
            }
            require(uri.userInfo == null) { "Credentials are not allowed in the device API URL." }
            require(uri.rawQuery == null && uri.rawFragment == null) {
                "The device API URL cannot contain a query or fragment."
            }
            require(uri.normalize() == uri) { "The device API URL cannot contain dot segments." }

            val path = uri.path.trimEnd('/')
            require(path.endsWith(REQUIRED_SUFFIX)) {
                "The device API URL must end with $REQUIRED_SUFFIX."
            }

            val canonical = URI(
                scheme,
                null,
                uri.host,
                uri.port,
                path,
                null,
                null,
            ).toASCIIString()

            return DeviceApiBaseUrl(canonical)
        }
    }
}
