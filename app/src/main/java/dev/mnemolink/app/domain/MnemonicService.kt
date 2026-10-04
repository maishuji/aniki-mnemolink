package dev.mnemolink.app.domain

/** Generates a plain-text suggestion only. Implementations must not save or update notes. */
fun interface MnemonicService {
    suspend fun generate(input: GenerationInput): String
}
