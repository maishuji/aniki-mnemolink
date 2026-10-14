package dev.mnemolink.app

import dev.mnemolink.app.data.demo.DemoMnemonicService
import dev.mnemolink.app.domain.ApprovedProposal
import dev.mnemolink.app.domain.EntryMode
import dev.mnemolink.app.domain.GenerationExamples
import dev.mnemolink.app.domain.GenerationInput
import dev.mnemolink.app.domain.GenerationValidation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationDomainTest {
    @Test
    fun validationAcceptsExactLengthLimitsAndEmptyContext() {
        assertNull(
            GenerationValidation.inputError(
                GenerationInput(
                    concept = "a".repeat(GenerationValidation.MAX_CONCEPT_LENGTH),
                    context = "b".repeat(GenerationValidation.MAX_CONTEXT_LENGTH)
                )
            )
        )
        assertNull(GenerationValidation.inputError(GenerationInput("Concept")))
        assertNull(
            GenerationValidation.draftError("a".repeat(GenerationValidation.MAX_DRAFT_LENGTH))
        )
    }

    @Test
    fun rejectsMarkupVariantsAndFieldSeparatorsInAllTextFields() {
        val unsafeTexts = listOf(
            "left\u001Fright",
            "[SOUND:clip.mp3]",
            "[sound :clip.mp3]",
            "{{c12::hidden::hint}}",
            "{{Front}}",
            "<IMG SRC=x>",
            "<script>bad</script>"
        )
        for (text in unsafeTexts) {
            assertNotNull(GenerationValidation.inputError(GenerationInput(text)))
            assertNotNull(GenerationValidation.inputError(GenerationInput("Concept", text)))
            assertNotNull(GenerationValidation.draftError(text))
        }
    }

    @Test
    fun returnToHostIsExplicitAndApprovalHasOnlyInputAndText() {
        val input = GenerationInput("Concept", entryMode = EntryMode.ReturnToHost)
        val approval = ApprovedProposal(input, "A story")
        assertEquals(EntryMode.ReturnToHost, approval.input.entryMode)
        assertEquals(input, approval.input)
        assertEquals("A story", approval.text)
    }

    @Test
    fun demoIsDeterministicPlainTextAndExplicitlyImaginary() = runTest {
        val service = DemoMnemonicService()
        val input = GenerationInput("ubiquitous", "Synthetic front/back context")
        val first = service.generate(input)
        assertEquals(first, service.generate(input))
        assertTrue(first.startsWith("Mock mnemonic (imaginary story):"))
        assertTrue(first.contains("ubiquitous"))
        assertTrue(first.contains("does not explain or verify"))
        assertNull(GenerationValidation.draftError(first))
        assertEquals(
            first,
            service.generate(
                input.copy(context = "Different context", entryMode = EntryMode.ReturnToHost)
            )
        )
    }

    @Test
    fun editedJapaneseInputDoesNotReceiveThePrewrittenTreeMnemonic() = runTest {
        val service = DemoMnemonicService()
        val example = GenerationExamples.japaneseCharacter
        val editedInputs = listOf(
            example.copy(context = "A different supplied sense or objective"),
            example.copy(concept = "水")
        )
        for (input in editedInputs) {
            val draft = service.generate(input)
            assertTrue(draft.startsWith("Mock mnemonic (imaginary story):"))
            assertTrue(draft.contains("does not explain or verify"))
        }
    }

    @Test
    fun japaneseDemoIsDeterministicAndIndependentOfEntryMode() = runTest {
        val service = DemoMnemonicService()
        val input = GenerationExamples.japaneseCharacter
        val draft = service.generate(input)
        assertTrue(draft.startsWith("Démo mnémotechnique (image fictive) :"))
        assertEquals(draft, service.generate(input))
        assertEquals(draft, service.generate(input.copy(entryMode = EntryMode.ReturnToHost)))
    }

    @Test
    fun japaneseModeRequiresSuppliedKanaAndMeaning() {
        val missingDetails = GenerationInput("魚", mode = LearningInputMode.JapaneseVocabulary)
        assertEquals(
            "Enter the Japanese reading and meaning.",
            GenerationValidation.inputError(missingDetails)
        )
        val missingMeaning = GenerationInput(
            "魚",
            mode = LearningInputMode.JapaneseVocabulary,
            japanese = JapaneseWordDetails("さかな", "", MeaningLanguage.French)
        )
        assertEquals("Enter the intended meaning.", GenerationValidation.inputError(missingMeaning))
        assertNull(GenerationValidation.inputError(GenerationExamples.japaneseCharacter))
    }

    @Test
    fun japaneseDetailsCannotBeHiddenInGeneralMode() {
        val input = GenerationInput(
            "魚",
            mode = LearningInputMode.General,
            japanese = JapaneseWordDetails("さかな", "poisson", MeaningLanguage.French)
        )
        assertEquals(
            "Japanese vocabulary details require Japanese vocabulary mode.",
            GenerationValidation.inputError(input)
        )
    }

    @Test
    fun japaneseFieldsRejectUnsafeMarkupAndRespectLimits() {
        val base = GenerationExamples.japaneseCharacter
        assertNotNull(
            GenerationValidation.inputError(
                base.copy(japanese = base.japanese!!.copy(kana = "<b>き</b>"))
            )
        )
        assertNotNull(
            GenerationValidation.inputError(
                base.copy(
                    japanese = base.japanese!!.copy(
                        meaning = "a".repeat(GenerationValidation.MAX_JAPANESE_MEANING_LENGTH + 1)
                    )
                )
            )
        )
    }

    @Test
    fun demoRejectsInvalidInput() = runTest {
        val service = DemoMnemonicService()
        var rejected = false
        try {
            service.generate(GenerationInput("<script>bad</script>"))
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }
}
