package com.attune.tools

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CatalogToolsTest {

    @Test
    fun font_scale_applies_then_reverts() {
        val settings = FakeSettings(system = mutableMapOf("font_scale" to "1.0"))
        val tool = FontScaleTool()

        val snap = tool.apply(settings, mapOf("scale" to "1.3"))
        assertEquals("1.3", settings.system["font_scale"])

        tool.revert(settings, snap)
        assertEquals("1.0", settings.system["font_scale"])
    }

    @Test
    fun font_scale_reverts_to_default_when_unset() {
        val settings = FakeSettings()
        val tool = FontScaleTool()
        val snap = tool.apply(settings, mapOf("scale" to "0.85"))
        assertEquals("0.85", settings.system["font_scale"])
        tool.revert(settings, snap)
        assertEquals("1.0", settings.system["font_scale"], "unset font_scale defaults to 1.0")
    }

    @Test
    fun font_scale_rejects_garbage_and_out_of_range() {
        assertFailsWith<IllegalArgumentException> { FontScaleTool().apply(FakeSettings(), mapOf("scale" to "big")) }
        assertFailsWith<IllegalArgumentException> { FontScaleTool().apply(FakeSettings(), mapOf("scale" to "9.0")) }
    }

    @Test
    fun screen_off_timeout_applies_then_reverts() {
        val settings = FakeSettings(system = mutableMapOf("screen_off_timeout" to "30000"))
        val tool = ScreenOffTimeoutTool()
        val snap = tool.apply(settings, mapOf("ms" to "60000"))
        assertEquals("60000", settings.system["screen_off_timeout"])
        tool.revert(settings, snap)
        assertEquals("30000", settings.system["screen_off_timeout"])
    }

    @Test
    fun screen_off_timeout_rejects_non_integer_and_zero() {
        assertFailsWith<IllegalArgumentException> { ScreenOffTimeoutTool().apply(FakeSettings(), mapOf("ms" to "soon")) }
        assertFailsWith<IllegalArgumentException> { ScreenOffTimeoutTool().apply(FakeSettings(), mapOf("ms" to "0")) }
    }

    @Test
    fun brightness_mode_toggles_and_reverts() {
        val settings = FakeSettings(system = mutableMapOf("screen_brightness_mode" to "1"))
        val tool = ScreenBrightnessModeTool()
        val snap = tool.apply(settings, mapOf("automatic" to "false"))
        assertEquals("0", settings.system["screen_brightness_mode"], "manual")
        tool.revert(settings, snap)
        assertEquals("1", settings.system["screen_brightness_mode"], "restored to auto")
    }

    @Test
    fun haptic_feedback_toggles_and_reverts() {
        val settings = FakeSettings(system = mutableMapOf("haptic_feedback_enabled" to "1"))
        val tool = HapticFeedbackTool()
        val snap = tool.apply(settings, mapOf("enabled" to "false"))
        assertEquals("0", settings.system["haptic_feedback_enabled"])
        tool.revert(settings, snap)
        assertEquals("1", settings.system["haptic_feedback_enabled"])
    }

    @Test
    fun stay_awake_charging_sets_bitmask_and_reverts() {
        val settings = FakeSettings(global = mutableMapOf("stay_on_while_plugged_in" to "0"))
        val tool = StayAwakeChargingTool()
        val snap = tool.apply(settings, mapOf("enabled" to "true"))
        assertEquals("7", settings.global["stay_on_while_plugged_in"], "7 = all charging types")
        tool.revert(settings, snap)
        assertEquals("0", settings.global["stay_on_while_plugged_in"])
    }

    @Test
    fun brightness_also_forces_manual_mode_so_it_does_not_no_op() {
        // Auto-brightness is ON — a naive brightness write would be overridden. The tool must flip
        // mode to manual AND restore both on revert.
        val settings = FakeSettings(
            system = mutableMapOf("screen_brightness" to "128", "screen_brightness_mode" to "1"),
        )
        val tool = ScreenBrightnessTool()

        val snap = tool.apply(settings, mapOf("level" to "200"))
        assertEquals("200", settings.system["screen_brightness"], "brightness written")
        assertEquals("0", settings.system["screen_brightness_mode"], "mode forced to manual so it sticks")

        tool.revert(settings, snap)
        assertEquals("128", settings.system["screen_brightness"], "brightness restored")
        assertEquals("1", settings.system["screen_brightness_mode"], "auto-brightness restored")
    }

    @Test
    fun brightness_rejects_out_of_range() {
        assertFailsWith<IllegalArgumentException> { ScreenBrightnessTool().apply(FakeSettings(), mapOf("level" to "999")) }
        assertFailsWith<IllegalArgumentException> { ScreenBrightnessTool().apply(FakeSettings(), mapOf("level" to "-1")) }
    }

    @Test
    fun registry_contains_the_full_catalog_with_permission_tiers() {
        val registry = AttuneTools.registry()
        val ids = registry.descriptors.map { it.id }.toSet()
        assertEquals(
            setOf(
                "ux.window_animation_scale", "ux.transition_animation_scale", "ux.animator_duration_scale",
                "ux.font_scale", "ux.screen_off_timeout", "ux.screen_brightness_mode",
                "ux.screen_brightness", "ux.haptic_feedback", "ux.stay_awake_charging",
            ),
            ids,
        )
        // Every descriptor carries its required-permission tier.
        registry.descriptors.forEach { d ->
            assertTrue(d.requiredPermissions.isNotEmpty(), "${d.id} must declare its permission tier")
        }
        // Comfort tier is WRITE_SETTINGS; animation scales + stay-awake are WRITE_SECURE_SETTINGS.
        fun perms(id: String) = registry.tool(id)!!.descriptor.requiredPermissions
        assertEquals(listOf(SettingsPermissions.WRITE_SETTINGS), perms("ux.font_scale"))
        assertEquals(listOf(SettingsPermissions.WRITE_SECURE_SETTINGS), perms("ux.stay_awake_charging"))
        assertEquals(listOf(SettingsPermissions.WRITE_SECURE_SETTINGS), perms("ux.window_animation_scale"))
    }
}
