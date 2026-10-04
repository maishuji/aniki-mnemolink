package dev.mnemolink.app

import dev.mnemolink.app.domain.EntryMode
import dev.mnemolink.app.domain.GenerationInput
import dev.mnemolink.app.domain.GenerationStatus
import dev.mnemolink.app.domain.GenerationValidation
import dev.mnemolink.app.domain.MnemonicService
import dev.mnemolink.app.workflow.GenerationWorkflow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GenerationWorkflowTest {
    private fun workflowTest(block: suspend TestScope.() -> Unit) =
        TestScope(StandardTestDispatcher()).runTest { block() }

    @Test
    fun successAndApprovalCaptureExactInputAndEditedText() = workflowTest {
        var calls = 0
        GenerationWorkflow(
            MnemonicService { input ->
                calls++
                assertEquals("ubiquitous", input.concept)
                "An imaginary story"
            },
            this
        ).use { workflow ->
            assertEquals(GenerationStatus.Ready, workflow.state.value.status)
            assertTrue(workflow.state.value.input.context.contains("Synthetic demo"))
            assertEquals(EntryMode.Standalone, workflow.state.value.input.entryMode)
            workflow.generate()
            assertEquals(GenerationStatus.Generating, workflow.state.value.status)
            assertFalse(workflow.state.value.canApprove)
            advanceUntilIdle()
            assertEquals(GenerationStatus.Editing, workflow.state.value.status)
            assertEquals("An imaginary story", workflow.state.value.draft)
            assertNull(workflow.state.value.approval)
            workflow.updateDraft("My edited story")
            workflow.approve()
            assertEquals(GenerationStatus.Approved, workflow.state.value.status)
            assertEquals(workflow.state.value.input, workflow.state.value.approval?.input)
            assertEquals("My edited story", workflow.state.value.approval?.text)
            workflow.approve()
            assertEquals(1, calls)
            workflow.updateDraft("My edited story")
            assertNull(workflow.state.value.approval)
        }
    }

    @Test
    fun blankAndUnsafeOutputsAreRejectedWithoutReplacingDraft() = workflowTest {
        val outputs = listOf(
            "",
            " \n\t",
            "a\u001Fb",
            "[sound:clip.mp3]",
            "{{c1::hidden}}",
            "<b>unsafe</b>",
            "x".repeat(GenerationValidation.MAX_DRAFT_LENGTH + 1)
        )
        for (output in outputs) {
            GenerationWorkflow(MnemonicService { output }, this).use { workflow ->
                workflow.updateDraft("Keep this draft")
                workflow.approve()
                workflow.generate()
                advanceUntilIdle()
                assertEquals(GenerationStatus.Error, workflow.state.value.status)
                assertEquals("Keep this draft", workflow.state.value.draft)
                assertEquals(
                    GenerationValidation.draftError(output),
                    workflow.state.value.errorMessage
                )
                assertNull(workflow.state.value.approval)
                workflow.updateDraft(output)
                workflow.approve()
                assertFalse(workflow.state.value.canApprove)
                assertNull(workflow.state.value.approval)
            }
        }
    }

    @Test
    fun duplicateTapsStartOnlyOneRequest() = workflowTest {
        var calls = 0
        val result = CompletableDeferred<String>()
        GenerationWorkflow(
            MnemonicService {
                calls++
                result.await()
            },
            this
        ).use { workflow ->
            workflow.generate()
            workflow.generate()
            runCurrent()
            workflow.generate()
            workflow.approve()
            assertEquals(1, calls)
            assertNull(workflow.state.value.approval)
            result.complete("One story")
            advanceUntilIdle()
            assertEquals("One story", workflow.state.value.draft)
        }
    }

    @Test
    fun cancelStopsRequestAndClearsLocalDraftAndApproval() = workflowTest {
        var cancelled = false
        var calls = 0
        GenerationWorkflow(
            MnemonicService {
                calls++
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            },
            this
        ).use { workflow ->
            val input = workflow.state.value.input
            workflow.updateDraft("Previous draft")
            workflow.approve()
            workflow.generate()
            runCurrent()
            workflow.cancel()
            advanceUntilIdle()
            assertTrue(cancelled)
            assertEquals(1, calls)
            assertEquals(input, workflow.state.value.input)
            assertEquals(GenerationStatus.Ready, workflow.state.value.status)
            assertEquals("", workflow.state.value.draft)
            assertNull(workflow.state.value.approval)
            assertNull(workflow.state.value.errorMessage)
        }
    }

    @Test
    fun noncooperativeResponseCannotOverwriteNewerApproval() = workflowTest {
        var calls = 0
        val oldResult = CompletableDeferred<String>()
        GenerationWorkflow(
            MnemonicService {
                calls++
                if (calls == 1) withContext(NonCancellable) { oldResult.await() } else "New result"
            },
            this
        ).use { workflow ->
            workflow.generate()
            runCurrent()
            workflow.cancel()
            workflow.generate()
            runCurrent()
            assertEquals("New result", workflow.state.value.draft)
            workflow.approve()
            val approvedState = workflow.state.value
            oldResult.complete("Stale result")
            advanceUntilIdle()
            assertEquals(approvedState, workflow.state.value)
            assertEquals(2, calls)
        }
    }

    @Test
    fun sourceEditsClearApprovalAndInvalidateNoncooperativeRequests() = workflowTest {
        for (editConcept in listOf(true, false)) {
            val result = CompletableDeferred<String>()
            GenerationWorkflow(
                MnemonicService {
                    withContext(NonCancellable) { result.await() }
                },
                this
            ).use { workflow ->
                workflow.updateDraft("Approved draft")
                workflow.approve()
                workflow.updateConcept(workflow.state.value.input.concept)
                workflow.updateContext(workflow.state.value.input.context)
                assertEquals(GenerationStatus.Approved, workflow.state.value.status)
                if (editConcept) {
                    workflow.updateConcept("First edit")
                } else {
                    workflow.updateContext("First edit")
                }
                assertEquals("", workflow.state.value.draft)
                assertNull(workflow.state.value.approval)
                workflow.generate()
                runCurrent()
                if (editConcept) {
                    workflow.updateConcept("Changed concept")
                } else {
                    workflow.updateContext("Changed context")
                }
                val editedState = workflow.state.value
                result.complete("Old source story")
                advanceUntilIdle()
                assertEquals(editedState, workflow.state.value)
                assertEquals(GenerationStatus.Ready, workflow.state.value.status)
                assertEquals("", workflow.state.value.draft)
            }
        }
    }

    @Test
    fun manualDraftEditInvalidatesNoncooperativeRequest() = workflowTest {
        val result = CompletableDeferred<String>()
        GenerationWorkflow(
            MnemonicService {
                withContext(NonCancellable) { result.await() }
            },
            this
        ).use { workflow ->
            workflow.generate()
            runCurrent()
            workflow.updateDraft("Manual replacement")
            workflow.approve()
            val approvedState = workflow.state.value
            result.complete("Late suggestion")
            advanceUntilIdle()
            assertEquals(approvedState, workflow.state.value)
        }
    }

    @Test
    fun closeCancelsInFlightRequestWithoutCancellingCallerScope() = workflowTest {
        var cancelled = false
        val workflow = GenerationWorkflow(
            MnemonicService {
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            },
            this
        )
        workflow.generate()
        runCurrent()
        val closedState = workflow.state.value
        workflow.close()
        workflow.close()
        advanceUntilIdle()
        assertTrue(cancelled)
        assertTrue(isActive)
        var callerWorkRan = false
        launch { callerWorkRan = true }
        runCurrent()
        assertTrue(callerWorkRan)
        workflow.generate()
        workflow.updateConcept("Ignored")
        workflow.updateContext("Ignored")
        workflow.updateDraft("Ignored")
        workflow.approve()
        workflow.cancel()
        assertEquals(closedState, workflow.state.value)
    }

    @Test
    fun closeInvalidatesNoncooperativeResponse() = workflowTest {
        val result = CompletableDeferred<String>()
        val workflow = GenerationWorkflow(
            MnemonicService {
                withContext(NonCancellable) { result.await() }
            },
            this
        )
        workflow.generate()
        runCurrent()
        val closedState = workflow.state.value
        workflow.close()
        result.complete("Late result")
        advanceUntilIdle()
        assertEquals(closedState, workflow.state.value)
        assertTrue(isActive)
    }

    @Test
    fun regenerateFailurePreservesDraftAndHidesServiceDetails() = workflowTest {
        GenerationWorkflow(
            MnemonicService {
                throw IllegalStateException("<untrusted service details>")
            },
            this
        ).use { workflow ->
            workflow.updateDraft("Keep this draft")
            workflow.approve()
            workflow.generate()
            assertNull(workflow.state.value.approval)
            advanceUntilIdle()
            assertEquals(GenerationStatus.Error, workflow.state.value.status)
            assertEquals("Keep this draft", workflow.state.value.draft)
            assertTrue(workflow.state.value.canApprove)
            assertEquals(
                "Could not generate a mnemonic. Please try again.",
                workflow.state.value.errorMessage
            )
            workflow.updateDraft("Edited after failure")
            assertEquals(GenerationStatus.Editing, workflow.state.value.status)
            assertNull(workflow.state.value.errorMessage)
        }
    }

    @Test
    fun serviceCancellationRestoresEditableStatusWithoutError() = workflowTest {
        GenerationWorkflow(
            MnemonicService {
                throw CancellationException("Service cancelled")
            },
            this
        ).use { workflow ->
            workflow.generate()
            advanceUntilIdle()
            assertEquals(GenerationStatus.Ready, workflow.state.value.status)
            workflow.updateDraft("Keep this draft")
            workflow.generate()
            advanceUntilIdle()
            assertEquals(GenerationStatus.Editing, workflow.state.value.status)
            assertEquals("Keep this draft", workflow.state.value.draft)
            assertNull(workflow.state.value.errorMessage)
        }
    }

    @Test
    fun invalidSourceNeverInvokesService() = workflowTest {
        var calls = 0
        GenerationWorkflow(
            MnemonicService {
                calls++
                "Unexpected"
            },
            this
        ).use { workflow ->
            workflow.updateConcept(" ")
            workflow.generate()
            advanceUntilIdle()
            assertEquals(GenerationStatus.Error, workflow.state.value.status)
            assertEquals("Enter a concept.", workflow.state.value.errorMessage)
            workflow.updateConcept("Valid")
            workflow.updateContext("<script>unsafe</script>")
            workflow.generate()
            advanceUntilIdle()
            assertEquals(GenerationStatus.Error, workflow.state.value.status)
            assertEquals(0, calls)
        }
    }

    @Test
    fun returnToHostSurvivesEditsAndApprovalIsLocalOnly() = workflowTest {
        val requests = mutableListOf<GenerationInput>()
        val initial = GenerationInput("Concept", "Context", EntryMode.ReturnToHost)
        GenerationWorkflow(
            MnemonicService {
                requests += it
                "A story"
            },
            this,
            initial
        ).use { workflow ->
            assertEquals(initial, workflow.state.value.input)
            workflow.updateConcept("Changed concept")
            workflow.updateContext("Changed context")
            assertEquals(EntryMode.ReturnToHost, workflow.state.value.input.entryMode)
            assertTrue(requests.isEmpty())
            workflow.generate()
            advanceUntilIdle()
            assertNull(workflow.state.value.approval)
            workflow.approve()
            val proposal = workflow.state.value.approval
            assertEquals(EntryMode.ReturnToHost, proposal?.input?.entryMode)
            assertEquals("A story", proposal?.text)
            assertEquals(listOf(workflow.state.value.input), requests)
            workflow.cancel()
            advanceUntilIdle()
            assertEquals(EntryMode.ReturnToHost, workflow.state.value.input.entryMode)
            assertNull(workflow.state.value.approval)
            assertEquals(1, requests.size)
        }
    }
}
