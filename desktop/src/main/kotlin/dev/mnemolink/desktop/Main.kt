package dev.mnemolink.desktop

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.mnemolink.app.domain.GenerationInput
import dev.mnemolink.app.domain.GenerationStatus
import dev.mnemolink.desktop.data.anki.AnkiConnectRepository
import dev.mnemolink.desktop.feature.anki.AnkiBrowserController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withTimeout

fun main(args: Array<String>) {
    val options = DesktopLaunchOptions.parse(args)
    if (options.smokeTest) {
        // The bounded startup check should also work on CI without a GPU.
        System.setProperty("skiko.renderApi", "SOFTWARE")
    }
    var smokeFailure: Throwable? = null
    var smokeCompleted = false
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "MnemoLink for Anki — Linux",
            state = rememberWindowState(width = 620.dp, height = 860.dp)
        ) {
            val scope = rememberCoroutineScope { Dispatchers.Swing }
            val generation = remember {
                DesktopGenerationController(
                    scope,
                    if (options.smokeTest) GenerationBackend.Demo else GenerationBackend.Qwen3
                )
            }
            val workflow = generation.workflow
            val backend by generation.backend.collectAsState()
            val browser = remember {
                AnkiBrowserController({ key -> AnkiConnectRepository(apiKey = key) }, scope)
            }
            DisposableEffect(generation, browser) {
                onDispose {
                    browser.close()
                    generation.close()
                }
            }
            val state by workflow.state.collectAsState()
            val browserState by browser.state.collectAsState()
            var pendingInput by remember { mutableStateOf<GenerationInput?>(null) }
            var pendingBackend by remember { mutableStateOf<GenerationBackend?>(null) }
            var pendingGeneration by remember { mutableStateOf<GenerationRequest?>(null) }
            var darkTheme by remember { mutableStateOf(false) }
            MaterialTheme(colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()) {
                DesktopGenerationScreen(
                    state = state,
                    onConceptChange = workflow::updateConcept,
                    onContextChange = workflow::updateContext,
                    onGenerate = {
                        if (backend == GenerationBackend.Demo) {
                            workflow.generate()
                        } else {
                            pendingGeneration = generation.prepareRequest()
                        }
                    },
                    onDraftChange = workflow::updateDraft,
                    onApprove = workflow::approve,
                    onCancel = workflow::cancel,
                    darkTheme = darkTheme,
                    onDarkThemeChange = { darkTheme = it },
                    backend = backend,
                    generationSettings = {
                        GenerationSettingsPanel(backend) { selected ->
                            if (state.draft.isNotEmpty() ||
                                state.approval != null ||
                                state.status == GenerationStatus.Generating
                            ) {
                                pendingBackend = selected
                            } else {
                                generation.selectBackend(selected)
                            }
                        }
                    },
                    noteBrowser = {
                        AnkiBrowserPanel(browserState, browser) {
                            browser.mappedInput()?.let { input ->
                                if (state.draft.isNotEmpty() ||
                                    state.approval != null ||
                                    state.status == GenerationStatus.Generating
                                ) {
                                    pendingInput = input
                                } else {
                                    workflow.loadNoteInput(input)
                                }
                            }
                        }
                    }
                )
                pendingGeneration?.let { request ->
                    val input = request.input
                    AlertDialog(
                        onDismissRequest = { pendingGeneration = null },
                        title = { Text("Generate with ${request.backend.model}?") },
                        text = {
                            Text(
                                "Send these fields to your local Ollama server only:\n\n" +
                                    "Concept: ${input.concept}\nContext: ${input.context}\n\n" +
                                    "No note IDs, tags or destination content are sent. " +
                                    "Use a trusted local model, not a cloud-backed alias. " +
                                    "The suggestion may be wrong; approval will not save to Anki.",
                                modifier = Modifier.heightIn(max = 360.dp)
                                    .verticalScroll(rememberScrollState())
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                generation.generateConfirmed(request)
                                pendingGeneration = null
                            }) { Text("Generate locally") }
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingGeneration = null }) { Text("Cancel") }
                        }
                    )
                }
                pendingBackend?.let { selected ->
                    AlertDialog(
                        onDismissRequest = { pendingBackend = null },
                        title = { Text("Change generation service?") },
                        text = {
                            Text("This discards your draft and approval, and cancels generation.")
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                generation.selectBackend(selected)
                                pendingBackend = null
                            }) { Text("Discard and change") }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                pendingBackend = null
                            }) { Text("Keep current service") }
                        }
                    )
                }
                pendingInput?.let { input ->
                    AlertDialog(
                        onDismissRequest = { pendingInput = null },
                        title = { Text("Replace current input?") },
                        text = {
                            Text(
                                "This discards the draft and local approval, and cancels generation."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                workflow.loadNoteInput(input)
                                pendingInput = null
                            }) { Text("Discard and load") }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                pendingInput = null
                            }) { Text("Keep current draft") }
                        }
                    )
                }
            }
            if (options.smokeTest) {
                LaunchedEffect(workflow) {
                    try {
                        withTimeout(5_000) {
                            workflow.generate()
                            workflow.state.first { it.status == GenerationStatus.Editing }
                            val smokeDraft = "Synthetic desktop smoke-test draft"
                            workflow.updateDraft(smokeDraft)
                            workflow.approve()
                            check(workflow.state.value.approval?.text == smokeDraft)
                            workflow.cancel()
                            check(workflow.state.value.draft.isEmpty())
                            check(workflow.state.value.approval == null)
                        }
                        smokeCompleted = true
                        println(
                            "Desktop smoke test passed: application composition and local workflow."
                        )
                    } catch (failure: Throwable) {
                        smokeFailure = failure
                    } finally {
                        exitApplication()
                    }
                }
            }
        }
    }
    if (options.smokeTest && !smokeCompleted) {
        throw IllegalStateException(
            "Desktop smoke test failed or closed before completion",
            smokeFailure
        )
    }
}
