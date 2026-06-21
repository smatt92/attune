package com.attune.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Turns a plain-language intent into an [IntentPlan] by calling Claude's Messages API with a
 * single forced tool, `emit_plan`, whose schema mirrors [IntentPlan]. The model is grounded:
 * the only tool ids it may reference are the ones in `availableTools` (enforced both by the
 * schema enum AND by defensive validation here — the model can never emit an unregistered tool).
 *
 * Pure Kotlin / JVM: no Android imports, no LLM logic in the tool layer. The HTTP call is behind
 * [HttpTransport] so this is unit-testable with a fake transport and runnable live by the eval
 * harness. NEVER applies anything — parsing is side-effect free (an IntentPlan is a proposal).
 */
class ClaudeIntentParser(
    private val apiKey: String,
    private val model: String = DEFAULT_MODEL,
    private val transport: HttpTransport = JdkHttpTransport(),
    private val baseUrl: String = "https://api.anthropic.com/v1/messages",
    private val maxTokens: Int = 1024,
) : IntentParser {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun parse(intent: String, availableTools: List<ToolDescriptor>): IntentPlan {
        require(apiKey.isNotBlank()) { "ClaudeIntentParser: missing API key" }
        val validIds = availableTools.map { it.id }.toSet()

        val requestBody = buildRequest(intent, availableTools, validIds)
        val response = transport.postJson(
            url = baseUrl,
            headers = mapOf(
                "x-api-key" to apiKey,
                "anthropic-version" to ANTHROPIC_VERSION,
                "content-type" to "application/json",
            ),
            body = requestBody,
        )

        if (response.statusCode !in 200..299) {
            return IntentPlan(intent, emptyList(), listOf("Intent service error (HTTP ${response.statusCode})."))
        }
        return parseResponse(intent, response.body, validIds)
    }

    // --- request ---------------------------------------------------------------------------

    private fun buildRequest(
        intent: String,
        tools: List<ToolDescriptor>,
        validIds: Set<String>,
    ): String {
        val obj = buildJsonObject {
            put("model", model)
            put("max_tokens", maxTokens)
            put("system", systemPrompt(tools))
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", intent)
                }
            }
            putJsonArray("tools") { add(emitPlanTool(validIds)) }
            putJsonObject("tool_choice") {
                put("type", "tool")
                put("name", TOOL_NAME)
            }
        }
        return obj.toString()
    }

    private fun systemPrompt(tools: List<ToolDescriptor>): String {
        val catalog = tools.joinToString("\n") { d ->
            val params = d.params.entries.joinToString(", ") { (name, desc) -> "$name ($desc)" }
            val tier = if (d.requiredPermissions.isEmpty()) "" else " [needs ${d.requiredPermissions.joinToString(", ")}]"
            "- ${d.id}: ${d.description}$tier" + if (params.isNotBlank()) " | params: $params" else ""
        }
        return SYSTEM_PROMPT + "\n\nAvailable tools (you may use ONLY these ids):\n" + catalog
    }

    /** emit_plan's input schema mirrors IntentPlan; toolId is constrained to the real ids. */
    private fun emitPlanTool(validIds: Set<String>): JsonObject = buildJsonObject {
        put("name", TOOL_NAME)
        put("description", "Return the proposed plan of configuration changes for the user's intent.")
        putJsonObject("input_schema") {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("actions") {
                    put("type", "array")
                    putJsonObject("items") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("toolId") {
                                put("type", "string")
                                put("enum", buildJsonArray { validIds.forEach { add(it) } })
                            }
                            putJsonObject("params") {
                                put("type", "object")
                                putJsonObject("additionalProperties") { put("type", "string") }
                            }
                            putJsonObject("humanLabel") { put("type", "string") }
                            putJsonObject("rationale") { put("type", "string") }
                        }
                        putJsonArray("required") { add("toolId"); add("params"); add("humanLabel"); add("rationale") }
                    }
                }
                putJsonObject("warnings") {
                    put("type", "array")
                    putJsonObject("items") { put("type", "string") }
                }
            }
            putJsonArray("required") { add("actions") }
        }
    }

    // --- response --------------------------------------------------------------------------

    private fun parseResponse(intent: String, body: String, validIds: Set<String>): IntentPlan {
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return IntentPlan(intent, emptyList(), listOf("Could not read the intent service response."))

        val content = root["content"]?.let { it as? JsonArray }
            ?: return IntentPlan(intent, emptyList(), listOf("Intent service returned no plan."))

        val toolInput = content.firstOrNull { el ->
            val o = el as? JsonObject ?: return@firstOrNull false
            o["type"]?.jsonPrimitive?.contentOrNull() == "tool_use" &&
                o["name"]?.jsonPrimitive?.contentOrNull() == TOOL_NAME
        }?.jsonObject?.get("input")?.let { it as? JsonObject }
            ?: return IntentPlan(intent, emptyList(), listOf("The model did not return a plan."))

        val warnings = mutableListOf<String>()
        toolInput["warnings"]?.let { it as? JsonArray }?.forEach { w ->
            w.jsonPrimitive.contentOrNull()?.let(warnings::add)
        }

        val actions = mutableListOf<ConfigAction>()
        (toolInput["actions"] as? JsonArray).orEmpty().forEach { el ->
            val a = el as? JsonObject ?: return@forEach
            val toolId = a["toolId"]?.jsonPrimitive?.contentOrNull()
            if (toolId == null || toolId !in validIds) {
                warnings += "Skipped an action referencing an unknown tool: ${toolId ?: "(none)"}."
                return@forEach
            }
            val params = (a["params"] as? JsonObject).orEmpty()
                .mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull()?.let { k to it } }
                .toMap()
            val humanLabel = a["humanLabel"]?.jsonPrimitive?.contentOrNull() ?: toolId
            val rationale = a["rationale"]?.jsonPrimitive?.contentOrNull()
            actions += ConfigAction(toolId, params, humanLabel, rationale)
        }

        return IntentPlan(intent, actions, warnings)
    }

    companion object {
        const val DEFAULT_MODEL = "claude-sonnet-4-6"
        const val ANTHROPIC_VERSION = "2023-06-01"
        const val TOOL_NAME = "emit_plan"

        // Starting system prompt (from the Phase-1 kickoff; refine as the vocabulary matures).
        val SYSTEM_PROMPT = """
            You translate a person's plain-language intent about their Android phone into a PLAN of
            configuration changes. You may ONLY use the tools provided to you; never invent a toolId
            or a setting. Prefer the smallest plan that satisfies the intent. If the intent is
            ambiguous or you are unsure, include fewer actions and add a warning rather than guessing.
            For EVERY action, include a short `rationale` that ties the change to the user's own words
            (e.g. "you mentioned battery, so I'm lowering the screen timeout"). You never apply
            anything — you only propose. Return your plan by calling the emit_plan tool.
        """.trimIndent()
    }
}

private fun JsonPrimitive.contentOrNull(): String? = if (isString) content else content.takeIf { it != "null" }
private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())
private fun JsonObject?.orEmpty(): JsonObject = this ?: JsonObject(emptyMap())
