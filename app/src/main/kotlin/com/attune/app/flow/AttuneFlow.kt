package com.attune.app.flow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.attune.app.settings.Permissions
import com.attune.core.ExecutionResult
import com.attune.core.IntentPlan
import com.attune.core.ToolSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val EXAMPLE_INTENTS = listOf(
    "make my phone feel faster",
    "turn off animations",
    "put the animations back to normal",
)

/** Confirm-sheet phases (UX logic per the M5 design pack). */
private sealed interface Phase {
    object Input : Phase
    object Working : Phase
    data class Review(val plan: IntentPlan, val previews: List<ActionPreview>) : Phase
    data class Unsure(val warnings: List<String>) : Phase
    object Applying : Phase
    data class Applied(val snapshots: List<ToolSnapshot>, val count: Int) : Phase
    data class RolledBack(val reason: String) : Phase
    data class NeedPermission(val missing: List<String>) : Phase
}

/**
 * The heart of the product: intent → see the plan → approve → applied → undo. Implements the M5
 * design pack's UX logic — intent echo, per-action include/exclude with before→after, an
 * always-visible reversibility promise, the unsure/empty and permission-missing states, and the
 * honest all-or-nothing rollback message. Styling is deliberately plain (visual craft is owned by
 * the designer); the deterministic apply/undo lives in [PlanSession].
 *
 * @param onNeedPermission invoked when a needed grant is missing at apply time (host routes to
 *   onboarding) — never a silent failure.
 */
