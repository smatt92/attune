package com.attune.tools

import com.attune.core.SettingsContext
import kotlin.test.Test
import kotlin.test.assertEquals

/** In-memory SettingsContext: lets the whole tool layer be tested without a device. */
class FakeSettings : SettingsContext {
    val global = mutableMapOf<String, String>()
    override fun getSecure(key: String): String? = null
    override fun putSecure(key: String, value: String) {}
    override fun getGlobal(key: String): String? = global[key]
    override fun putGlobal(key: String, value: String) { global[key] = value }
    override fun getSystem(key: String): String? = null
    override fun putSystem(key: String, value: String) {}
}

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
