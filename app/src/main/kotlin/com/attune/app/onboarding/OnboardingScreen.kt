package com.attune.app.onboarding

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.attune.app.settings.PermissionStatus
import kotlinx.coroutines.delay

private const val GRANT_COMMAND =
    "adb shell pm grant com.attune.app android.permission.WRITE_SECURE_SETTINGS"

/** Internal navigation for the value-first permission flow (UX logic per the M3 design pack). */
private enum class Route { Welcome, Home, PerformanceMethod, AdbSteps, ShizukuSteps, Granted }

/**
 * Value-first, progressive permission onboarding. Leads with what Attune does and the trust
 * promise; the easy Comfort tier (font) is grantable immediately; the hard Performance tier
 * (WRITE_SECURE_SETTINGS) is framed as a one-time unlock, scaffolded step-by-step, and detected
 * live so the user never taps "continue" to guess whether it worked. The two tiers render and
 * grant independently. Visual craft (type/color/motion/spacing) is intentionally left plain.
 *
 * @param status live permission state (re-read on resume by the host and polled here).
 * @param onRecheck re-read permissions now (used for live polling).
 * @param onCopyCommand copy the adb command to the clipboard.
 * @param onEnableWriteSettings open the system "Modify system settings" page for the Comfort tier.
 * @param onContinue proceed into the app once Performance is ready.
 */
@Composable
fun OnboardingScreen(
    status: PermissionStatus,
    onRecheck: () -> Unit,
    onCopyCommand: (String) -> Unit,
    onEnableWriteSettings: () -> Unit,
    onContinue: () -> Unit,
) {
    var route by remember { mutableStateOf(Route.Welcome) }

    // Live grant-detection: while on a setup step, poll permissions; the moment the secure grant
    // lands, advance to the granted state — no "tap to continue".
    LaunchedEffect(route) {
        if (route == Route.AdbSteps || route == Route.ShizukuSteps || route == Route.PerformanceMethod) {
            while (true) {
                delay(1200)
                onRecheck()
            }
        }
    }
    LaunchedEffect(status.performanceReady) {
        if (status.performanceReady) route = Route.Granted
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (route) {
            Route.Welcome -> Welcome(onNext = { route = Route.Home })
            Route.Home -> Home(
                status = status,
                onSetUpPerformance = { route = Route.PerformanceMethod },
                onEnableWriteSettings = onEnableWriteSettings,
            )
            Route.PerformanceMethod -> PerformanceMethod(
                onAdb = { route = Route.AdbSteps },
                onShizuku = { route = Route.ShizukuSteps },
                onBack = { route = Route.Home },
            )
            Route.AdbSteps -> AdbSteps(
                onCopyCommand = onCopyCommand,
                onRecheck = onRecheck,
                onBack = { route = Route.PerformanceMethod },
            )
            Route.ShizukuSteps -> ShizukuSteps(
                onRecheck = onRecheck,
                onBack = { route = Route.PerformanceMethod },
            )
            Route.Granted -> Granted(onContinue = onContinue)
        }
    }
}

@Composable
private fun Welcome(onNext: () -> Unit) {
    Text("Attune", style = MaterialTheme.typography.headlineMedium)
    Text(
        "Describe how you want your phone to feel, and Attune sets it up for you.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Text(
        "Attune never changes anything without showing you first — and every change can be undone in one tap.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Get started") }
}

@Composable
private fun Home(
    status: PermissionStatus,
    onSetUpPerformance: () -> Unit,
    onEnableWriteSettings: () -> Unit,
) {
    Text("Permissions", style = MaterialTheme.typography.titleLarge)

    // Performance tier (the core demo) — needs the one-time secure grant.
    TierCard(
        title = "Performance",
        body = "Make your phone feel faster by speeding up animations. Needs a one-time setup.",
        granted = status.writeSecureSettings,
    ) {
        if (!status.writeSecureSettings) {
            Button(onClick = onSetUpPerformance, modifier = Modifier.fillMaxWidth()) { Text("Set up") }
        }
    }

    // Comfort tier — grantable immediately, independent of the Performance tier.
    TierCard(
        title = "Comfort",
        body = "Let Attune adjust text size. You can enable this right now.",
        granted = status.writeSettings,
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Switch(
                checked = status.writeSettings,
                onCheckedChange = { if (!status.writeSettings) onEnableWriteSettings() },
                modifier = Modifier.semantics { contentDescription = "Enable text-size changes" },
            )
            Text("Enable text-size changes", modifier = Modifier.padding(start = 8.dp))
        }
    }

    WhySafeExpander()
}

