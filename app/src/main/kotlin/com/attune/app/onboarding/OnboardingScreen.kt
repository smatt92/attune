package com.attune.app.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.attune.app.settings.PermissionStatus

private const val GRANT_COMMAND =
    "adb shell pm grant com.attune.app android.permission.WRITE_SECURE_SETTINGS"

/**
 * One-time permission onboarding. Functional and minimal by design — the visual/IA polish of
 * this screen is the product's edge and is human-owned (CLAUDE.md), so this just states the
 * facts, shows the exact command, reflects live grant status, and never blocks or crashes.
 *
 * @param status live permission state (re-read by the host on resume).
 * @param onRecheck re-read permissions (e.g. after the user runs the adb command).
 * @param onCopyCommand copy the adb command to the clipboard.
 * @param onContinue proceed into the app; only meaningful once [PermissionStatus.performanceReady].
 */
@Composable
fun OnboardingScreen(
    status: PermissionStatus,
    onRecheck: () -> Unit,
    onCopyCommand: (String) -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Set up Attune", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Attune changes real system settings (like animation speed) so your phone feels the " +
                "way you describe. To do that it needs one permission that's granted once, over a " +
                "USB cable, using adb. You only do this a single time.",
            style = MaterialTheme.typography.bodyMedium,
        )

        StatusRow("Performance controls (animation speed)", status.writeSecureSettings)
        StatusRow("Font size control", status.writeSettings)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("1. Connect your phone to a computer over USB.", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "2. With adb installed, run this command:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    GRANT_COMMAND,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
                OutlinedButton(onClick = { onCopyCommand(GRANT_COMMAND) }) { Text("Copy command") }
                Text(
                    "3. Tap “I've run it” below — Attune will detect the grant automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        SamsungNote()
        ShizukuNote()

        Button(onClick = onRecheck, modifier = Modifier.fillMaxWidth()) { Text("I've run it — re-check") }

        if (status.performanceReady) {
            Text(
                "Permission granted — you're ready.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
        }
    }
}

@Composable
private fun StatusRow(label: String, granted: Boolean) {
    Text(
        text = (if (granted) "✓ " else "• ") + label + (if (granted) " — ready" else " — not granted yet"),
        style = MaterialTheme.typography.bodyMedium,
        color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SamsungNote() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("On Samsung (One UI)", style = MaterialTheme.typography.titleSmall)
            Text(
                "If the grant is refused, enable Developer options → “USB debugging (Security " +
                    "settings)” — this is separate from normal USB debugging and needs you to be " +
                    "signed into a Samsung account. Then run the command again.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ShizukuNote() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("No cable? Use Shizuku", style = MaterialTheme.typography.titleSmall)
            Text(
                "Shizuku can grant the same permission without a computer after a one-time wireless " +
                    "debugging setup. (Attune's Shizuku path is planned; the adb command above is the " +
                    "supported route for now.)",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
