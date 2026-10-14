package dev.mnemolink.app.domain

/** Canonical Japanese vocabulary data supplied by the learner or a trusted note mapping. */
data class JapaneseWordDetails(
    val kana: String,
    val meaning: String,
    val meaningLanguage: MeaningLanguage
)

enum class MeaningLanguage {
    French,
    English
}
