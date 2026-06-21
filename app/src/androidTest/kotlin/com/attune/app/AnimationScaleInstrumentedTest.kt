package com.attune.app

import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.attune.app.settings.AndroidSettingsContext
import com.attune.app.settings.Permissions
import com.attune.tools.WindowAnimationScaleTool
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/**
 * Proves the thin "does it really write the setting" seam on a real Android system: a tool
 * applied through AndroidSettingsContext changes an actual Global setting and reverts it.
 *
 * Requires WRITE_SECURE_SETTINGS. CI grants it before connectedCheck:
 *   adb shell pm grant com.attune.app android.permission.WRITE_SECURE_SETTINGS
 * If it isn't granted (e.g. a local run without the grant), the test is skipped rather than
 * failing spuriously — the grant is a precondition of this tier, not the thing under test.
 */
@RunWith(AndroidJUnit4::class)
class AnimationScaleInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver = context.contentResolver
    private val key = "window_animation_scale"

    @Test
    fun apply_then_revert_changes_real_global_setting() {
        assumeTrue(
            "WRITE_SECURE_SETTINGS not granted — skipping (grant via adb to run this tier)",
            Permissions.hasWriteSecureSettings(context),
        )

        val original = Settings.Global.getString(resolver, key) ?: "1.0"
        val settings = AndroidSettingsContext(resolver)
        val tool = WindowAnimationScaleTool()

        try {
            val snapshot = tool.apply(settings, mapOf("scale" to "0.0"))
            assertEquals("0.0", Settings.Global.getString(resolver, key), "apply should write the real setting")

            tool.revert(settings, snapshot)
            assertEquals(original, Settings.Global.getString(resolver, key), "revert should restore the original")
        } finally {
            // Defensive: never leave the device altered if an assertion throws mid-test.
            Settings.Global.putString(resolver, key, original)
        }
    }
}
