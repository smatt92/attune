package com.attune.tools

import com.attune.core.ConfigTool
import com.attune.core.SettingsContext
import com.attune.core.ToolDescriptor
import com.attune.core.ToolSnapshot

/**
 * The PERFORMANCE persona. The three Android animation-scale settings live in
 * `Settings.Global` and are applied live by the system (no reboot):
 *   - "1.0" = stock, "0.5" = visibly snappier (the persona default), "0.0" = animations off.
 *
 * All three require WRITE_SECURE_SETTINGS (adb-granted once on the S25; a normal app cannot
 * hold it). Behaviour is identical apart from the key, so it lives in this base class; the
 * three concrete tools just bind an id, a Global key, and a human-facing name.
 *
 * NOTE: keys/behaviour are standard AOSP that Samsung One UI honors, but pin them down with the
 * instrumented test on the actual S25 (M3) — that is the layer where ROM quirks surface.
 */
sealed class AnimationScaleTool(
    final override val id: String,
    private val key: String,
    private val humanName: String,
) : ConfigTool {

    final override val requiredPermissions = listOf("android.permission.WRITE_SECURE_SETTINGS")

    final override val descriptor = ToolDescriptor(
        id = id,
        description = "Set the $humanName speed. Lower is faster; the system applies it instantly.",
        params = mapOf(
            PARAM to "string: speed multiplier — \"0.0\" off (instant), \"0.5\" fast, \"1.0\" default",
        ),
        requiredPermissions = requiredPermissions,
    )

    final override fun preview(params: Map<String, String>): String =
        when (val v = params[PARAM]) {
            "0.0" -> "Turn off $humanName (instant)"
            "0.5" -> "Speed up $humanName (0.5×)"
            "1.0" -> "Reset $humanName to default (1×)"
            null -> "Change $humanName"
            else -> "Set $humanName to ${v}×"
        }

    final override fun snapshot(context: SettingsContext): ToolSnapshot =
        // Unset animation scales default to "1.0" on Android — restore to that if absent.
        ToolSnapshot(id, mapOf(key to (context.getGlobal(key) ?: DEFAULT)))

    final override fun apply(context: SettingsContext, params: Map<String, String>): ToolSnapshot {
        val value = params[PARAM] ?: error("$id: missing required param '$PARAM'")
        val scale = requireNotNull(value.toFloatOrNull()) { "$id: '$PARAM' must be a number, got '$value'" }
        require(scale >= 0f) { "$id: '$PARAM' must be >= 0, got '$value'" }
        val snap = snapshot(context)
        context.putGlobal(key, value)
        return snap
    }

    final override fun revert(context: SettingsContext, snapshot: ToolSnapshot) {
        snapshot.previousState[key]?.let { context.putGlobal(key, it) }
    }

    companion object {
        const val PARAM = "scale"
        const val DEFAULT = "1.0"
    }
}

/** Window open/close animation speed (`Global window_animation_scale`). */
class WindowAnimationScaleTool : AnimationScaleTool(
    id = "ux.window_animation_scale",
    key = "window_animation_scale",
    humanName = "window animations",
)

/** Transitions between screens (`Global transition_animation_scale`). */
class TransitionAnimationScaleTool : AnimationScaleTool(
    id = "ux.transition_animation_scale",
    key = "transition_animation_scale",
    humanName = "screen transitions",
)

/** Animator duration for in-app motion (`Global animator_duration_scale`). */
class AnimatorDurationScaleTool : AnimationScaleTool(
    id = "ux.animator_duration_scale",
    key = "animator_duration_scale",
    humanName = "in-app animations",
)
