package dev.mnemolink.desktop

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.mnemolink.app.domain.GenerationStatus
import dev.mnemolink.app.domain.GenerationUiState
import dev.mnemolink.app.domain.GenerationValidation

@Composable
fun DesktopGenerationScreen(
    state: GenerationUiState,
    onConceptChange: (String) -> Unit,
    onContextChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onDraftChange: (String) -> Unit,
    onApprove: () -> Unit,
    onCancel: () -> Unit,
    darkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    noteBrowser: @Composable () -> Unit = {},
    backend: GenerationBackend = GenerationBackend.Demo,
    generationSettings: @Composable () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val generating = state.status == GenerationStatus.Generating
    Surface(modifier = Modifier.fillMaxSize()) {
        Box {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(scrollState).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "MnemoLink",
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.semantics { heading() }
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Dark theme")
                        Switch(
                            checked = darkTheme,
                            onCheckedChange = onDarkThemeChange,
                            modifier = Modifier.semantics { contentDescription = "Dark theme" }
                        )
                    }
                }
                Text("Linux companion for Anki")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "${backend.label} • read-only Anki access",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Generate locally with Ollama, or choose the offline mock demo. " +
                                "No cloud provider or Anki saves are configured. " +
                                "Review AI suggestions: they can be inaccurate."
                        )
                    }
                }
                noteBrowser()
                generationSettings()
                Text(
                    "Review your input",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() }
                )
                OutlinedTextField(
                    value = state.input.concept,
                    onValueChange = onConceptChange,
                    label = { Text("Concept or word") },
                    supportingText = {
                        CharacterCount(
                            state.input.concept.length,
                            GenerationValidation.MAX_CONCEPT_LENGTH
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.input.context,
                    onValueChange = onContextChange,
                    label = { Text("Context or definition (optional)") },
                    supportingText = {
                        CharacterCount(
                            state.input.context.length,
                            GenerationValidation.MAX_CONTEXT_LENGTH
                        )
                    },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Changing input discards the current draft and approval.",
                    style = MaterialTheme.typography.bodySmall
                )
                state.inputError?.let { ErrorText(it) }
                Button(
                    onClick = onGenerate,
                    enabled = state.canGenerate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (state.draft.isBlank()) {
                            if (backend ==
                                GenerationBackend.Demo
                            ) {
                                "Generate demo mnemonic"
                            } else {
                                "Generate local mnemonic"
                            }
                        } else {
                            if (backend ==
                                GenerationBackend.Demo
                            ) {
                                "Regenerate demo mnemonic"
                            } else {
                                "Regenerate local mnemonic"
                            }
                        }
                    )
                }
                if (generating) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        CircularProgressIndicator()
                        Text("Generating a suggestion…")
                    }
                }
                Text(
                    "Review and edit",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() }
                )
                OutlinedTextField(
                    value = state.draft,
                    onValueChange = onDraftChange,
                    label = { Text("Mnemonic draft") },
                    supportingText = {
                        CharacterCount(state.draft.length, GenerationValidation.MAX_DRAFT_LENGTH)
                    },
                    isError = state.draft.isNotEmpty() && state.draftError != null,
                    enabled = !generating,
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )
                if (state.draft.isNotEmpty()) {
                    state.draftError?.let { ErrorText(it) }
                }
                state.errorMessage?.let { ErrorText(it) }
                if (state.status == GenerationStatus.Approved) {
                    Text(
                        "Approved locally — not saved to Anki.",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Button(
                    onClick = onApprove,
                    enabled = state.canApprove,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (backend ==
                            GenerationBackend.Demo
                        ) {
                            "Approve demo draft"
                        } else {
                            "Approve draft locally"
                        }
                    )
                }
                TextButton(
                    onClick = onCancel,
                    enabled = state.draft.isNotEmpty() || generating || state.errorMessage != null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (generating) "Cancel generation" else "Discard draft")
                }
                Text(
                    "Drafts are in memory only and disappear when the window closes. " +
                        "Copy important drafts elsewhere until durable recovery is implemented.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(scrollState),
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
            )
        }
    }
}

@Composable
private fun CharacterCount(count: Int, limit: Int) {
    Text("Characters: $count / $limit")
}

@Composable
private fun ErrorText(message: String) {
    Text(message, color = MaterialTheme.colorScheme.error)
}
