package dev.mnemolink.app.domain

/** Synthetic standalone examples, never loaded from a user's Anki collection. */
object GenerationExamples {
    val generic = GenerationInput(
        concept = "ubiquitous",
        context = "Synthetic demo example\nFront: ubiquitous\nBack: present everywhere"
    )

    val japaneseCharacter = GenerationInput(
        concept = "木",
        mode = LearningInputMode.JapaneseVocabulary,
        japanese = JapaneseWordDetails(
            kana = "き",
            meaning = "arbre",
            meaningLanguage = MeaningLanguage.French
        ),
        context = """
            Synthetic demo example (no Anki note)
            Langue : japonais
            Caractère : 木
            Lecture fournie : き
            Sens : arbre
            Contexte : mot isolé désignant un arbre ; cette lecture ne couvre pas tous les usages du kanji.
            Profil : francophone connaissant les clés/composants usuels et les hiragana.
            Composant déjà connu : 木 = arbre (clé entière, sans emboîtement dans cet exemple).
            Objectif : reconnaître le sens et retrouver la lecture fournie き à partir de l'arbre.
            Explication et association en français ; indice sonore approximatif, pas une étymologie.
            La forme connue n'est pas une règle permettant de déduire la lecture.
        """.trimIndent()
    )
}
