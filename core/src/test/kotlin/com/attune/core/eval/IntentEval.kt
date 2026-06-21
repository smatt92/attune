package com.attune.core.eval

import com.attune.core.ClaudeIntentParser
import com.attune.core.IntentParser
import com.attune.core.ToolDescriptor
import com.attune.tools.AttuneTools
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.system.exitProcess

/**
 * Tier-2 eval harness (ARCHITECTURE.md): treat the non-deterministic LLM layer like a model, not
 * like code. Loads the golden corpus, runs each phrasing through the real ClaudeIntentParser, and
 * reports the fraction of `mustInclude` actions the parser actually produced. Gates on a threshold.
 *
 * Costs API calls — run via `./gradlew :core:intentEval` with ANTHROPIC_API_KEY set (CI does this
 * on schedule/dispatch). Cases that reference tools not currently registered are SKIPPED (so the
 * legacy privacy corpus doesn't fail while only the performance persona ships).
 */
private data class Case(val intent: String, val mustInclude: List<Expected>)
private data class Expected(val toolId: String, val params: Map<String, String>)

fun main() {
    val apiKey = System.getenv("ANTHROPIC_API_KEY").orEmpty()
    if (apiKey.isBlank()) {
        System.err.println("intentEval: ANTHROPIC_API_KEY is not set — cannot run the live eval.")
        exitProcess(2)
    }
    // Default the eval to Haiku to keep it cheap (overridable); the app default stays Sonnet.
    val model = System.getenv("ATTUNE_EVAL_MODEL")?.takeIf { it.isNotBlank() }
        ?: "claude-haiku-4-5-20251001"
    val threshold = System.getenv("ATTUNE_EVAL_THRESHOLD")?.toDoubleOrNull() ?: 0.80

    val registry = AttuneTools.registry()
    val descriptors: List<ToolDescriptor> = registry.descriptors
    val registeredIds = descriptors.map { it.id }.toSet()
    val parser: IntentParser = ClaudeIntentParser(apiKey = apiKey, model = model)

    val corpus = loadCorpus()
    if (corpus.isEmpty()) {
        System.err.println("intentEval: no golden corpus found under resources/intents.")
        exitProcess(2)
    }

    var required = 0
    var satisfied = 0
    var skipped = 0
    println("intentEval — model=$model threshold=${(threshold * 100).toInt()}%")

    for ((file, cases) in corpus) {
        for (case in cases) {
            if (case.mustInclude.any { it.toolId !in registeredIds }) {
                skipped++
                println("  [skip] (${file}) \"${case.intent}\" — references unregistered tools")
                continue
            }
            val plan = runBlocking { parser.parse(case.intent, descriptors) }
            var caseHits = 0
            for (exp in case.mustInclude) {
                required++
                val present = plan.actions.any { a ->
                    a.toolId == exp.toolId && exp.params.all { (k, v) -> a.params[k] == v }
                }
                if (present) { satisfied++; caseHits++ }
            }
            val mark = if (caseHits == case.mustInclude.size) "pass" else "PARTIAL"
            println("  [$mark] (${file}) \"${case.intent}\" — $caseHits/${case.mustInclude.size}")
            plan.warnings.forEach { println("      warning: $it") }
        }
    }

    val rate = if (required == 0) 0.0 else satisfied.toDouble() / required
    println("intentEval result: $satisfied/$required mustInclude actions (${(rate * 100).toInt()}%), $skipped case(s) skipped")
    if (rate + 1e-9 < threshold) {
        System.err.println("intentEval FAILED: ${(rate * 100).toInt()}% < ${(threshold * 100).toInt()}% threshold")
        exitProcess(1)
    }
    println("intentEval PASSED")
}

private fun loadCorpus(): List<Pair<String, List<Case>>> {
    val dirUrl = object {}.javaClass.getResource("/intents") ?: return emptyList()
    val dir = File(dirUrl.toURI())
    val json = Json { ignoreUnknownKeys = true }
    return dir.listFiles { f -> f.extension == "json" }.orEmpty().sorted().map { file ->
        val root = json.parseToJsonElement(file.readText()).jsonObject
        val cases = (root["cases"] as? JsonArray).orEmptyArray().map { el ->
            val o = el.jsonObject
            Case(
                intent = o["intent"]!!.jsonPrimitive.content,
                mustInclude = (o["mustInclude"] as JsonArray).map { m ->
                    val mo = m.jsonObject
                    Expected(
                        toolId = mo["toolId"]!!.jsonPrimitive.content,
                        params = (mo["params"] as? JsonObject).orEmptyObject()
                            .mapValues { (_, v) -> v.jsonPrimitive.content },
                    )
                },
            )
        }
        file.name to cases
    }
}

private fun JsonArray?.orEmptyArray(): JsonArray = this ?: JsonArray(emptyList())
private fun JsonObject?.orEmptyObject(): JsonObject = this ?: JsonObject(emptyMap())
