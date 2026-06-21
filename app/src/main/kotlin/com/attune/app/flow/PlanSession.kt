package com.attune.app.flow

import com.attune.core.ConfigAction
import com.attune.core.ExecutionResult
import com.attune.core.IntentParser
import com.attune.core.IntentPlan
import com.attune.core.PlanExecutor
import com.attune.core.SettingsContext
import com.attune.core.ToolRegistry
import com.attune.core.ToolSnapshot

/** A plain-language preview of one proposed change, including its current → target values. */
data class ActionPreview(
    val action: ConfigAction,
    val beforeRaw: String?, // current value of the setting (read via the tool's snapshot)
    val afterRaw: String?,  // target value the action would write
    val rawSettingKey: String?, // the underlying Settings key, for the "what's this?" reveal
) {
    val alreadySet: Boolean get() = beforeRaw != null && beforeRaw == afterRaw
}

/**
 * The deterministic core of the M5 loop, deliberately free of Compose so it can be driven straight
 * from an instrumented test: propose (LLM) -> apply only the user-approved actions -> undo via the
 * stored snapshots. The parser is injected, so tests use a deterministic fake while the app uses
 * the real ClaudeIntentParser.
 *
 * INVARIANTS preserved: nothing applies without an explicit apply() call (the UI gates that behind
 * approval), and every apply is reversible via the ToolSnapshots it returns. Apply is all-or-nothing
 * with rollback (PlanExecutor) — a failed write reverts everything already applied in the set.
 */
class PlanSession(
    private val parser: IntentParser,
    private val registry: ToolRegistry,
    private val settings: SettingsContext,
) {
    private val executor = PlanExecutor(settings, registry.toolsById)

    /** Side-effect free: ask the model for a proposed plan grounded in the registered tools. */
    suspend fun propose(intent: String): IntentPlan = parser.parse(intent, registry.descriptors)

    /**
     * Current → target preview for each action, read via the tool's side-effect-free snapshot().
     * Lets the confirm sheet show "before → after" and flag no-ops as "already set". Reads settings,
     * so call off the UI thread (the flow does this on Dispatchers.IO right after propose()).
     */
    fun preview(plan: IntentPlan): List<ActionPreview> = plan.actions.map { action ->
        val tool = registry.tool(action.toolId)
        val snapshot = tool?.snapshot(settings)
        val key = snapshot?.previousState?.keys?.firstOrNull()
        ActionPreview(
            action = action,
            beforeRaw = key?.let { snapshot.previousState[it] },
            afterRaw = action.params.values.firstOrNull(),
            rawSettingKey = key,
        )
    }

    /** Permissions the action's tool needs — lets the sheet route to onboarding before applying. */
    fun requiredPermissions(action: ConfigAction): List<String> =
        registry.tool(action.toolId)?.requiredPermissions ?: emptyList()

    /**
     * Apply only the actions the user left included. [excludedIndices] are positions into
     * [plan].actions the user toggled off. Returns the executor result; on Success the caller keeps
     * the snapshots for one-tap undo.
     */
    fun apply(plan: IntentPlan, excludedIndices: Set<Int> = emptySet()): ExecutionResult {
        val included = plan.actions.filterIndexed { index, _ -> index !in excludedIndices }
        return executor.apply(plan.copy(actions = included))
    }

    /** Revert a previously applied change set (reverse order). */
    fun undo(snapshots: List<ToolSnapshot>) = executor.revert(snapshots)
}
