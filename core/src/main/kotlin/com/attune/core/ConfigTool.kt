package com.attune.core

/**
 * A deterministic, reversible unit of configuration. Each tool wraps exactly ONE capability
 * with KNOWN effects.
 *
 * INVARIANTS:
 *  - A tool NEVER calls an LLM. (Reasoning lives in the intent layer; tools only execute.)
 *  - apply() must be reversible via the ToolSnapshot it returns.
 *  - A tool must be fully unit-testable against a fake SettingsContext.
 */
interface ConfigTool {
    val id: String
    val requiredPermissions: List<String>     // e.g. listOf("android.permission.WRITE_SECURE_SETTINGS")

    /**
     * What the intent layer is told this tool can do. Grounds the LLM in real capabilities —
     * the parser may only reference ids/params that appear here. The ToolRegistry collects these.
     */
    val descriptor: ToolDescriptor

    /** Pure description of what applying these params will do. No side effects. Powers the confirm sheet. */
    fun preview(params: Map<String, String>): String

    /** Capture current state so the change can be reverted later. No mutation. */
    fun snapshot(context: SettingsContext): ToolSnapshot

    /** Apply the change. Returns the snapshot needed to revert it. */
    fun apply(context: SettingsContext, params: Map<String, String>): ToolSnapshot

    /** Restore captured state. */
    fun revert(context: SettingsContext, snapshot: ToolSnapshot)
}

data class ToolSnapshot(val toolId: String, val previousState: Map<String, String>)

/** What the intent layer is told a tool can do — keeps the model grounded in real capabilities. */
data class ToolDescriptor(
    val id: String,
    val description: String,
    val params: Map<String, String>,         // paramName -> "type: description"
)
