package com.attune.app

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * App entry point. This is the minimal M1 skeleton so the :app module compiles and the
 * three-module project is real. The actual Attune loop — onboarding (M3), intent input,
 * confirmation sheet, apply + one-tap undo (M5) — is built in later milestones and is the
 * human-owned UX surface per CLAUDE.md, so it is intentionally not designed here.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AttunePlaceholder()
                }
            }
        }
    }
}

@Composable
private fun AttunePlaceholder() {
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

@Preview(showBackground = true)
@Composable
private fun AttunePlaceholderPreview() {
    MaterialTheme { AttunePlaceholder() }
}
