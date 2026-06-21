package com.attune.core

/**
 * The single source of truth for which tools exist. Maps `id -> ConfigTool` and exposes the
 * matching `ToolDescriptor`s so the intent layer can be grounded in real capabilities.
 *
 * Lives in `core` (no Android deps) and is constructed with whatever concrete tools the caller
 * provides (from `:tools`/`:app`) — it never imports tool implementations, preserving the
 * dependency direction tools -> core.
 */
class ToolRegistry(tools: List<ConfigTool>) {
    private val byId: Map<String, ConfigTool> = tools.associateBy { it.id }

    init {
        require(byId.size == tools.size) {
            val dupes = tools.groupingBy { it.id }.eachCount().filter { it.value > 1 }.keys
            "Duplicate tool ids in registry: $dupes"
        }
    }

    /** All registered tools keyed by id — what PlanExecutor needs. */
    val toolsById: Map<String, ConfigTool> get() = byId

    /** The grounded capability list handed to the IntentParser. */
    val descriptors: List<ToolDescriptor> get() = byId.values.map { it.descriptor }

    fun tool(id: String): ConfigTool? = byId[id]

    fun isRegistered(id: String): Boolean = byId.containsKey(id)
}
