package dev.mnemolink.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.mnemolink.desktop.feature.anki.AnkiBrowserController
import dev.mnemolink.desktop.feature.anki.AnkiBrowserState
import dev.mnemolink.desktop.feature.anki.AnkiConnection
import dev.mnemolink.desktop.feature.anki.AnkiOperation
import dev.mnemolink.desktop.feature.anki.ankiHtmlToPlainText

@Composable
fun AnkiBrowserPanel(
    state: AnkiBrowserState,
    controller: AnkiBrowserController,
    onLoadInput: () -> Unit
) {
    var apiKey by remember { mutableStateOf("") }
    val idle = state.operation == AnkiOperation.Idle
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Read from Anki", style = MaterialTheme.typography.titleLarge)
            Text("Keep Anki running with AnkiConnect installed (2055492159). Local access only.")
            if (state.connection == AnkiConnection.Disconnected) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("AnkiConnect API key (optional, session only)") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = {
                    controller.connect(apiKey)
                    apiKey = ""
                }) { Text("Connect to Anki") }
            } else {
                Text(
                    if (state.connection == AnkiConnection.Connecting) {
                        "Connecting…"
                    } else {
                        "Connected — API ${state.apiVersion}"
                    }
                )
                TextButton(onClick = controller::disconnect) { Text("Disconnect") }
            }
            if (state.connection == AnkiConnection.Connected) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = controller::updateQuery,
                    label = { Text("Anki search query") },
                    supportingText = { Text("Use Anki search syntax; maximum 512 characters.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = controller::search, enabled = state.canSearch) {
                    Text("Search notes")
                }
                if (!idle) {
                    Text(
                        if (state.operation ==
                            AnkiOperation.Searching
                        ) {
                            "Searching…"
                        } else {
                            "Loading note…"
                        }
                    )
                }
                state.totalMatches?.let {
                    Text(
                        "$it matching notes; showing the first ${state.results.size} (maximum 20)."
                    )
                    if (it > 20) Text("Narrow your query to find other notes.")
                }
                state.results.forEach { note ->
                    TextButton(
                        onClick = { controller.selectNote(note.noteId) },
                        enabled = idle,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val preview = ankiHtmlToPlainText(
                            note.fields.firstOrNull()?.value.orEmpty()
                        )
                            .replace('\n', ' ').take(100)
                        Text("${note.modelName} • ${note.noteId}\n$preview")
                    }
                }
                state.selectedNote?.let { note ->
                    Text("Selected: ${note.modelName} • ${note.noteId}")
                    val names = note.fields.map { it.name }
                    FieldPicker(
                        "Concept field",
                        state.mapping?.conceptField,
                        names,
                        idle,
                        controller::chooseConcept
                    )
                    FieldPicker(
                        "Context field",
                        state.mapping?.contextField,
                        names,
                        idle,
                        { controller.chooseContext(it.ifEmpty { null }) },
                        optional = true
                    )
                    FieldPicker(
                        "Destination field (future saving)",
                        state.mapping?.destinationField,
                        names,
                        idle,
                        controller::chooseDestination
                    )
                    Text(
                        "An existing, distinct destination is required. No fields are created or saved."
                    )
                    state.inputPreview?.let { input ->
                        Text("Plaintext preview", style = MaterialTheme.typography.titleMedium)
                        Text("Concept: ${input.concept}")
                        Text("Context: ${input.context.ifBlank { "(none)" }}")
                    }
                    state.mappingError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(onClick = onLoadInput, enabled = state.canLoadInput) {
                        Text("Load input")
                    }
                    Text("Copies input only; it does not authorize or bind a future Anki write.")
                }
            }
            state.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun FieldPicker(
    label: String,
    selected: String?,
    names: List<String>,
    enabled: Boolean,
    onSelect: (String) -> Unit,
    optional: Boolean = false
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        TextButton(onClick = { expanded = true }, enabled = enabled) {
            Text("$label: ${selected ?: if (optional) "No context" else "Select field"}")
        }
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            if (optional) {
                DropdownMenuItem(text = { Text("No context") }, onClick = {
                    onSelect("")
                    expanded = false
                })
            }
            names.forEach { name ->
                DropdownMenuItem(text = { Text(name) }, onClick = {
                    onSelect(name)
                    expanded = false
                })
            }
        }
    }
}
