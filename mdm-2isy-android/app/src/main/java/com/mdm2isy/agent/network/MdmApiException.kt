package com.mdm2isy.agent.network

import java.io.IOException
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

sealed class MdmApiException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

class MdmTransportException(
    cause: IOException,
) : MdmApiException("The MDM server could not be reached.", cause)

class MdmProtocolException(
    message: String,
    cause: Throwable? = null,
) : MdmApiException(message, cause)

open class MdmHttpException internal constructor(
    val status: Int,
    val reason: String?,
    val retryAfterSeconds: Long?,
    val serverMessage: String?,
    val validationErrors: Map<String, List<String>>,
) : MdmApiException(serverMessage ?: "The MDM server returned HTTP $status.")

class MdmAuthenticationException internal constructor(
    reason: String?,
    retryAfterSeconds: Long?,
    serverMessage: String?,
) : MdmHttpException(401, reason, retryAfterSeconds, serverMessage, emptyMap())

class MdmNotFoundException internal constructor(
    reason: String?,
    retryAfterSeconds: Long?,
    serverMessage: String?,
) : MdmHttpException(404, reason, retryAfterSeconds, serverMessage, emptyMap())

class MdmConflictException internal constructor(
    reason: String?,
    retryAfterSeconds: Long?,
    serverMessage: String?,
) : MdmHttpException(409, reason, retryAfterSeconds, serverMessage, emptyMap())

class MdmValidationException internal constructor(
    reason: String?,
    retryAfterSeconds: Long?,
    serverMessage: String?,
    validationErrors: Map<String, List<String>>,
) : MdmHttpException(422, reason, retryAfterSeconds, serverMessage, validationErrors)

class MdmRateLimitException internal constructor(
    reason: String?,
    retryAfterSeconds: Long?,
    serverMessage: String?,
) : MdmHttpException(429, reason, retryAfterSeconds, serverMessage, emptyMap())

internal object RetryAfterParser {
    fun parseSeconds(
        rawValue: String?,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): Long? {
        val value = rawValue?.trim()?.takeIf(String::isNotEmpty) ?: return null
        value.toLongOrNull()?.let { return it.coerceAtLeast(0) }

        val retryAt = runCatching {
            ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
        }.getOrNull() ?: return null
        val remainingMillis = Duration.between(
            java.time.Instant.ofEpochMilli(nowEpochMillis),
            retryAt,
        ).toMillis()

        if (remainingMillis <= 0) return 0
        return (remainingMillis + 999) / 1_000
    }
}
