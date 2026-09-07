package com.mdm2isy.agent.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mdm2isy.agent.device.DeviceAdminController
import com.mdm2isy.agent.service.AgentStatusStore
import com.mdm2isy.agent.service.MdmAgentService
import com.mdm2isy.agent.storage.EnrollmentStore

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in SUPPORTED_ACTIONS) return
        if (!EnrollmentStore(context).isEnrolled()) return

        // Android 15+ forbids a BOOT_COMPLETED receiver from starting a
        // dataSync FGS. A provisioned Device Owner uses systemExempted instead.
        if (!DeviceAdminController(context).status().isDeviceOwner) {
            AgentStatusStore(context).update(
                running = false,
                message = "Ouvrez l'application pour relancer l'agent non provisionné.",
            )
            return
        }

        try {
            MdmAgentService.start(context)
        } catch (exception: RuntimeException) {
            AgentStatusStore(context).update(
                running = false,
                message = exception.message?.take(200)
                    ?: "Android a refusé le redémarrage automatique de l'agent.",
            )
        }
    }

    private companion object {
        val SUPPORTED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
