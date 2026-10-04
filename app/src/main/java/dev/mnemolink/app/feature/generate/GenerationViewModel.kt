package dev.mnemolink.app.feature.generate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mnemolink.app.domain.ApprovedProposal
import dev.mnemolink.app.domain.GenerationInput
import dev.mnemolink.app.domain.GenerationStatus
import dev.mnemolink.app.domain.GenerationUiState
import dev.mnemolink.app.domain.GenerationValidation
import dev.mnemolink.app.domain.MnemonicService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Main-thread workflow. State is in memory only and is not restored after process death. */
class GenerationViewModel(private val service: MnemonicService) : ViewModel() {
    private val mutableState = MutableStateFlow(
        GenerationUiState(
            input = GenerationInput(
                concept = "ubiquitous",
                context = "Synthetic demo example\nFront: ubiquitous\nBack: present everywhere"
            )
        )
    )
    val state: StateFlow<GenerationUiState> = mutableState.asStateFlow()

    private var generationJob: Job? = null
    private var requestVersion = 0L

    fun updateConcept(concept: String) {
        if (concept != state.value.input.concept) {
            updateInput(state.value.input.copy(concept = concept))
        }
    }

    fun updateContext(context: String) {
        if (context != state.value.input.context) {
            updateInput(state.value.input.copy(context = context))
        }
    }

    private fun updateInput(input: GenerationInput) {
        invalidateRequest()
        mutableState.value = GenerationUiState(input = input)
    }

    fun generate() {
        val current = state.value
        if (current.status == GenerationStatus.Generating) return
        val inputError = current.inputError
        if (inputError != null) {
            mutableState.value = current.copy(
                status = GenerationStatus.Error,
                errorMessage = inputError
            )
            return
        }

        invalidateRequest()
        val version = requestVersion
        mutableState.value = current.copy(
            status = GenerationStatus.Generating,
            approval = null,
            errorMessage = null
        )
        generationJob = viewModelScope.launch {
            try {
                val suggestion = service.generate(current.input)
                if (version != requestVersion) return@launch
                val draftError = GenerationValidation.draftError(suggestion)
                if (draftError != null) {
                    mutableState.value = state.value.copy(
                        status = GenerationStatus.Error,
                        errorMessage = draftError
                    )
                } else {
                    mutableState.value = state.value.copy(
                        status = GenerationStatus.Editing,
                        draft = suggestion,
                        errorMessage = null
                    )
                }
            } catch (cancelled: CancellationException) {
                if (version == requestVersion) {
                    mutableState.value = state.value.copy(
                        status = editableStatus(),
                        errorMessage = null
                    )
                }
                throw cancelled
            } catch (_: Exception) {
                if (version == requestVersion) {
                    // Do not surface untrusted service exception text as UI content.
                    mutableState.value = state.value.copy(
                        status = GenerationStatus.Error,
                        errorMessage = "Could not generate a mnemonic. Please try again."
                    )
                }
            }
        }
    }

    fun updateDraft(text: String) {
        invalidateRequest()
        mutableState.value = state.value.copy(
            status = GenerationStatus.Editing,
            draft = text,
            approval = null,
            errorMessage = null
        )
    }

    fun approve() {
        val current = state.value
        if (!current.canApprove) return
        mutableState.value = current.copy(
            status = GenerationStatus.Approved,
            approval = ApprovedProposal(input = current.input, text = current.draft),
            errorMessage = null
        )
    }

    fun cancel() {
        invalidateRequest()
        mutableState.value = GenerationUiState(input = state.value.input)
    }

    private fun invalidateRequest() {
        // Increment before cancellation, including for services that ignore cancellation.
        requestVersion++
        generationJob?.cancel()
        generationJob = null
    }

    private fun editableStatus(): GenerationStatus =
        if (state.value.draft.isEmpty()) GenerationStatus.Ready else GenerationStatus.Editing

    override fun onCleared() {
        invalidateRequest()
        super.onCleared()
    }
}
