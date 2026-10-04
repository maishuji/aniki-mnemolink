package dev.mnemolink.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun GenerationSettingsPanel(
    selected: GenerationBackend,
    onSelect: (GenerationBackend) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Generation service", style = MaterialTheme.typography.titleLarge)
            GenerationBackend.entries.forEach { backend ->
                TextButton(onClick = { onSelect(backend) }, enabled = selected != backend) {
                    Text(if (selected == backend) "Selected: ${backend.label}" else backend.label)
                }
            }
            Text("Settings are session-only. Changing the service discards the draft and approval.")
            if (selected != GenerationBackend.Demo) {
                Text("Ollama endpoint: http://127.0.0.1:11434 • output language: French")
                Text(
                    "Only concept and context are sent after confirmation. No key or automatic download."
                )
                Text(
                    "Use locally installed models, not cloud-backed aliases. Ollama must be trusted and local."
                )
                Text(
                    "A request can take up to 60 seconds, including model loading. No automatic retries."
                )
            } else {
                Text("Deterministic mock suggestions; no model or server requests.")
            }
        }
    }
}
