package com.attune.core

/**
 * Thin abstraction over Android's Settings providers (Secure / Global / System).
 * The real implementation writes via android.provider.Settings; the test implementation
 * is an in-memory map. This boundary is what makes the entire tool layer unit-testable
 * on the JVM with no device or emulator.
 */
interface SettingsContext {
    fun getSecure(key: String): String?
    fun putSecure(key: String, value: String)
    fun getGlobal(key: String): String?
    fun putGlobal(key: String, value: String)
    fun getSystem(key: String): String?
    fun putSystem(key: String, value: String)
}
