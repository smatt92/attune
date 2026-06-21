package com.attune.tools

import com.attune.core.ConfigTool
import com.attune.core.SettingsContext
import com.attune.core.ToolDescriptor
import com.attune.core.ToolSnapshot

/** Permission strings, kept here so :tools doesn't depend on :app. */
object SettingsPermissions {
    const val WRITE_SECURE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS"
    const val WRITE_SETTINGS = "android.permission.WRITE_SETTINGS"
}

/** Which Settings provider a key lives in. */
enum class SettingsNamespace { GLOBAL, SECURE, SYSTEM }

/**
 * Base for the common case: one capability that reads/writes a SINGLE Settings key. Handles
 * snapshot/apply/revert uniformly across the three namespaces; subclasses only validate input and
 * declare their descriptor. (Tools touching more than one key — e.g. brightness, which must also
 * force manual mode — implement ConfigTool directly.)
 *
 * INVARIANTS preserved: no LLM calls; apply() is reversible via the returned ToolSnapshot;
 * unit-testable against FakeSettings.
 */
abstract class SingleKeySettingTool(
    final override val id: String,
    protected val key: String,
    private val namespace: SettingsNamespace,
    permission: String,
    private val defaultValue: String,
    description: String,
    params: Map<String, String>,
) : ConfigTool {

    final override val requiredPermissions: List<String> = listOf(permission)

    final override val descriptor = ToolDescriptor(id, description, params, requiredPermissions)

    /** Default plain-language preview; subclasses may override for nicer copy. */
    override fun preview(params: Map<String, String>): String =
        runCatching { resolve(params) }.fold(
            onSuccess = { "${descriptor.description} (→ $it)" },
            onFailure = { descriptor.description },
        )

    protected fun read(context: SettingsContext): String? = when (namespace) {
        SettingsNamespace.GLOBAL -> context.getGlobal(key)
        SettingsNamespace.SECURE -> context.getSecure(key)
        SettingsNamespace.SYSTEM -> context.getSystem(key)
    }

    protected fun write(context: SettingsContext, value: String) {
        when (namespace) {
            SettingsNamespace.GLOBAL -> context.putGlobal(key, value)
            SettingsNamespace.SECURE -> context.putSecure(key, value)
            SettingsNamespace.SYSTEM -> context.putSystem(key, value)
        }
    }

    final override fun snapshot(context: SettingsContext): ToolSnapshot =
        ToolSnapshot(id, mapOf(key to (read(context) ?: defaultValue)))

    final override fun apply(context: SettingsContext, params: Map<String, String>): ToolSnapshot {
        val value = resolve(params)
        val snap = snapshot(context)
        write(context, value)
        return snap
    }

    final override fun revert(context: SettingsContext, snapshot: ToolSnapshot) {
        snapshot.previousState[key]?.let { write(context, it) }
    }

    /** Validate params and return the raw value to write; throw IllegalArgumentException on bad input. */
    protected abstract fun resolve(params: Map<String, String>): String
}

/** Reads a boolean param, defaulting to false when absent/garbage. */
internal fun Map<String, String>.boolParam(name: String): Boolean = this[name]?.toBooleanStrictOrNull() ?: false
