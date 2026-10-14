package dev.mnemolink.app.data.demo

import dev.mnemolink.app.domain.GenerationExamples
import dev.mnemolink.app.domain.GenerationInput
import dev.mnemolink.app.domain.GenerationValidation
import dev.mnemolink.app.domain.MnemonicService

/** Deterministic mock association, not an explanation or a factual claim about the concept. */
class DemoMnemonicService : MnemonicService {
    override suspend fun generate(input: GenerationInput): String {
        require(GenerationValidation.inputError(input) == null) {
            "Demo generation requires valid plain-text input."
        }
        val example = GenerationExamples.japaneseCharacter
        if (input.concept == example.concept && input.context == example.context) {
            return "Démo mnémotechnique (image fictive) : Pour la forme, reconnais 木 comme " +
                "la clé déjà connue « arbre », sans découpage inventé. " +
                "Pour le son, imagine cet arbre demandant « QUI m'a planté ? » : " +
                "le mot français « qui » sert d'indice approximatif pour la lecture fournie き. " +
                "Cette association est fictive, pas une étymologie et pas une règle de prononciation."
        }
        return "Mock mnemonic (imaginary story): Picture a bright suitcase labeled " +
            "\"${input.concept.trim()}\". A tiny guide carries it through a familiar room. " +
            "Use this invented scene as a cue to recall your own source context; " +
            "it does not explain or verify the concept."
    }
}
