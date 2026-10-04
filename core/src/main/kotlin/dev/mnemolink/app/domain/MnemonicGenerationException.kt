package dev.mnemolink.app.domain

/** Only fixed, application-owned failure messages may cross the service/UI boundary. */
enum class MnemonicFailure(val userMessage: String) {
    Configuration("Check the generation service configuration."),
    Unavailable("Generation service unavailable. Check that Ollama is running locally."),
    Timeout("Generation timed out. The first model load can be slow; retry explicitly."),
    ModelUnavailable(
        "Model unavailable. Choose an installed local model; no model is downloaded automatically."
    ),
    Rejected("The generation request was rejected. Check the service configuration."),
    InvalidResponse(
        "The model returned an invalid or incomplete mnemonic. Retry explicitly or edit the draft."
    )
}

open class MnemonicGenerationException(val failure: MnemonicFailure) :
    Exception(failure.userMessage)
