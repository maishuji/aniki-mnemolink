package dev.mnemolink.app.domain

/** Limits count Kotlin String UTF-16 code units. Text is plain text, never HTML. */
object GenerationValidation {
    const val MAX_CONCEPT_LENGTH = 200
    const val MAX_CONTEXT_LENGTH = 2_000
    const val MAX_DRAFT_LENGTH = 4_000
    const val MAX_JAPANESE_KANA_LENGTH = 200
    const val MAX_JAPANESE_MEANING_LENGTH = 500

    private val soundMarkup = Regex("\\[sound\\s*:", RegexOption.IGNORE_CASE)

    fun inputError(input: GenerationInput): String? {
        val basicError = when {
            input.concept.isBlank() -> "Enter a concept."
            input.concept.length > MAX_CONCEPT_LENGTH -> "Concept is too long (maximum 200)."
            input.context.length > MAX_CONTEXT_LENGTH -> "Context is too long (maximum 2000)."
            else -> unsafeTextError(input.concept) ?: unsafeTextError(input.context)
        }
        if (basicError != null) return basicError
        if (input.mode == LearningInputMode.General && input.japanese != null) {
            return "Japanese vocabulary details require Japanese vocabulary mode."
        }
        if (input.mode == LearningInputMode.JapaneseVocabulary) {
            val japanese = input.japanese ?: return "Enter the Japanese reading and meaning."
            when {
                japanese.kana.isBlank() -> return "Enter the supplied Japanese kana reading."
                japanese.kana.length > MAX_JAPANESE_KANA_LENGTH ->
                    return "Japanese reading is too long (maximum 200)."
                japanese.meaning.isBlank() -> return "Enter the intended meaning."
                japanese.meaning.length > MAX_JAPANESE_MEANING_LENGTH ->
                    return "Japanese meaning is too long (maximum 500)."
                unsafeTextError(japanese.kana) != null -> return unsafeTextError(japanese.kana)
                unsafeTextError(
                    japanese.meaning
                ) != null -> return unsafeTextError(japanese.meaning)
            }
        }
        return null
    }

    fun draftError(text: String): String? = when {
        text.isBlank() -> "Enter a nonblank mnemonic."
        text.length > MAX_DRAFT_LENGTH -> "Mnemonic is too long (maximum 4000)."
        else -> unsafeTextError(text)
    }

    private fun unsafeTextError(text: String): String? = when {
        '\u001F' in text -> "Anki field separators are not allowed."
        soundMarkup.containsMatchIn(text) -> "Anki sound markup is not allowed."
        "{{" in text || "}}" in text -> "Cloze or template markup is not allowed."
        '<' in text || '>' in text -> "HTML markup is not allowed; use plain text."
        else -> null
    }
}
