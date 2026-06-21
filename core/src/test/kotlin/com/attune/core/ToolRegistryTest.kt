package com.attune.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Minimal ConfigTool stub — registry behaviour needs only id + descriptor, not real settings. */
private class StubTool(override val id: String) : ConfigTool {
    override val requiredPermissions = emptyList<String>()
    override val descriptor = ToolDescriptor(id = id, description = "stub", params = emptyMap())
    override fun preview(params: Map<String, String>) = "stub"
    override fun snapshot(context: SettingsContext) = ToolSnapshot(id, emptyMap())
    override fun apply(context: SettingsContext, params: Map<String, String>) = ToolSnapshot(id, emptyMap())
    override fun revert(context: SettingsContext, snapshot: ToolSnapshot) {}
}

class ToolRegistryTest {

    @Test
    fun maps_ids_and_exposes_descriptors() {
        val registry = ToolRegistry(listOf(StubTool("a"), StubTool("b")))

        assertTrue(registry.isRegistered("a"))
        assertEquals("b", registry.tool("b")?.id)
        assertEquals(setOf("a", "b"), registry.toolsById.keys)
        assertEquals(setOf("a", "b"), registry.descriptors.map { it.id }.toSet())
    }

    @Test
    fun unknown_ids_are_not_registered() {
        val registry = ToolRegistry(listOf(StubTool("a")))
        assertFalse(registry.isRegistered("nope"))
        assertNull(registry.tool("nope"))
    }

    @Test
    fun rejects_duplicate_ids() {
        assertFailsWith<IllegalArgumentException> {
            ToolRegistry(listOf(StubTool("dupe"), StubTool("dupe")))
        }
    }
}
