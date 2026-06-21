package com.attune.core

/**
 * Applies an APPROVED IntentPlan with all-or-nothing semantics.
 * On any failure, reverts everything already applied within this plan (reverse order).
 */
class PlanExecutor(
    private val context: SettingsContext,
    private val tools: Map<String, ConfigTool>,
) {
    fun apply(plan: IntentPlan): ExecutionResult {
        val applied = mutableListOf<ToolSnapshot>()
        try {
            for (action in plan.actions) {
                val tool = tools[action.toolId] ?: error("Unknown tool: ${action.toolId}")
                applied += tool.apply(context, action.params)
            }
            return ExecutionResult.Success(applied)
        } catch (t: Throwable) {
            applied.asReversed().forEach { snap -> tools[snap.toolId]?.revert(context, snap) }
            return ExecutionResult.RolledBack(t.message ?: "apply failed")
        }
    }

    /** Undo a previously applied plan. */
    fun revert(snapshots: List<ToolSnapshot>) {
        snapshots.asReversed().forEach { snap -> tools[snap.toolId]?.revert(context, snap) }
    }
}

sealed interface ExecutionResult {
    data class Success(val snapshots: List<ToolSnapshot>) : ExecutionResult
    data class RolledBack(val reason: String) : ExecutionResult
}
