package com.attune.app.settings

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import com.attune.core.ConfigTool

/**
 * Attune's permission model has two independent tiers:
 *
 *  - WRITE_SECURE_SETTINGS — needed for the animation scales (Global). A normal app cannot
 *    hold it; it is granted once over adb:
 *        adb shell pm grant com.attune.app android.permission.WRITE_SECURE_SETTINGS
 *  - WRITE_SETTINGS — needed for System keys like font_scale. User-grantable at runtime via
 *    the "Modify system settings" screen (Settings.System.canWrite()).
 *
 * The two are checked separately so a missing secure grant never blocks the font path, and the
 * UI can show exactly what is and isn't available rather than crashing on a write.
 */
object Permissions {

    const val WRITE_SECURE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS"
    const val WRITE_SETTINGS = "android.permission.WRITE_SETTINGS"

    fun hasWriteSecureSettings(context: Context): Boolean =
        context.checkSelfPermission(WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun canWriteSystemSettings(context: Context): Boolean =
        Settings.System.canWrite(context)

    /** Is [permission] currently held? Unknown permissions are treated as not held. */
    fun isGranted(context: Context, permission: String): Boolean = when (permission) {
        WRITE_SECURE_SETTINGS -> hasWriteSecureSettings(context)
        WRITE_SETTINGS -> canWriteSystemSettings(context)
        else -> context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    /** The permissions [tool] needs that are not yet granted. Empty = the tool can run. */
    fun missingFor(context: Context, tool: ConfigTool): List<String> =
        tool.requiredPermissions.filterNot { isGranted(context, it) }

    fun canRun(context: Context, tool: ConfigTool): Boolean = missingFor(context, tool).isEmpty()
}

/** Snapshot of grant state for the onboarding screen to render and react to. */
data class PermissionStatus(
    val writeSecureSettings: Boolean,
    val writeSettings: Boolean,
) {
    /** The performance persona (animation scales) is usable once the secure grant is held. */
    val performanceReady: Boolean get() = writeSecureSettings

    companion object {
        fun read(context: Context) = PermissionStatus(
            writeSecureSettings = Permissions.hasWriteSecureSettings(context),
            writeSettings = Permissions.canWriteSystemSettings(context),
        )
    }
}
