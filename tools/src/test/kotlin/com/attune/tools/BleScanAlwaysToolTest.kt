package com.attune.tools

import kotlin.test.Test
import kotlin.test.assertEquals

class BleScanAlwaysToolTest {
    @Test
    fun applies_then_reverts_to_original() {
        val settings = FakeSettings().apply { global["ble_scan_always_enabled"] = "1" }
        val tool = BleScanAlwaysTool()

        val snap = tool.apply(settings, mapOf("enabled" to "false"))
        assertEquals("0", settings.global["ble_scan_always_enabled"], "apply should disable scanning")

        tool.revert(settings, snap)
        assertEquals("1", settings.global["ble_scan_always_enabled"], "revert should restore original")
    }
}
