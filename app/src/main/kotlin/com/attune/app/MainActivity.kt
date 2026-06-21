package com.attune.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.attune.app.onboarding.OnboardingScreen
import com.attune.app.settings.PermissionStatus

/**
 * App entry point for the M3 milestone. Routes between onboarding (when the performance grant is
 * missing) and the main loop placeholder (M5 fills in the real intent → confirm → apply → undo
 * flow). Permission state is re-read whenever the screen resumes so a grant performed over adb is
 * detected without a restart. Nothing here crashes when the permission is absent.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AttuneApp()
                }
            }
        }
    }
}

@Composable
private fun AttuneApp() {
    val context = LocalContext.current
    var status by remember { mutableStateOf(PermissionStatus.read(context)) }
    var continuedPastOnboarding by remember { mutableStateOf(false) }

    // Re-read permissions on every resume (e.g. after the user runs the adb grant and returns).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) status = PermissionStatus.read(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!status.performanceReady || !continuedPastOnboarding) {
        OnboardingScreen(
            status = status,
            onRecheck = { status = PermissionStatus.read(context) },
            onCopyCommand = { command -> copyToClipboard(context, command) },
            onContinue = { continuedPastOnboarding = true },
        )
    } else {
        MainPlaceholder()
    }
}

@Composable
private fun MainPlaceholder() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Attune", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Describe how you want your phone to feel.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Attune adb command", text))
}
