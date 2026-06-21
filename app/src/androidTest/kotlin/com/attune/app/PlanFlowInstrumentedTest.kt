package com.attune.app

import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.attune.app.flow.PlanSession
import com.attune.app.settings.AndroidSettingsContext
import com.attune.app.settings.Permissions
import com.attune.core.ConfigAction
import com.attune.core.ExecutionResult
import com.attune.core.IntentParser
import com.attune.core.IntentPlan
import com.attune.core.ToolDescriptor
import com.attune.tools.AttuneTools
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers the full M5 loop on a real Android system: propose (deterministic fake parser) -> apply the
 * approved plan through PlanExecutor + AndroidSettingsContext (a real Global write) -> one-tap undo
 * restores the original. The parser is faked so the test is hermetic — the apply/undo seam is what's
 * under test, not the LLM.
 *
 * Requires WRITE_SECURE_SETTINGS (CI grants it before connectedCheck); skipped otherwise.
 */
@RunWith(AndroidJUnit4::class)
class PlanFlowInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver = context.contentResolver
    private val key = "window_animation_scale"

    /** Always proposes turning window animations off — deterministic, no network. */
    private val fakeParser = object : IntentParser {
        override suspend fun parse(intent: String, availableTools: List<ToolDescriptor>): IntentPlan =
            IntentPlan(
                intent = intent,
                actions = listOf(
                    ConfigAction(
                        toolId = "ux.window_animation_scale",
                        params = mapOf("scale" to "0.0"),
                        humanLabel = "Turn off window animations",
                    ),
                ),
            )
    }

    @Test
    fun intent_to_approve_to_apply_to_undo_changes_then_restores_real_setting() {
        assumeTrue(
            "WRITE_SECURE_SETTINGS not granted — skipping",
            Permissions.hasWriteSecureSettings(context),
        )

        val original = Settings.Global.getString(resolver, key) ?: "1.0"
        val session = PlanSession(
            parser = fakeParser,
            registry = AttuneTools.registry(),
            settings = AndroidSettingsContext(resolver),
        )

        try {
            // 1. propose (intent -> plan)
            val plan = runBlocking { session.propose("make my phone feel faster") }
            assertEquals(1, plan.actions.size)

            // 2. approve + apply (all actions included)
            val result = session.apply(plan, excludedIndices = emptySet())
            assertTrue("apply should succeed", result is ExecutionResult.Success)
            val snapshots = (result as ExecutionResult.Success).snapshots
            assertEquals("0.0", Settings.Global.getString(resolver, key))

            // 3. one-tap undo restores the original
            session.undo(snapshots)
            assertEquals(original, Settings.Global.getString(resolver, key))
        } finally {
            Settings.Global.putString(resolver, key, original)
        }
    }

    @Test
    fun excluded_action_is_not_applied() {
        assumeTrue(
            "WRITE_SECURE_SETTINGS not granted — skipping",
            Permissions.hasWriteSecureSettings(context),
        )

        val original = Settings.Global.getString(resolver, key) ?: "1.0"
        val session = PlanSession(fakeParser, AttuneTools.registry(), AndroidSettingsContext(resolver))

        try {
            val plan = runBlocking { session.propose("anything") }
            // User toggled the only action OFF -> nothing should be written.
            val result = session.apply(plan, excludedIndices = setOf(0))
            assertTrue(result is ExecutionResult.Success)
            assertEquals("excluded action must not change the setting", original, Settings.Global.getString(resolver, key))
        } finally {
            Settings.Global.putString(resolver, key, original)
        }
    }
}
