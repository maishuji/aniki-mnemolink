package dev.mnemolink.app

import dev.mnemolink.app.data.demo.DemoMnemonicService
import dev.mnemolink.app.domain.ApprovedProposal
import dev.mnemolink.app.domain.EntryMode
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
