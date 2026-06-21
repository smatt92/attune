package com.attune.tools

import com.attune.core.ConfigTool
import com.attune.core.SettingsContext
import com.attune.core.ToolDescriptor
import com.attune.core.ToolSnapshot

/*
 * Comfort tier — all WRITE_SETTINGS (user-grantable in-app, no computer needed). Standard AOSP
 * System keys that Samsung generally honors.
 *
 * DEVICE-UNVERIFIED: implemented + unit-tested against FakeSettings, but NOT yet confirmed to take
 * effect on the S25 / One UI. Confirm on-device; if any silently no-ops on One UI, mark it
 * @Deferred with the failure note rather than shipping a tool that does nothing.
 */

/** Text size. `font_scale` is a multiplier; typical accessibility range ~0.85–1.3. */
class FontScaleTool : SingleKeySettingTool(
    id = "ux.font_scale",
    key = "font_scale",
    namespace = SettingsNamespace.SYSTEM,
    permission = SettingsPermissions.WRITE_SETTINGS,
    defaultValue = "1.0",
    description = "Set the system text size. Larger values make text bigger; 1.0 is default.",
    params = mapOf("scale" to "string: text-size multiplier, e.g. \"0.85\" small, \"1.0\" default, \"1.3\" large"),
) {
    override fun resolve(params: Map<String, String>): String {
        val raw = params["scale"] ?: error("$id: missing required param 'scale'")
        val scale = requireNotNull(raw.toFloatOrNull()) { "$id: 'scale' must be a number, got '$raw'" }
        require(scale in 0.5f..2.0f) { "$id: 'scale' must be between 0.5 and 2.0, got '$raw'" }
        return raw
    }
}

/** How long the screen stays on before sleeping, in milliseconds. */
class ScreenOffTimeoutTool : SingleKeySettingTool(
    id = "ux.screen_off_timeout",
    key = "screen_off_timeout",
    namespace = SettingsNamespace.SYSTEM,
    permission = SettingsPermissions.WRITE_SETTINGS,
    defaultValue = "30000",
    description = "Set how long the screen stays on before it turns off, in milliseconds.",
    params = mapOf("ms" to "integer: milliseconds, e.g. \"15000\" (15s), \"60000\" (1m), \"600000\" (10m)"),
) {
    override fun resolve(params: Map<String, String>): String {
        val raw = params["ms"] ?: error("$id: missing required param 'ms'")
        val ms = requireNotNull(raw.toLongOrNull()) { "$id: 'ms' must be an integer, got '$raw'" }
        require(ms in 1_000..86_400_000) { "$id: 'ms' must be between 1000 and 86400000, got '$raw'" }
        return ms.toString()
    }
}

/** Auto-brightness on/off. 1 = automatic, 0 = manual. */
class ScreenBrightnessModeTool : SingleKeySettingTool(
    id = "ux.screen_brightness_mode",
    key = "screen_brightness_mode",
    namespace = SettingsNamespace.SYSTEM,
    permission = SettingsPermissions.WRITE_SETTINGS,
    defaultValue = "1",
    description = "Turn automatic brightness on or off. When on, the system adjusts brightness to ambient light.",
    params = mapOf("automatic" to "boolean: true for auto-brightness, false for manual"),
) {
    override fun resolve(params: Map<String, String>): String {
        require(params.containsKey("automatic")) { "$id: missing required param 'automatic'" }
        return if (params.boolParam("automatic")) "1" else "0"
    }
}

/** Touch vibration on/off. */
class HapticFeedbackTool : SingleKeySettingTool(
    id = "ux.haptic_feedback",
    key = "haptic_feedback_enabled",
    namespace = SettingsNamespace.SYSTEM,
    permission = SettingsPermissions.WRITE_SETTINGS,
    defaultValue = "1",
    description = "Turn touch/haptic vibration feedback on or off.",
    params = mapOf("enabled" to "boolean: true to enable touch vibration, false to disable"),
) {
    override fun resolve(params: Map<String, String>): String {
        require(params.containsKey("enabled")) { "$id: missing required param 'enabled'" }
        return if (params.boolParam("enabled")) "1" else "0"
    }
}

/**
 * Screen brightness level (0–255). Touches TWO keys: it also forces `screen_brightness_mode` to
 * manual (0), because a brightness value set while auto-brightness is on is immediately overridden —
 * the tool would silently no-op otherwise. Both keys are snapshotted so revert restores the prior
 * brightness AND the prior auto/manual mode.
 *
 * DEVICE-UNVERIFIED on One UI (see file header in ComfortTools.kt for the rule).
 */
class ScreenBrightnessTool : ConfigTool {
    override val id = "ux.screen_brightness"
    override val requiredPermissions = listOf(SettingsPermissions.WRITE_SETTINGS)

    private val brightnessKey = "screen_brightness"
    private val modeKey = "screen_brightness_mode"

    override val descriptor = ToolDescriptor(
        id = id,
        description = "Set the screen brightness level (0–255). Also switches brightness to manual so the " +
            "level holds (auto-brightness would otherwise override it).",
        params = mapOf("level" to "integer: brightness from 0 (dimmest) to 255 (brightest)"),
        requiredPermissions = requiredPermissions,
    )

    override fun preview(params: Map<String, String>): String =
        "Set brightness to ${params["level"] ?: "?"}/255 (switches to manual brightness)"

    override fun snapshot(context: SettingsContext): ToolSnapshot = ToolSnapshot(
        id,
        mapOf(
            brightnessKey to (context.getSystem(brightnessKey) ?: "128"),
            modeKey to (context.getSystem(modeKey) ?: "1"),
        ),
    )

    override fun apply(context: SettingsContext, params: Map<String, String>): ToolSnapshot {
        val raw = params["level"] ?: error("$id: missing required param 'level'")
        val level = requireNotNull(raw.toIntOrNull()) { "$id: 'level' must be an integer, got '$raw'" }
        require(level in 0..255) { "$id: 'level' must be between 0 and 255, got '$raw'" }
        val snap = snapshot(context)
        context.putSystem(modeKey, "0") // manual, so the brightness value actually sticks
        context.putSystem(brightnessKey, level.toString())
        return snap
    }

    override fun revert(context: SettingsContext, snapshot: ToolSnapshot) {
        // Restore mode first, then the level, so we don't briefly fight auto-brightness on revert.
        snapshot.previousState[modeKey]?.let { context.putSystem(modeKey, it) }
        snapshot.previousState[brightnessKey]?.let { context.putSystem(brightnessKey, it) }
    }
}
