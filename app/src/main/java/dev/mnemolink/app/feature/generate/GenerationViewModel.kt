package dev.mnemolink.app.feature.generate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.mnemolink.app.domain.GenerationUiState
import dev.mnemolink.app.domain.MnemonicService
import dev.mnemolink.app.workflow.GenerationWorkflow
import kotlinx.coroutines.flow.StateFlow

/** Main-thread adapter. State is in memory only and is not restored after process death. */
class GenerationViewModel(service: MnemonicService) : ViewModel() {
    private val workflow = GenerationWorkflow(service, viewModelScope)
    val state: StateFlow<GenerationUiState> = workflow.state

    fun updateConcept(concept: String) = workflow.updateConcept(concept)

    fun updateContext(context: String) = workflow.updateContext(context)

    fun generate() = workflow.generate()

    fun updateDraft(text: String) = workflow.updateDraft(text)

    fun approve() = workflow.approve()

    fun cancel() = workflow.cancel()

    override fun onCleared() {
        workflow.close()
        super.onCleared()
    }
}
