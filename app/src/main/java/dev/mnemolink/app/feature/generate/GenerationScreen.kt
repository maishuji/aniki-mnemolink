package dev.mnemolink.app.feature.generate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.mnemolink.app.R
import dev.mnemolink.app.domain.GenerationStatus
import dev.mnemolink.app.domain.GenerationUiState
import dev.mnemolink.app.domain.GenerationValidation

@Composable
fun GenerationScreen(
    state: GenerationUiState,
    onConceptChange: (String) -> Unit,
    onContextChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onDraftChange: (String) -> Unit,
    onApprove: () -> Unit,
    onCancel: () -> Unit
) {
    Scaffold { contentPadding ->
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.screen_title),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.semantics { heading() }
            )
            Text(stringResource(R.string.screen_subtitle))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.demo_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(stringResource(R.string.demo_notice))
                }
            }
            Text(
                stringResource(R.string.input_heading),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
            OutlinedTextField(
                value = state.input.concept,
                onValueChange = onConceptChange,
                label = { Text(stringResource(R.string.concept_label)) },
                supportingText = {
                    Text(
                        stringResource(
                            R.string.text_limit,
                            state.input.concept.length,
                            GenerationValidation.MAX_CONCEPT_LENGTH
                        )
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("concept")
            )
            OutlinedTextField(
                value = state.input.context,
                onValueChange = onContextChange,
                label = { Text(stringResource(R.string.context_label)) },
                supportingText = {
                    Text(
                        stringResource(
                            R.string.text_limit,
                            state.input.context.length,
                            GenerationValidation.MAX_CONTEXT_LENGTH
                        )
                    )
                },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth().testTag("context")
            )
            Text(
                stringResource(R.string.input_edit_notice),
                style = MaterialTheme.typography.bodySmall
            )
            state.inputError?.let { StatusMessage(it, "input-error", isError = true) }
            Button(
                onClick = onGenerate,
                enabled = state.canGenerate,
                modifier = Modifier.fillMaxWidth().testTag("generate")
            ) {
                Text(
                    stringResource(
                        if (state.draft.isBlank()) {
                            R.string.generate_demo
                        } else {
                            R.string.regenerate_demo
                        }
                    )
                )
            }
            if (state.status == GenerationStatus.Generating) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.generating))
                }
            }
            Text(
                stringResource(R.string.draft_heading),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() }
            )
            OutlinedTextField(
                value = state.draft,
                onValueChange = onDraftChange,
                label = { Text(stringResource(R.string.mnemonic_label)) },
                supportingText = {
                    Text(
                        stringResource(
                            R.string.text_limit,
                            state.draft.length,
                            GenerationValidation.MAX_DRAFT_LENGTH
                        )
                    )
                },
                isError = state.draft.isNotEmpty() && state.draftError != null,
                enabled = state.status != GenerationStatus.Generating,
                minLines = 3,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth().testTag("draft")
            )
            if (state.draft.isNotEmpty()) {
                state.draftError?.let { StatusMessage(it, "draft-error", isError = true) }
            }
            state.errorMessage?.let { StatusMessage(it, "generation-error", isError = true) }
            if (state.status == GenerationStatus.Approved) {
                StatusMessage(stringResource(R.string.local_approval_notice), "approved")
            }
            Button(
                onClick = onApprove,
                enabled = state.canApprove,
                modifier = Modifier.fillMaxWidth().testTag("approve")
            ) {
                Text(stringResource(R.string.approve_demo))
            }
            TextButton(
                onClick = onCancel,
                enabled =
                state.draft.isNotEmpty() ||
                    state.status == GenerationStatus.Generating ||
                    state.errorMessage != null,
                modifier = Modifier.fillMaxWidth().testTag("cancel")
            ) {
                Text(
                    stringResource(
                        if (state.status ==
                            GenerationStatus.Generating
                        ) {
                            R.string.cancel_generation
                        } else {
                            R.string.discard_draft
                        }
                    )
                )
            }
            Text(
                stringResource(R.string.persistence_notice),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun StatusMessage(text: String, tag: String, isError: Boolean = false) {
    Text(
        text = text,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        modifier = Modifier.testTag(tag).semantics { liveRegion = LiveRegionMode.Polite }
    )
}
