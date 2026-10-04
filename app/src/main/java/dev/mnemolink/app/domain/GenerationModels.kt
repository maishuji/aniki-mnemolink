package dev.mnemolink.app.domain

enum class EntryMode {
    Standalone,
    ReturnToHost
}

data class GenerationInput(
    val concept: String,
    val context: String = "",
    val entryMode: EntryMode = EntryMode.Standalone
)

/** A local approval only: no note identity, persistence, or host write is implied. */
data class ApprovedProposal(val input: GenerationInput, val text: String)

enum class GenerationStatus {
    Ready,
    Generating,
    Editing,
    Approved,
    Error
}

data class GenerationUiState(
    val input: GenerationInput,
    val status: GenerationStatus = GenerationStatus.Ready,
    val draft: String = "",
    val approval: ApprovedProposal? = null,
    val errorMessage: String? = null
) {
    val inputError: String?
        get() = GenerationValidation.inputError(input)

    val draftError: String?
        get() = GenerationValidation.draftError(draft)

    val canGenerate: Boolean
        get() = status != GenerationStatus.Generating && inputError == null

    val canApprove: Boolean
        get() = status != GenerationStatus.Generating &&
            approval == null &&
            inputError == null &&
            draftError == null
}
