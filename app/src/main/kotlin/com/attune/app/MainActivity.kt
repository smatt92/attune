package com.attune.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.attune.app.flow.AttuneFlow
import com.attune.app.flow.PlanSession
import com.attune.app.onboarding.OnboardingScreen
import com.attune.app.settings.AndroidSettingsContext
import com.attune.app.settings.PermissionStatus
import com.attune.core.ClaudeIntentParser
import com.attune.tools.AttuneTools

/**
 * App entry point. Routes between onboarding (when the performance grant is missing) and the
 * end-to-end loop (AttuneFlow: intent → previewed plan → approve → apply → one-tap undo). Permission
 * state is re-read whenever the screen resumes so a grant performed over adb is detected without a
 * restart. Nothing here crashes when the permission is absent.
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
        val session = remember {
            PlanSession(
                parser = ClaudeIntentParser(apiKey = BuildConfig.ANTHROPIC_API_KEY),
                registry = AttuneTools.registry(),
                settings = AndroidSettingsContext(context.contentResolver),
            )
        }
        AttuneFlow(session)
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Attune adb command", text))
}
