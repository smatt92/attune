package com.attune.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AnimationScaleToolsTest {

    private val cases = listOf(
        Triple(WindowAnimationScaleTool(), "ux.window_animation_scale", "window_animation_scale"),
        Triple(TransitionAnimationScaleTool(), "ux.transition_animation_scale", "transition_animation_scale"),
        Triple(AnimatorDurationScaleTool(), "ux.animator_duration_scale", "animator_duration_scale"),
    )

    @Test
    fun apply_sets_fast_scale_then_revert_restores_original() {
        for ((tool, _, key) in cases) {
            val settings = FakeSettings(global = mutableMapOf(key to "1.0"))

            val snap = tool.apply(settings, mapOf("scale" to "0.5"))
            assertEquals("0.5", settings.global[key], "${tool.id}: apply should speed up animations")

            tool.revert(settings, snap)
            assertEquals("1.0", settings.global[key], "${tool.id}: revert should restore the original")
        }
    }

    @Test
    fun revert_restores_default_when_setting_was_unset() {
        for ((tool, _, key) in cases) {
            val settings = FakeSettings() // key absent → system default is 1.0

            val snap = tool.apply(settings, mapOf("scale" to "0.0"))
            assertEquals("0.0", settings.global[key], "${tool.id}: apply should turn animations off")

            tool.revert(settings, snap)
            assertEquals("1.0", settings.global[key], "${tool.id}: revert should restore the 1.0 default")
        }
    }

    @Test
    fun apply_rejects_non_numeric_and_negative_values() {
        for ((tool, _, _) in cases) {
            assertFailsWith<IllegalArgumentException>("${tool.id}: should reject non-numeric") {
                tool.apply(FakeSettings(), mapOf("scale" to "fast"))
            }
            assertFailsWith<IllegalArgumentException>("${tool.id}: should reject negative") {
                tool.apply(FakeSettings(), mapOf("scale" to "-1.0"))
            }
        }
    }

    @Test
    fun descriptor_and_permissions_are_grounded() {
        for ((tool, id, _) in cases) {
            assertEquals(id, tool.descriptor.id)
            assertTrue(tool.descriptor.params.containsKey("scale"), "${tool.id}: descriptor exposes 'scale'")
            assertEquals(
                listOf("android.permission.WRITE_SECURE_SETTINGS"),
                tool.requiredPermissions,
                "${tool.id}: animation scales need WRITE_SECURE_SETTINGS",
            )
        }
    }
}
