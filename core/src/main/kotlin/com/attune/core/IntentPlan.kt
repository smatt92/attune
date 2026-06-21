package com.attune.core

/** A single, named, reversible configuration change proposed by the intent layer. */
data class ConfigAction(
    val toolId: String,                 // must map to a registered ConfigTool
    val params: Map<String, String>,
    val humanLabel: String,             // shown in the confirmation sheet
    val rationale: String? = null,
)

/**
 * The structured plan produced from a natural-language intent.
 * INVARIANT: an IntentPlan is a *proposal*. It is never applied without explicit user approval.
 */
data class IntentPlan(
    val intent: String,                 // original user phrasing
    val actions: List<ConfigAction>,
    val warnings: List<String> = emptyList(),
)
