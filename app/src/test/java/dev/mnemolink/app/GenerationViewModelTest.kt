package dev.mnemolink.app

import dev.mnemolink.app.domain.EntryMode
import dev.mnemolink.app.domain.GenerationStatus
import dev.mnemolink.app.domain.GenerationValidation
import dev.mnemolink.app.domain.MnemonicService
import dev.mnemolink.app.feature.generate.GenerationViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GenerationViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun initialStateIsSyntheticStandaloneAndReady() {
        val viewModel = GenerationViewModel(MnemonicService { "A story" })

        assertEquals(GenerationStatus.Ready, viewModel.state.value.status)
        assertEquals("ubiquitous", viewModel.state.value.input.concept)
        assertTrue(viewModel.state.value.input.context.contains("Synthetic demo"))
        assertEquals(EntryMode.Standalone, viewModel.state.value.input.entryMode)
        assertTrue(viewModel.state.value.canGenerate)
        assertFalse(viewModel.state.value.canApprove)
    }

    @Test
    fun successProducesEditableDraftAndApprovalCapturesExactInputAndText() = runTest {
        val viewModel = GenerationViewModel(
            MnemonicService { input ->
                assertEquals("ubiquitous", input.concept)
                "An imaginary story"
            }
        )

        viewModel.generate()
        assertEquals(GenerationStatus.Generating, viewModel.state.value.status)
        assertFalse(viewModel.state.value.canGenerate)
        assertFalse(viewModel.state.value.canApprove)
        advanceUntilIdle()

        assertEquals(GenerationStatus.Editing, viewModel.state.value.status)
        assertEquals("An imaginary story", viewModel.state.value.draft)
        assertNull(viewModel.state.value.approval)
        assertTrue(viewModel.state.value.canApprove)
        viewModel.approve()
        assertEquals(GenerationStatus.Approved, viewModel.state.value.status)
        assertEquals(viewModel.state.value.input, viewModel.state.value.approval?.input)
        assertEquals("An imaginary story", viewModel.state.value.approval?.text)
        assertFalse(viewModel.state.value.canApprove)
    }

    @Test
    fun blankAndInvalidDraftsCannotBeApproved() {
        val viewModel = GenerationViewModel(MnemonicService { "Unused" })
        val invalidDrafts = listOf(
            "",
            " \n\t",
            "a\u001Fb",
            "[sound:clip.mp3]",
            "{{c1::hidden}}",
            "<script>alert(1)</script>",
            "x".repeat(GenerationValidation.MAX_DRAFT_LENGTH + 1)
        )

        for (draft in invalidDrafts) {
            viewModel.updateDraft(draft)
            assertFalse(viewModel.state.value.canApprove)
            viewModel.approve()
            assertNull(viewModel.state.value.approval)
            assertEquals(GenerationStatus.Editing, viewModel.state.value.status)
        }
    }

    @Test
    fun draftEditRevokesApprovalEvenWhenTextIsUnchanged() {
        val viewModel = GenerationViewModel(MnemonicService { "Unused" })
        viewModel.updateDraft("A story")
        viewModel.approve()
        viewModel.updateDraft("A story")

        assertEquals(GenerationStatus.Editing, viewModel.state.value.status)
        assertNull(viewModel.state.value.approval)
        assertTrue(viewModel.state.value.canApprove)
    }

    @Test
    fun conceptAndContextEditsClearDraftAndApproval() {
        val viewModel = GenerationViewModel(MnemonicService { "Unused" })
        viewModel.updateDraft("A story")
        viewModel.approve()
        viewModel.updateConcept("new concept")
        assertEquals(GenerationStatus.Ready, viewModel.state.value.status)
        assertEquals("", viewModel.state.value.draft)
        assertNull(viewModel.state.value.approval)

        viewModel.updateDraft("Another story")
        viewModel.approve()
        viewModel.updateContext("New source")
        assertEquals(GenerationStatus.Ready, viewModel.state.value.status)
        assertEquals("", viewModel.state.value.draft)
        assertNull(viewModel.state.value.approval)
    }

    @Test
    fun unchangedSourceDoesNotDiscardDraft() {
        val viewModel = GenerationViewModel(MnemonicService { "Unused" })
        viewModel.updateDraft("A story")
        viewModel.updateConcept(viewModel.state.value.input.concept)
        viewModel.updateContext(viewModel.state.value.input.context)
        assertEquals("A story", viewModel.state.value.draft)
    }

    @Test
    fun doubleTapDuringLoadingMakesOnlyOneRequest() = runTest {
        var calls = 0
        val result = CompletableDeferred<String>()
        val viewModel = GenerationViewModel(
            MnemonicService {
                calls++
                result.await()
            }
        )

        viewModel.generate()
        viewModel.generate()
        runCurrent()
        viewModel.generate()
        assertEquals(1, calls)
        result.complete("One story")
        advanceUntilIdle()
        assertEquals("One story", viewModel.state.value.draft)
    }

    @Test
    fun cancelStopsGenerationAndNeverApprovesOrStartsAnotherRequest() = runTest {
        var calls = 0
        var cancelled = false
        val viewModel = GenerationViewModel(
            MnemonicService {
                calls++
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }
        )
        val input = viewModel.state.value.input
        viewModel.updateDraft("Previous draft")
        viewModel.generate()
        runCurrent()
        viewModel.approve()
        assertNull(viewModel.state.value.approval)
        viewModel.cancel()
        advanceUntilIdle()

        assertTrue(cancelled)
        assertEquals(1, calls)
        assertEquals(input, viewModel.state.value.input)
        assertEquals(GenerationStatus.Ready, viewModel.state.value.status)
        assertEquals("", viewModel.state.value.draft)
        assertNull(viewModel.state.value.approval)
        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun cancelClearsLocalApproval() {
        val viewModel = GenerationViewModel(MnemonicService { "Unused" })
        viewModel.updateDraft("A story")
        viewModel.approve()
        viewModel.cancel()
        assertEquals(GenerationStatus.Ready, viewModel.state.value.status)
        assertNull(viewModel.state.value.approval)
        assertEquals("", viewModel.state.value.draft)
    }

    @Test
    fun noncooperativeLateResponseCannotOverwriteNewerResult() = runTest {
        var calls = 0
        val oldResult = CompletableDeferred<String>()
        val viewModel = GenerationViewModel(
            MnemonicService {
                calls++
                if (calls == 1) {
                    withContext(NonCancellable) { oldResult.await() }
                } else {
                    "New result"
                }
            }
        )

        viewModel.generate()
        runCurrent()
        viewModel.cancel()
        viewModel.generate()
        runCurrent()
        assertEquals("New result", viewModel.state.value.draft)
        viewModel.approve()
        val approvedState = viewModel.state.value
        oldResult.complete("Stale result")
        advanceUntilIdle()
        assertEquals(approvedState, viewModel.state.value)
        assertEquals(2, calls)
    }

    @Test
    fun noncooperativeLateResponseAfterCancelCannotRestoreDraft() = runTest {
        val result = CompletableDeferred<String>()
        val viewModel = GenerationViewModel(
            MnemonicService {
                withContext(NonCancellable) { result.await() }
            }
        )
        viewModel.generate()
        runCurrent()
        viewModel.cancel()
        result.complete("Late story")
        advanceUntilIdle()
        assertEquals(GenerationStatus.Ready, viewModel.state.value.status)
        assertEquals("", viewModel.state.value.draft)
        assertNull(viewModel.state.value.approval)
    }

    @Test
    fun generationFailurePreservesEditableDraftButRevokesApproval() = runTest {
        val viewModel = GenerationViewModel(
            MnemonicService {
                throw IllegalStateException("<untrusted service details>")
            }
        )
        viewModel.updateDraft("Keep this draft")
        viewModel.approve()
        viewModel.generate()
        assertNull(viewModel.state.value.approval)
        advanceUntilIdle()

        assertEquals(GenerationStatus.Error, viewModel.state.value.status)
        assertEquals("Keep this draft", viewModel.state.value.draft)
        assertTrue(viewModel.state.value.canApprove)
        assertFalse(viewModel.state.value.errorMessage.orEmpty().contains("untrusted"))
        viewModel.updateDraft("Edited after failure")
        assertEquals(GenerationStatus.Editing, viewModel.state.value.status)
        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun invalidServiceOutputsAreRejectedWithoutReplacingExistingDraft() = runTest {
        val outputs = listOf(
            "",
            " ",
            "a\u001Fb",
            "[sound:clip.mp3]",
            "{{c2::hidden}}",
            "<b>unsafe</b>",
            "x".repeat(GenerationValidation.MAX_DRAFT_LENGTH + 1)
        )
        for (output in outputs) {
            val viewModel = GenerationViewModel(MnemonicService { output })
            viewModel.updateDraft("Keep this draft")
            viewModel.generate()
            advanceUntilIdle()
            assertEquals(GenerationStatus.Error, viewModel.state.value.status)
            assertEquals("Keep this draft", viewModel.state.value.draft)
            assertTrue(viewModel.state.value.canApprove)
            assertNull(viewModel.state.value.approval)
        }
    }

    @Test
    fun sourceEditsInvalidatePendingNoncooperativeRequests() = runTest {
        for (editConcept in listOf(true, false)) {
            val result = CompletableDeferred<String>()
            val viewModel = GenerationViewModel(
                MnemonicService {
                    withContext(NonCancellable) { result.await() }
                }
            )
            viewModel.generate()
            runCurrent()
            if (editConcept) {
                viewModel.updateConcept("Changed concept")
            } else {
                viewModel.updateContext("Changed context")
            }
            val editedState = viewModel.state.value
            result.complete("Old source story")
            advanceUntilIdle()
            assertEquals(editedState, viewModel.state.value)
            assertEquals(GenerationStatus.Ready, viewModel.state.value.status)
            assertEquals("", viewModel.state.value.draft)
        }
    }

    @Test
    fun draftEditInvalidatesPendingNoncooperativeRequest() = runTest {
        val result = CompletableDeferred<String>()
        val viewModel = GenerationViewModel(
            MnemonicService {
                withContext(NonCancellable) { result.await() }
            }
        )
        viewModel.generate()
        runCurrent()
        viewModel.updateDraft("Manual replacement")
        viewModel.approve()
        val approvedState = viewModel.state.value
        result.complete("Late suggestion")
        advanceUntilIdle()
        assertEquals(approvedState, viewModel.state.value)
    }

    @Test
    fun cancellationExceptionIsNotGenericErrorAndPreservesDraft() = runTest {
        val viewModel = GenerationViewModel(
            MnemonicService {
                throw CancellationException("Service cancelled")
            }
        )
        viewModel.updateDraft("Keep this draft")
        viewModel.generate()
        advanceUntilIdle()
        assertEquals(GenerationStatus.Editing, viewModel.state.value.status)
        assertEquals("Keep this draft", viewModel.state.value.draft)
        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun invalidInputsNeverInvokeService() = runTest {
        var calls = 0
        val viewModel = GenerationViewModel(
            MnemonicService {
                calls++
                "Unexpected"
            }
        )
        val invalidConcepts = listOf(
            "",
            " \t",
            "x".repeat(GenerationValidation.MAX_CONCEPT_LENGTH + 1),
            "a\u001Fb",
            "[sound:clip.mp3]",
            "{{c1::hidden}}",
            "<img src=x>"
        )
        for (concept in invalidConcepts) {
            viewModel.updateConcept(concept)
            assertFalse(viewModel.state.value.canGenerate)
            viewModel.generate()
            advanceUntilIdle()
            assertEquals(GenerationStatus.Error, viewModel.state.value.status)
        }
        viewModel.updateConcept("Valid concept")
        val invalidContexts = listOf(
            "x".repeat(GenerationValidation.MAX_CONTEXT_LENGTH + 1),
            "a\u001Fb",
            "[sound:clip.mp3]",
            "{{c1::hidden}}",
            "<script>bad</script>"
        )
        for (context in invalidContexts) {
            viewModel.updateContext(context)
            assertFalse(viewModel.state.value.canGenerate)
            viewModel.generate()
            advanceUntilIdle()
        }
        assertEquals(0, calls)
        assertNull(viewModel.state.value.approval)
    }
}
