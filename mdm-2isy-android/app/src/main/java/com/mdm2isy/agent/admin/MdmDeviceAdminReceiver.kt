package com.mdm2isy.agent.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context

/**
 * Android entry point that grants the agent access to device-policy APIs.
 *
 * Provisioning the application as Device Owner is deliberately handled outside
 * this receiver. Merely installing the APK or activating a legacy device admin
 * does not turn the terminal into a fully managed device.
 */
class MdmDeviceAdminReceiver : DeviceAdminReceiver() {
    companion object {
        @JvmStatic
        fun componentName(context: Context): ComponentName =
            ComponentName(context.applicationContext, MdmDeviceAdminReceiver::class.java)
    }
}
