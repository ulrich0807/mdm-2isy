# No reflection-based JSON library is used. Keep the DeviceAdminReceiver name
# because Android instantiates it from the manifest.
-keep class com.mdm2isy.agent.admin.MdmDeviceAdminReceiver { *; }

