package com.attune.app.flow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.attune.core.ExecutionResult
import com.attune.core.IntentPlan
import com.attune.core.ToolSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The end-to-end loop, built functional-and-minimal: intent input -> previewed plan with a
 * per-action include/exclude toggle and any warnings -> Apply -> a persistent one-tap Undo.
 *
 * This is the product's UX/IA edge and is human-owned (CLAUDE.md) — styling is intentionally plain
 * so the visual/IA design can be done separately. The deterministic apply/undo logic lives in
 * [PlanSession]; this composable only holds screen state.
 */
@Composable
fun AttuneFlow(session: PlanSession, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()

    var intent by remember { mutableStateOf("") }
    var plan by remember { mutableStateOf<IntentPlan?>(null) }
    val excluded = remember { mutableStateListOf<Int>() } // indices the user toggled OFF
    var busy by remember { mutableStateOf(false) }
    var appliedSnapshots by remember { mutableStateOf<List<ToolSnapshot>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun reset() {
        plan = null; excluded.clear(); appliedSnapshots = null
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Describe how you want your phone to feel", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = intent,
            onValueChange = { intent = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy && appliedSnapshots == null,
            placeholder = { Text("e.g. make my phone feel faster") },
        )

        Button(
            onClick = {
                message = null
                busy = true
                reset()
                scope.launch {
                    val proposed = runCatching { withContext(Dispatchers.IO) { session.propose(intent) } }
                    busy = false
                    proposed.onSuccess { plan = it }.onFailure { message = "Couldn't reach the intent service." }
                }
            },
            enabled = intent.isNotBlank() && !busy && appliedSnapshots == null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Preview changes") }

        if (busy) CircularProgressIndicator()

        val currentPlan = plan
        if (currentPlan != null && appliedSnapshots == null) {
            Divider()
            if (currentPlan.actions.isEmpty()) {
                Text("No changes proposed for that.")
            } else {
                Text("Proposed changes", style = MaterialTheme.typography.titleSmall)
                currentPlan.actions.forEachIndexed { index, action ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = index !in excluded,
                            onCheckedChange = { include -> if (include) excluded.remove(index) else excluded.add(index) },
                        )
                        Column(modifier = Modifier.padding(start = 4.dp)) {
                            Text(action.humanLabel, style = MaterialTheme.typography.bodyMedium)
                            action.rationale?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            currentPlan.warnings.forEach { w ->
                Text("⚠ $w", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = {
                    when (val result = session.apply(currentPlan, excluded.toSet())) {
                        is ExecutionResult.Success -> {
                            appliedSnapshots = result.snapshots
                            message = "Applied ${result.snapshots.size} change(s)."
                        }
                        is ExecutionResult.RolledBack -> message = "Couldn't apply: ${result.reason}"
                    }
                },
                enabled = currentPlan.actions.size > excluded.size, // at least one included
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Apply") }
        }

        appliedSnapshots?.let { snapshots ->
            Divider()
            Text("Changes applied.", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(
                onClick = {
                    session.undo(snapshots)
                    message = "Reverted."
                    intent = ""
                    reset()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Undo this change set") }
        }

        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}
