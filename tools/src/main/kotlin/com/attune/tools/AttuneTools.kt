package com.attune.tools

import com.attune.core.ConfigTool
import com.attune.core.ToolRegistry

/**
 * The canonical list of tools Attune ships, in one place so the app, the executor, and the
 * intent-eval harness all agree on what exists. Phase 1 = the performance persona only.
 */
object AttuneTools {

    /** The performance persona: the three live animation-scale controls. */
    fun performanceTools(): List<ConfigTool> = listOf(
        WindowAnimationScaleTool(),
        TransitionAnimationScaleTool(),
        AnimatorDurationScaleTool(),
    )

    /** All tools registered for Phase 1. `BleScanAlwaysTool` stays out — it is only a contract example. */
    fun registry(): ToolRegistry = ToolRegistry(performanceTools())
}
