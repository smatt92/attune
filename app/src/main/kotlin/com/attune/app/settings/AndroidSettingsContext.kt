package com.attune.app.settings

import android.content.ContentResolver
import android.provider.Settings
import com.attune.core.SettingsContext

/**
 * The real SettingsContext: reads/writes Android's Settings providers via a ContentResolver.
 *
 * Writes to Global/Secure require WRITE_SECURE_SETTINGS (adb-granted once); writes to System
 * require WRITE_SETTINGS (Settings.System.canWrite). Without the right grant the provider throws
 * SecurityException — callers must gate on Permissions (see Permissions.kt) and surface what is
 * blocked rather than attempt the write. The tool/executor layer treats a thrown write as a
 * failure and rolls back; the UI prevents getting there by checking permissions first.
 */
class AndroidSettingsContext(private val resolver: ContentResolver) : SettingsContext {

    override fun getSecure(key: String): String? = Settings.Secure.getString(resolver, key)

    override fun putSecure(key: String, value: String) {
        Settings.Secure.putString(resolver, key, value)
    }

    override fun getGlobal(key: String): String? = Settings.Global.getString(resolver, key)

    override fun putGlobal(key: String, value: String) {
        Settings.Global.putString(resolver, key, value)
    }

    override fun getSystem(key: String): String? = Settings.System.getString(resolver, key)

    override fun putSystem(key: String, value: String) {
        Settings.System.putString(resolver, key, value)
    }
}
