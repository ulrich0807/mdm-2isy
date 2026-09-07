package com.mdm2isy.agent.network

import com.mdm2isy.agent.BuildConfig
import com.mdm2isy.agent.model.CommandResultRequest
import com.mdm2isy.agent.model.CommandTransition
import com.mdm2isy.agent.model.DeviceCommand
import com.mdm2isy.agent.model.EnrollmentRequest
import com.mdm2isy.agent.model.EnrollmentResult
import com.mdm2isy.agent.model.HeartbeatReceipt
import com.mdm2isy.agent.model.HeartbeatRequest
import com.mdm2isy.agent.model.isCanonicalUuid
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class MdmApiClient(
    baseUrl: String,
    private val connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
) : MdmDeviceApi {
    private val baseUrl = DeviceApiBaseUrl.from(baseUrl)

    init {
        require(connectTimeoutMs in 1..MAX_TIMEOUT_MS) {
            "connectTimeoutMs must be between 1 and $MAX_TIMEOUT_MS."
        }
        require(readTimeoutMs in 1..MAX_TIMEOUT_MS) {
            "readTimeoutMs must be between 1 and $MAX_TIMEOUT_MS."
        }
    }

    override fun enroll(request: EnrollmentRequest): EnrollmentResult {
        val response = execute(
            url = baseUrl.endpoint("enroll"),
            method = "POST",
            body = MdmJsonCodec.encodeEnrollment(request),
            deviceToken = null,
        )
        return MdmJsonCodec.parseEnrollment(response)
    }

    override fun heartbeat(
        deviceToken: String,
        request: HeartbeatRequest,
    ): HeartbeatReceipt {
        val response = execute(
            url = baseUrl.endpoint("heartbeat"),
            method = "POST",
            body = MdmJsonCodec.encodeHeartbeat(request),
            deviceToken = deviceToken,
        )
        return MdmJsonCodec.parseHeartbeat(response)
    }

    override fun poll(deviceToken: String, limit: Int): List<DeviceCommand> {
        require(limit in 1..50) { "The command poll limit must be between 1 and 50." }
        val endpoint = baseUrl.endpoint("commands")
        val url = URL("${endpoint}?limit=$limit")
        val response = execute(
            url = url,
            method = "GET",
            body = null,
            deviceToken = deviceToken,
        )
        return MdmJsonCodec.parseCommands(response)
    }

    override fun ack(deviceToken: String, commandPublicId: String): CommandTransition {
        requireCommandId(commandPublicId)
        val response = execute(
            url = baseUrl.endpoint("commands/$commandPublicId/ack"),
            method = "POST",
            body = null,
            deviceToken = deviceToken,
        )
        return MdmJsonCodec.parseTransition(response)
    }

    override fun result(
        deviceToken: String,
        commandPublicId: String,
        request: CommandResultRequest,
    ): CommandTransition {
        requireCommandId(commandPublicId)
        val response = execute(
            url = baseUrl.endpoint("commands/$commandPublicId/result"),
            method = "POST",
            body = MdmJsonCodec.encodeResult(request),
            deviceToken = deviceToken,
        )
        return MdmJsonCodec.parseTransition(response)
    }

    private fun execute(
        url: URL,
        method: String,
        body: String?,
        deviceToken: String?,
    ): String {
        if (deviceToken != null) requireDeviceToken(deviceToken)

        val connection = try {
            url.openConnection() as HttpURLConnection
        } catch (exception: IOException) {
            throw MdmTransportException(exception)
        }

        return try {
            connection.requestMethod = method
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept", JSON_CONTENT_TYPE)
            connection.setRequestProperty("User-Agent", "MDM-2ISY-Agent/${BuildConfig.VERSION_NAME}")
            if (deviceToken != null) {
                connection.setRequestProperty("Authorization", "Bearer $deviceToken")
            }

            if (body != null) {
                val bytes = body.toByteArray(Charsets.UTF_8)
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "$JSON_CONTENT_TYPE; charset=utf-8")
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }

            val status = connection.responseCode
            val successful = status in 200..299
            val responseBody = readBody(
                stream = if (successful) connection.inputStream else connection.errorStream,
            )

            if (successful) {
                if (responseBody.truncated) {
                    throw MdmProtocolException("The MDM server response exceeded the size limit.")
                }
                responseBody.value
            } else {
                throw httpException(
                    status = status,
                    retryAfter = connection.getHeaderField("Retry-After"),
                    body = responseBody.value,
                )
            }
        } catch (exception: MdmApiException) {
            throw exception
        } catch (exception: IOException) {
            throw MdmTransportException(exception)
        } finally {
            connection.disconnect()
        }
    }

    private fun httpException(status: Int, retryAfter: String?, body: String): MdmHttpException {
        val error = MdmJsonCodec.parseHttpError(body)
        val retryAfterSeconds = RetryAfterParser.parseSeconds(retryAfter)

        return when (status) {
            401 -> MdmAuthenticationException(
                error.reason,
                retryAfterSeconds,
                error.message,
            )
            404 -> MdmNotFoundException(
                error.reason,
                retryAfterSeconds,
                error.message,
            )
            409 -> MdmConflictException(
                error.reason,
                retryAfterSeconds,
                error.message,
            )
            422 -> MdmValidationException(
                error.reason,
                retryAfterSeconds,
                error.message,
                error.validationErrors,
            )
            429 -> MdmRateLimitException(
                error.reason,
                retryAfterSeconds,
                error.message,
            )
            else -> MdmHttpException(
                status = status,
                reason = error.reason,
                retryAfterSeconds = retryAfterSeconds,
                serverMessage = error.message,
                validationErrors = error.validationErrors,
            )
        }
    }

    private fun readBody(stream: InputStream?): ResponseBody {
        if (stream == null) return ResponseBody("", truncated = false)

        return stream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var truncated = false

            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                val remaining = MAX_RESPONSE_BYTES - output.size()
                if (remaining <= 0) {
                    truncated = true
                    break
                }
                output.write(buffer, 0, minOf(count, remaining))
                if (count > remaining) {
                    truncated = true
                    break
                }
            }

            ResponseBody(output.toString(Charsets.UTF_8.name()), truncated)
        }
    }

    private fun requireCommandId(publicId: String) {
        require(isCanonicalUuid(publicId)) { "The command public ID must be a UUID." }
    }

    private fun requireDeviceToken(deviceToken: String) {
        require(
            deviceToken.startsWith("mdm_device_") &&
                deviceToken.length in 12..512 &&
                deviceToken.all { it.code in 0x21..0x7e },
        ) { "A valid device credential is required." }
    }

    private data class ResponseBody(
        val value: String,
        val truncated: Boolean,
    )

    private companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MS = 15_000
        const val DEFAULT_READ_TIMEOUT_MS = 30_000
        const val MAX_TIMEOUT_MS = 120_000
        const val MAX_RESPONSE_BYTES = 1_048_576
        const val JSON_CONTENT_TYPE = "application/json"
    }
}
