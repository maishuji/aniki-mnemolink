package dev.mnemolink.desktop

import dev.mnemolink.app.data.demo.DemoMnemonicService
import dev.mnemolink.app.domain.GenerationInput
import dev.mnemolink.app.domain.MnemonicService
import dev.mnemolink.app.workflow.GenerationWorkflow
import dev.mnemolink.desktop.data.ollama.OllamaMnemonicService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class GenerationBackend(val label: String, val model: String?) {
    Qwen3("Local Ollama — Qwen3 14B", "qwen3:14b"),
    Qwen25("Local Ollama — Qwen2.5 14B", "qwen2.5:14b"),
    Demo("Offline mock demo", null)
}

internal data class GenerationRequest(val input: GenerationInput, val backend: GenerationBackend)

/** UI-thread confined; configuration changes invalidate drafts before switching services. */
internal class DesktopGenerationController(
    scope: CoroutineScope,
    initialBackend: GenerationBackend = GenerationBackend.Qwen3,
    private val factory: (GenerationBackend) -> MnemonicService = { backend ->
        backend.model?.let { OllamaMnemonicService(it) } ?: DemoMnemonicService()
    }
) : AutoCloseable {
    private val mutableBackend = MutableStateFlow(initialBackend)
    val backend: StateFlow<GenerationBackend> = mutableBackend.asStateFlow()
    private var service = factory(initialBackend)
    private var closed = false
    val workflow = GenerationWorkflow(MnemonicService { input -> service.generate(input) }, scope)

    fun prepareRequest(): GenerationRequest? = if (!closed && workflow.state.value.canGenerate) {
        GenerationRequest(workflow.state.value.input, backend.value)
    } else {
        null
    }

    fun generateConfirmed(request: GenerationRequest) {
        if (!closed &&
            request.backend == backend.value &&
            request.input == workflow.state.value.input &&
            workflow.state.value.canGenerate
        ) {
            workflow.generate()
        }
    }

    fun selectBackend(backend: GenerationBackend) {
        if (closed || backend == this.backend.value) return
        val replacement = factory(backend)
        workflow.cancel()
        (service as? AutoCloseable)?.close()
        service = replacement
        mutableBackend.value = backend
    }

    override fun close() {
        if (closed) return
        closed = true
        workflow.close()
        (service as? AutoCloseable)?.close()
    }
}
