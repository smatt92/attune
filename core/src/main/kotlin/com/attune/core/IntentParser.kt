package com.attune.core

/**
 * Turns a natural-language intent into an IntentPlan using an LLM (Claude).
 *
 * Implementations MUST:
 *  - only emit actions whose toolId is in `availableTools`,
 *  - never apply anything (parsing is side-effect free),
 *  - be evaluated against the golden corpus in core/src/test/resources/intents/.
 */
interface IntentParser {
    suspend fun parse(intent: String, availableTools: List<ToolDescriptor>): IntentPlan
}
