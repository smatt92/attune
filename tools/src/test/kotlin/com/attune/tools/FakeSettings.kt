package com.attune.tools

import com.attune.core.SettingsContext

/**
 * In-memory SettingsContext: lets the whole tool layer be tested without a device.
 * Backs all three namespaces so any ConfigTool can be exercised on the JVM.
 */
class FakeSettings(
    val secure: MutableMap<String, String> = mutableMapOf(),
    val global: MutableMap<String, String> = mutableMapOf(),
    val system: MutableMap<String, String> = mutableMapOf(),
) : SettingsContext {
    override fun getSecure(key: String): String? = secure[key]
    override fun putSecure(key: String, value: String) { secure[key] = value }
    override fun getGlobal(key: String): String? = global[key]
    override fun putGlobal(key: String, value: String) { global[key] = value }
    override fun getSystem(key: String): String? = system[key]
    override fun putSystem(key: String, value: String) { system[key] = value }
}
