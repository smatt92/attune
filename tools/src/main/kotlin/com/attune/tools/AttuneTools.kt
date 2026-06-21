package com.attune.tools

import com.attune.core.ConfigTool
import com.attune.core.ToolRegistry

/**
 * The canonical list of tools Attune ships, in one place so the app, the executor, and the
 * intent-eval harness all agree on what exists. Grouped by permission tier (not by user-facing
 * persona — the intent vocabulary is owned separately and lives in the golden corpus).
 *
 * DEVICE-UNVERIFIED: every tool except the three animation scales is implemented + unit-tested but
 * not yet confirmed to take effect on the S25 / One UI. See the per-file headers.
 */
object AttuneTools {

    /** Animation scales — the live, instantly-visible performance controls (WRITE_SECURE_SETTINGS). */
    fun performanceTools(): List<ConfigTool> = listOf(
        WindowAnimationScaleTool(),
        TransitionAnimationScaleTool(),
        AnimatorDurationScaleTool(),
    )

    /** Comfort/display controls — user-grantable WRITE_SETTINGS (no computer needed). */
    fun writeSettingsTools(): List<ConfigTool> = listOf(
        FontScaleTool(),
        ScreenOffTimeoutTool(),
        ScreenBrightnessModeTool(),
        ScreenBrightnessTool(),
        HapticFeedbackTool(),
    )

    /** Additional WRITE_SECURE_SETTINGS controls beyond the animation scales. */
    fun secureSettingsTools(): List<ConfigTool> = listOf(
        StayAwakeChargingTool(),
    )

    /** Everything registered for the app to compose plans from. */
    fun allTools(): List<ConfigTool> = performanceTools() + writeSettingsTools() + secureSettingsTools()

    /** The full registry. `BleScanAlwaysTool` stays out — it is only a contract example. */
    fun registry(): ToolRegistry = ToolRegistry(allTools())
}
