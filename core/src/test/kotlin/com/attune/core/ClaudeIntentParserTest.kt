package com.attune.core

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Captures the outgoing request and returns a canned response — no network, fully deterministic. */
private class FakeTransport(private val response: HttpResponse) : HttpTransport {
    var lastBody: String? = null
    override fun postJson(url: String, headers: Map<String, String>, body: String): HttpResponse {
        lastBody = body
        return response
    }
}

private val descriptors = listOf(
    ToolDescriptor("ux.window_animation_scale", "window animation speed", mapOf("scale" to "string: speed")),
    ToolDescriptor("ux.transition_animation_scale", "transition speed", mapOf("scale" to "string: speed")),
)

private fun toolUseResponse(inputJson: String) = HttpResponse(
    200,
    """{"content":[{"type":"tool_use","name":"emit_plan","input":$inputJson}]}""",
)

class ClaudeIntentParserTest {

    @Test
    fun forces_emit_plan_tool_and_injects_real_tool_ids() {
        val transport = FakeTransport(toolUseResponse("""{"actions":[]}"""))
        val parser = ClaudeIntentParser(apiKey = "k", transport = transport)

        runBlocking { parser.parse("make it fast", descriptors) }

        val body = transport.lastBody!!
        assertTrue(body.contains("\"tool_choice\""), "request must force a tool")
        assertTrue(body.contains("\"name\":\"emit_plan\""), "must force the emit_plan tool")
        assertTrue(body.contains("ux.window_animation_scale"), "must inject real tool ids for grounding")
    }

    @Test
    fun parses_tool_use_into_plan() {
        val input = """
            {"actions":[
              {"toolId":"ux.window_animation_scale","params":{"scale":"0.5"},"humanLabel":"Speed up windows","rationale":"snappier"}
            ],"warnings":["heads up"]}
        """.trimIndent()
        val parser = ClaudeIntentParser(apiKey = "k", transport = FakeTransport(toolUseResponse(input)))

        val plan = runBlocking { parser.parse("make it fast", descriptors) }

        assertEquals(1, plan.actions.size)
        assertEquals("ux.window_animation_scale", plan.actions[0].toolId)
        assertEquals("0.5", plan.actions[0].params["scale"])
        assertEquals("Speed up windows", plan.actions[0].humanLabel)
        assertEquals(listOf("heads up"), plan.warnings)
    }

    @Test
    fun rejects_actions_with_unregistered_tool_id() {
        val input = """
            {"actions":[
              {"toolId":"ux.window_animation_scale","params":{"scale":"0.5"},"humanLabel":"ok"},
              {"toolId":"evil.delete_everything","params":{},"humanLabel":"nope"}
            ]}
        """.trimIndent()
        val parser = ClaudeIntentParser(apiKey = "k", transport = FakeTransport(toolUseResponse(input)))

        val plan = runBlocking { parser.parse("do stuff", descriptors) }

        assertEquals(1, plan.actions.size, "unregistered tool must be dropped")
        assertEquals("ux.window_animation_scale", plan.actions[0].toolId)
        assertTrue(plan.warnings.any { it.contains("evil.delete_everything") }, "must warn about the rejected tool")
    }

    @Test
    fun api_error_status_yields_warning_not_crash() {
        val parser = ClaudeIntentParser(apiKey = "k", transport = FakeTransport(HttpResponse(429, "rate limited")))
        val plan = runBlocking { parser.parse("anything", descriptors) }
        assertTrue(plan.actions.isEmpty())
        assertTrue(plan.warnings.any { it.contains("429") })
    }

    @Test
    fun missing_tool_use_block_yields_warning_not_crash() {
        val parser = ClaudeIntentParser(
            apiKey = "k",
            transport = FakeTransport(HttpResponse(200, """{"content":[{"type":"text","text":"hi"}]}""")),
        )
        val plan = runBlocking { parser.parse("anything", descriptors) }
        assertTrue(plan.actions.isEmpty())
        assertTrue(plan.warnings.isNotEmpty())
    }
}