@Composable
fun AttuneFlow(
    session: PlanSession,
    onNeedPermission: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var intent by remember { mutableStateOf("") }
    var phase by remember { mutableStateOf<Phase>(Phase.Input) }
    val excluded = remember { mutableStateListOf<Int>() } // indices toggled OFF in Review

    fun propose() {
        excluded.clear()
        phase = Phase.Working
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val plan = session.propose(intent)
                    plan to session.preview(plan)
                }
            }
            phase = result.fold(
                onSuccess = { (plan, previews) ->
                    if (plan.actions.isEmpty()) Phase.Unsure(plan.warnings)
                    else Phase.Review(plan, previews)
                },
                onFailure = { Phase.RolledBack("Couldn't reach the intent service.") },
            )
        }
    }

    fun applyPlan(plan: IntentPlan) {
        val included = plan.actions.filterIndexed { i, _ -> i !in excluded }
        val missing = included.flatMap { session.requiredPermissions(it) }.distinct()
            .filterNot { Permissions.isGranted(context, it) }
        if (missing.isNotEmpty()) {
            phase = Phase.NeedPermission(missing)
            return
        }
        phase = Phase.Applying
        phase = when (val result = session.apply(plan, excluded.toSet())) {
            is ExecutionResult.Success -> Phase.Applied(result.snapshots, result.snapshots.size)
            is ExecutionResult.RolledBack -> Phase.RolledBack(result.reason)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Intent input is always present at the top; the sheet content renders below by phase.
        Text("Describe how you want your phone to feel", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = intent,
            onValueChange = { intent = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = phase is Phase.Input || phase is Phase.Unsure || phase is Phase.RolledBack,
            placeholder = { Text("e.g. make my phone feel faster") },
        )
        Button(
            onClick = { propose() },
            enabled = intent.isNotBlank() &&
                (phase is Phase.Input || phase is Phase.Unsure || phase is Phase.RolledBack),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Preview changes") }

        when (val p = phase) {
            is Phase.Input -> Unit
            is Phase.Working -> WorkingState()
            is Phase.Review -> ReviewState(
                intent = intent,
                plan = p.plan,
                previews = p.previews,
                excluded = excluded,
                onNotNow = { phase = Phase.Input; excluded.clear() },
                onApply = { applyPlan(p.plan) },
            )
            is Phase.Unsure -> UnsureState(warnings = p.warnings, onExample = { intent = it })
            is Phase.Applying -> WorkingState(label = "Applying…")
            is Phase.Applied -> AppliedState(
                count = p.count,
                onUndo = {
                    session.undo(p.snapshots)
                    intent = ""
                    excluded.clear()
                    phase = Phase.Input
                },
            )
            is Phase.RolledBack -> RolledBackState(reason = p.reason)
            is Phase.NeedPermission -> NeedPermissionState(onSetUp = onNeedPermission)
        }
    }
}

@Composable
private fun WorkingState(label: String = "Working out the changes…") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator()
        Text(label, modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ReviewState(
    intent: String,
    plan: IntentPlan,
    previews: List<ActionPreview>,
    excluded: MutableList<Int>,
    onNotNow: () -> Unit,
    onApply: () -> Unit,
) {
    HorizontalDivider()
    Text("For “$intent”, here's the plan:", style = MaterialTheme.typography.titleSmall)

    previews.forEachIndexed { index, preview ->
        ActionRow(
            preview = preview,
            included = index !in excluded,
            onToggle = { include -> if (include) excluded.remove(index) else excluded.add(index) },
        )
    }

    plan.warnings.forEach { w ->
        Text("⚠ $w", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }

    // Reversibility promise — always visible; the psychological key that makes approving low-stakes.
    Text(
        "↩ You can undo all of this in one tap.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
    )

    val includedCount = plan.actions.size - excluded.size
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onNotNow, modifier = Modifier.weight(1f)) { Text("Not now") }
        Button(onClick = onApply, enabled = includedCount > 0, modifier = Modifier.weight(1f)) {
            Text(if (includedCount > 0) "Apply $includedCount" else "Nothing selected")
        }
    }
}

@Composable
private fun ActionRow(preview: ActionPreview, included: Boolean, onToggle: (Boolean) -> Unit) {
    var showRaw by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(preview.action.humanLabel, style = MaterialTheme.typography.bodyMedium)
            if (preview.alreadySet) {
                Text("Already set", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(
                    "${friendlyScale(preview.beforeRaw)} → ${friendlyScale(preview.afterRaw)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            preview.action.rationale?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { showRaw = !showRaw }) { Text("What's this?") }
            AnimatedVisibility(visible = showRaw) {
                Text(
                    "Sets ${preview.rawSettingKey ?: preview.action.toolId} = ${preview.afterRaw ?: "?"}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Switch(
            checked = included,
            onCheckedChange = onToggle,
            modifier = Modifier.semantics { contentDescription = "Include: ${preview.action.humanLabel}" },
        )
    }
}

@Composable
private fun UnsureState(warnings: List<String>, onExample: (String) -> Unit) {
    HorizontalDivider()
    Text(
        "I'm not sure what to change for that — try saying it another way.",
        style = MaterialTheme.typography.bodyMedium,
    )
    warnings.forEach { Text("⚠ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    Text("Try:", style = MaterialTheme.typography.labelLarge)
    EXAMPLE_INTENTS.forEach { example ->
        TextButton(onClick = { onExample(example) }) { Text("“$example”") }
    }
}

@Composable
private fun AppliedState(count: Int, onUndo: () -> Unit) {
    HorizontalDivider()
    Text("Done — your phone should feel snappier.", style = MaterialTheme.typography.bodyMedium)
    // Persistent (not a fleeting toast) so "try it risk-free" stays true until the next change set.
    OutlinedButton(onClick = onUndo, modifier = Modifier.fillMaxWidth()) { Text("Undo these $count change(s)") }
}

@Composable
private fun RolledBackState(reason: String) {
    HorizontalDivider()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "One change couldn't be applied, so I undid the rest to keep things consistent. Nothing was changed.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NeedPermissionState(onSetUp: () -> Unit) {
    HorizontalDivider()
    Text(
        "This needs a permission that isn't set up yet.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Button(onClick = onSetUp, modifier = Modifier.fillMaxWidth()) { Text("Set up permissions") }
}

/** Plain-language scale labels for the before→after line (performance persona values). */
private fun friendlyScale(raw: String?): String = when (raw) {
    null -> "—"
    "0.0", "0" -> "Off"
    "0.5" -> "Fast"
    "1.0", "1" -> "Normal"
    else -> "${raw}×"
}
