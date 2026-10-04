package dev.mnemolink.app.data.demo

import dev.mnemolink.app.domain.GenerationInput
import dev.mnemolink.app.domain.GenerationValidation
import dev.mnemolink.app.domain.MnemonicService

/** Deterministic mock association, not an explanation or a factual claim about the concept. */
class DemoMnemonicService : MnemonicService {
    override suspend fun generate(input: GenerationInput): String {
        require(GenerationValidation.inputError(input) == null) {
            "Demo generation requires valid plain-text input."
        }
        return "Mock mnemonic (imaginary story): Picture a bright suitcase labeled " +
            "\"${input.concept.trim()}\". A tiny guide carries it through a familiar room. " +
            "Use this invented scene as a cue to recall your own source context; " +
            "it does not explain or verify the concept."
    }
}
