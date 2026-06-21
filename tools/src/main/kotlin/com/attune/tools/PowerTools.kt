package com.attune.tools

/*
 * Secure tier (WRITE_SECURE_SETTINGS, adb-granted), Global namespace.
 *
 * DEVICE-UNVERIFIED on One UI: implemented + unit-tested against FakeSettings, not yet confirmed on
 * the S25. Confirm on-device; mark @Deferred with a note if it no-ops on One UI.
 */

/**
 * Keep the screen on while charging. `stay_on_while_plugged_in` is a bitmask of charging types
 * (AC=1, USB=2, WIRELESS=4); 7 = on for all, 0 = off.
 */
class StayAwakeChargingTool : SingleKeySettingTool(
    id = "ux.stay_awake_charging",
    key = "stay_on_while_plugged_in",
    namespace = SettingsNamespace.GLOBAL,
    permission = SettingsPermissions.WRITE_SECURE_SETTINGS,
    defaultValue = "0",
    description = "Keep the screen on while the phone is charging.",
    params = mapOf("enabled" to "boolean: true to keep the screen on while charging, false for normal"),
) {
    override fun resolve(params: Map<String, String>): String {
        require(params.containsKey("enabled")) { "$id: missing required param 'enabled'" }
        // 7 = AC|USB|WIRELESS (stay on for every charging type), 0 = off.
        return if (params.boolParam("enabled")) "7" else "0"
    }
}
