package com.mdm2isy.agent.network

import com.mdm2isy.agent.model.CommandResultRequest
import com.mdm2isy.agent.model.CommandTransition
import com.mdm2isy.agent.model.DeviceCommand
import com.mdm2isy.agent.model.EnrollmentRequest
import com.mdm2isy.agent.model.EnrollmentResult
import com.mdm2isy.agent.model.HeartbeatReceipt
import com.mdm2isy.agent.model.HeartbeatRequest

/** Blocking transport API. Callers must invoke it from a background thread. */
interface MdmDeviceApi {
    @Throws(MdmApiException::class)
    fun enroll(request: EnrollmentRequest): EnrollmentResult

    @Throws(MdmApiException::class)
    fun heartbeat(
        deviceToken: String,
        request: HeartbeatRequest = HeartbeatRequest(),
    ): HeartbeatReceipt

    @Throws(MdmApiException::class)
    fun poll(deviceToken: String, limit: Int = 10): List<DeviceCommand>

    @Throws(MdmApiException::class)
    fun ack(deviceToken: String, commandPublicId: String): CommandTransition

    @Throws(MdmApiException::class)
    fun result(
        deviceToken: String,
        commandPublicId: String,
        request: CommandResultRequest,
    ): CommandTransition
}
