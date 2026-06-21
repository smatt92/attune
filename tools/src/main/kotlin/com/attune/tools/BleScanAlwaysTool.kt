package com.attune.tools

import com.attune.core.ConfigTool
import com.attune.core.SettingsContext
import com.attune.core.ToolDescriptor
import com.attune.core.ToolSnapshot

/**
 * EXAMPLE tool illustrating the contract. Controls "Bluetooth scanning always available"
 * (Settings.Global key "ble_scan_always_enabled") — a real privacy-relevant toggle.
 *
 * Replace/extend with the full privacy-lockdown tool set in Phase 1.
 */
class BleScanAlwaysTool : ConfigTool {
    override val id = "privacy.ble_scan_always"
    override val requiredPermissions = listOf("android.permission.WRITE_SECURE_SETTINGS")

    override val descriptor = ToolDescriptor(
        id = id,
        description = "Allow or stop apps from scanning for Bluetooth devices while Bluetooth is off.",
        params = mapOf("enabled" to "boolean: true to allow background BLE scanning, false to stop it"),
        requiredPermissions = requiredPermissions,
    )

    private val key = "ble_scan_always_enabled"

    override fun preview(params: Map<String, String>): String =
        if (params["enabled"]?.toBoolean() == true)
            "Allow apps to scan for Bluetooth even when Bluetooth is off"
        else
            "Stop apps from scanning for Bluetooth when Bluetooth is off"

    override fun snapshot(context: SettingsContext) =
        ToolSnapshot(id, mapOf(key to (context.getGlobal(key) ?: "1")))

    override fun apply(context: SettingsContext, params: Map<String, String>): ToolSnapshot {
        val snap = snapshot(context)
        context.putGlobal(key, if (params["enabled"]?.toBoolean() == true) "1" else "0")
        return snap
    }

    override fun revert(context: SettingsContext, snapshot: ToolSnapshot) {
        snapshot.previousState[key]?.let { context.putGlobal(key, it) }
    }
}