@Composable
private fun PerformanceMethod(onAdb: () -> Unit, onShizuku: () -> Unit, onBack: () -> Unit) {
    Text("Set up performance controls", style = MaterialTheme.typography.titleLarge)
    Text(
        "Android protects these settings. Because Attune isn't built into Samsung's system, you grant " +
            "this once — then never again. Pick how:",
        style = MaterialTheme.typography.bodyMedium,
    )
    Button(onClick = onAdb, modifier = Modifier.fillMaxWidth()) { Text("With a computer (adb)") }
    OutlinedButton(onClick = onShizuku, modifier = Modifier.fillMaxWidth()) { Text("Without a computer (Shizuku)") }
    TextButton(onClick = onBack) { Text("Back") }
}

@Composable
private fun AdbSteps(onCopyCommand: (String) -> Unit, onRecheck: () -> Unit, onBack: () -> Unit) {
    Text("Grant with a computer", style = MaterialTheme.typography.titleLarge)
    Step(1, "On your phone, enable Developer options (tap the build number 7×), then turn on USB debugging.")
    Step(2, "Connect your phone to a computer with adb installed.")
    Step(3, "Run this command once:")
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                GRANT_COMMAND,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
            OutlinedButton(onClick = { onCopyCommand(GRANT_COMMAND) }) { Text("Copy command") }
        }
    }
    Text(
        "Attune is watching — the moment the grant lands, this screen moves on automatically.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    SamsungSnagExpander()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onBack) { Text("Back") }
        OutlinedButton(onClick = onRecheck) { Text("Check now") }
    }
}

@Composable
private fun ShizukuSteps(onRecheck: () -> Unit, onBack: () -> Unit) {
    Text("Grant without a computer", style = MaterialTheme.typography.titleLarge)
    Step(1, "Install Shizuku from the Play Store.")
    Step(2, "Start Shizuku using wireless debugging (no PC needed).")
    Step(3, "Come back and tap “Grant via Shizuku”.")
    // Functional placeholder: the Shizuku integration itself is planned; the flow + live detection
    // are in place so it slots in without UX changes.
    OutlinedButton(onClick = onRecheck, modifier = Modifier.fillMaxWidth()) { Text("Grant via Shizuku") }
    Text(
        "Attune detects the grant automatically and continues.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    TextButton(onClick = onBack) { Text("Back") }
}

@Composable
private fun Granted(onContinue: () -> Unit) {
    Text("You're set", style = MaterialTheme.typography.titleLarge)
    Text(
        "Performance controls are unlocked — you won't be asked for this again.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
}

@Composable
private fun TierCard(title: String, body: String, granted: Boolean, action: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                if (granted) "✓ Enabled" else "Not set up yet",
                style = MaterialTheme.typography.labelLarge,
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(body, style = MaterialTheme.typography.bodyMedium)
            action()
        }
    }
}

@Composable
private fun Step(number: Int, text: String) {
    Row {
        Text("$number.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun WhySafeExpander() {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = !open }) { Text(if (open) "Why is this safe? ▲" else "Why is this safe? ▼") }
    AnimatedVisibility(visible = open) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Android protects these settings, so Attune asks for permission once. It only touches the " +
                        "settings you approve — nothing changes without your say-so, and every change is " +
                        "reversible in one tap. Attune is open source.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SamsungSnagExpander() {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = !open }) { Text(if (open) "The command failed? ▲" else "The command failed? ▼") }
    AnimatedVisibility(visible = open) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("On Samsung (One UI)", style = MaterialTheme.typography.titleSmall)
                Text(
                    "If you see a permission error, also enable Developer options → “USB debugging (Security " +
                        "settings)” — this is separate from normal USB debugging and requires being signed into " +
                        "a Samsung account. Then run the command again.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
