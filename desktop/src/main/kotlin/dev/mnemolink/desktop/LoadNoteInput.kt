package dev.mnemolink.desktop

import dev.mnemolink.app.domain.GenerationInput
import dev.mnemolink.app.workflow.GenerationWorkflow

/** A new note selection must invalidate approval even if its plaintext is identical. */
internal fun GenerationWorkflow.loadNoteInput(input: GenerationInput) {
    cancel()
    updateConcept(input.concept)
    updateContext(input.context)
}
