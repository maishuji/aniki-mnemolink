package dev.mnemolink.desktop

import dev.mnemolink.app.domain.GenerationInput
import dev.mnemolink.app.domain.GenerationStatus
import dev.mnemolink.app.domain.MnemonicService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopGenerationControllerTest {
    private class FakeService(
        val backend: GenerationBackend,
        private val action: suspend (GenerationInput) -> String = { "Generated mnemonic" }
    ) : MnemonicService,
        AutoCloseable {
        val requests = mutableListOf<GenerationInput>()
        var closeCalls = 0

        override suspend fun generate(input: GenerationInput): String {
            requests += input
            return action(input)
        }

        override fun close() {
            closeCalls++
        }
    }

    private class FakeFactory {
        val services = mutableListOf<FakeService>()

        fun create(backend: GenerationBackend): FakeService =
            FakeService(backend).also { services += it }
    }

    @Test
    fun preparingRequestCapturesInputAndBackendWithoutGenerating() = runTest {
        val factory = FakeFactory()
        DesktopGenerationController(this, factory = factory::create).use { controller ->
            controller.workflow.updateConcept("Consent concept")
            controller.workflow.updateContext("Consent context")
            controller.selectBackend(GenerationBackend.Qwen25)
            val state = controller.workflow.state.value

            val request = controller.prepareRequest()
            runCurrent()

            assertEquals(GenerationRequest(state.input, GenerationBackend.Qwen25), request)
            assertEquals(request, controller.prepareRequest())
            assertEquals(state, controller.workflow.state.value)
            assertTrue(factory.services.all { it.requests.isEmpty() })
        }
    }

    @Test
    fun preparingRequestRejectsInvalidInputWithoutGenerating() = runTest {
        val factory = FakeFactory()
        DesktopGenerationController(this, factory = factory::create).use { controller ->
            controller.workflow.updateConcept("")
            val state = controller.workflow.state.value
            assertFalse(state.canGenerate)

            assertNull(controller.prepareRequest())
            controller.generateConfirmed(GenerationRequest(state.input, controller.backend.value))
            runCurrent()

            assertEquals(state, controller.workflow.state.value)
            assertTrue(factory.services.single().requests.isEmpty())
        }
    }

    @Test
    fun confirmingMatchingRequestGeneratesWithCapturedInputAndBackend() = runTest {
        val factory = FakeFactory()
        DesktopGenerationController(this, factory = factory::create).use { controller ->
            controller.selectBackend(GenerationBackend.Qwen25)
            controller.workflow.updateConcept("Confirmed concept")
            controller.workflow.updateContext("Confirmed context")
            val request = checkNotNull(controller.prepareRequest())

            controller.generateConfirmed(request)
            assertEquals(GenerationStatus.Generating, controller.workflow.state.value.status)
            runCurrent()

            assertTrue(factory.services.first().requests.isEmpty())
            assertEquals(request.backend, factory.services.last().backend)
            assertEquals(listOf(request.input), factory.services.last().requests)
            assertEquals(GenerationStatus.Editing, controller.workflow.state.value.status)
            assertEquals("Generated mnemonic", controller.workflow.state.value.draft)
        }
    }

    @Test
    fun confirmingRequestWithStaleConceptOrContextIsIgnored() = runTest {
        for (editConcept in listOf(true, false)) {
            val factory = FakeFactory()
            DesktopGenerationController(this, factory = factory::create).use { controller ->
                val request = checkNotNull(controller.prepareRequest())
                if (editConcept) {
                    controller.workflow.updateConcept("Changed concept")
                } else {
                    controller.workflow.updateContext("Changed context")
                }
                val editedState = controller.workflow.state.value
                assertTrue(editedState.canGenerate)

                controller.generateConfirmed(request)
                runCurrent()

                assertEquals(editedState, controller.workflow.state.value)
                assertTrue(factory.services.single().requests.isEmpty())
            }
        }
    }

    @Test
    fun confirmingRequestWithStaleBackendIsIgnoredEvenWhenInputMatches() = runTest {
        val factory = FakeFactory()
        DesktopGenerationController(this, factory = factory::create).use { controller ->
            val request = checkNotNull(controller.prepareRequest())
            controller.selectBackend(GenerationBackend.Qwen25)
            val switchedState = controller.workflow.state.value
            assertEquals(request.input, switchedState.input)
            assertTrue(switchedState.canGenerate)

            controller.generateConfirmed(request)
            runCurrent()

            assertEquals(switchedState, controller.workflow.state.value)
            assertEquals(GenerationBackend.Qwen25, controller.backend.value)
            assertTrue(factory.services.all { it.requests.isEmpty() })
        }
    }

    @Test
    fun duplicateConfirmationWhileGeneratingIsIgnoredAndPreparationIsUnavailable() = runTest {
        val result = CompletableDeferred<String>()
        val service = FakeService(GenerationBackend.Qwen3) { result.await() }
        DesktopGenerationController(this, factory = { service }).use { controller ->
            val request = checkNotNull(controller.prepareRequest())
            controller.generateConfirmed(request)
            val generatingState = controller.workflow.state.value
            assertEquals(GenerationStatus.Generating, generatingState.status)

            controller.generateConfirmed(request)
            assertNull(controller.prepareRequest())
            runCurrent()
            controller.generateConfirmed(request)
            runCurrent()

            assertEquals(generatingState, controller.workflow.state.value)
            assertEquals(listOf(request.input), service.requests)
            assertNull(controller.prepareRequest())
            result.complete("Confirmed once")
            runCurrent()
            assertEquals("Confirmed once", controller.workflow.state.value.draft)
            assertEquals(1, service.requests.size)
        }
    }

    @Test
    fun closeRejectsPendingConsentAndFurtherPreparation() = runTest {
        val factory = FakeFactory()
        DesktopGenerationController(this, factory = factory::create).use { controller ->
            val request = checkNotNull(controller.prepareRequest())
            val closedState = controller.workflow.state.value
            controller.close()
            assertEquals(request.input, closedState.input)
            assertTrue(closedState.canGenerate)

            assertNull(controller.prepareRequest())
            controller.generateConfirmed(request)
            runCurrent()

            assertEquals(closedState, controller.workflow.state.value)
            assertEquals(request.backend, controller.backend.value)
            assertTrue(factory.services.single().requests.isEmpty())
            assertEquals(1, factory.services.single().closeCalls)
        }
    }

    @Test
    fun constructionDefaultsToQwen3WithoutSendingRequests() = runTest {
        val factory = FakeFactory()
        DesktopGenerationController(this, factory = factory::create).use { controller ->
            runCurrent()

            assertEquals(GenerationBackend.Qwen3, controller.backend.value)
            assertEquals("qwen3:14b", controller.backend.value.model)
            assertEquals(listOf(GenerationBackend.Qwen3), factory.services.map { it.backend })
            assertTrue(factory.services.single().requests.isEmpty())
            assertEquals(GenerationStatus.Ready, controller.workflow.state.value.status)
            assertEquals(0, factory.services.single().closeCalls)
        }
        assertEquals(1, factory.services.single().closeCalls)
    }

    @Test
    fun generationDispatchesExactInputToEachChosenBackend() = runTest {
        val factory = FakeFactory()
        DesktopGenerationController(this, factory = factory::create).use { controller ->
            val input = GenerationInput("ubiquitous", "Present everywhere")
            controller.workflow.updateConcept(input.concept)
            controller.workflow.updateContext(input.context)

            for (backend in GenerationBackend.entries) {
                controller.selectBackend(backend)
                assertEquals(backend, controller.backend.value)
                assertTrue(factory.services.last().requests.isEmpty())

                controller.workflow.generate()
                runCurrent()

                assertEquals(backend, factory.services.last().backend)
                assertEquals(listOf(input), factory.services.last().requests)
                assertEquals("Generated mnemonic", controller.workflow.state.value.draft)
            }
            assertEquals(
                listOf("qwen3:14b", "qwen2.5:14b", null),
                factory.services.map { it.backend.model }
            )
            assertEquals(listOf(1, 1, 0), factory.services.map { it.closeCalls })
        }
        assertEquals(listOf(1, 1, 1), factory.services.map { it.closeCalls })
    }

    @Test
    fun selectingDifferentBackendClearsDraftAndApprovalWithoutChangingInput() = runTest {
        val factory = FakeFactory()
        DesktopGenerationController(this, factory = factory::create).use { controller ->
            controller.workflow.generate()
            runCurrent()
            controller.workflow.approve()
            val input = controller.workflow.state.value.input
            assertEquals(GenerationStatus.Approved, controller.workflow.state.value.status)
            assertNotNull(controller.workflow.state.value.approval)

            controller.selectBackend(GenerationBackend.Qwen25)

            val state = controller.workflow.state.value
            assertEquals(input, state.input)
            assertEquals(GenerationStatus.Ready, state.status)
            assertEquals("", state.draft)
            assertNull(state.approval)
            assertNull(state.errorMessage)
            assertTrue(factory.services.last().requests.isEmpty())
        }
    }

    @Test
    fun selectingSameBackendPreservesApprovalAndService() = runTest {
        val factory = FakeFactory()
        DesktopGenerationController(this, factory = factory::create).use { controller ->
            controller.workflow.generate()
            runCurrent()
            controller.workflow.approve()
            val approvedState = controller.workflow.state.value
            assertNotNull(approvedState.approval)

            controller.selectBackend(GenerationBackend.Qwen3)
            runCurrent()

            assertEquals(approvedState, controller.workflow.state.value)
            assertEquals(1, factory.services.size)
            assertEquals(0, factory.services.single().closeCalls)
            assertEquals(1, factory.services.single().requests.size)
        }
    }

    @Test
    fun selectingSameBackendDoesNotCancelInFlightRequest() = runTest {
        val result = CompletableDeferred<String>()
        val service = FakeService(GenerationBackend.Qwen3) { result.await() }
        var creations = 0
        DesktopGenerationController(this, factory = {
            creations++
            service
        }).use { controller ->
            controller.workflow.generate()
            runCurrent()
            val generatingState = controller.workflow.state.value

            controller.selectBackend(GenerationBackend.Qwen3)

            assertEquals(generatingState, controller.workflow.state.value)
            assertEquals(1, creations)
            assertEquals(0, service.closeCalls)
            result.complete("Original request result")
            runCurrent()
            assertEquals("Original request result", controller.workflow.state.value.draft)
            assertEquals(1, service.requests.size)
        }
    }

    @Test
    fun switchingBackendCancelsInFlightRequestAndClosesPreviousService() = runTest {
        var cancelled = false
        val previous = FakeService(GenerationBackend.Qwen3) {
            try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
        }
        val replacement = FakeService(GenerationBackend.Qwen25)
        DesktopGenerationController(this, factory = {
            if (it == GenerationBackend.Qwen3) previous else replacement
        }).use { controller ->
            controller.workflow.generate()
            runCurrent()
            assertEquals(1, previous.requests.size)

            controller.selectBackend(GenerationBackend.Qwen25)
            runCurrent()

            assertTrue(cancelled)
            assertEquals(1, previous.closeCalls)
            assertEquals(0, replacement.closeCalls)
            assertTrue(replacement.requests.isEmpty())
            assertEquals(GenerationStatus.Ready, controller.workflow.state.value.status)
            assertEquals("", controller.workflow.state.value.draft)
            assertNull(controller.workflow.state.value.approval)
        }
        assertEquals(1, previous.closeCalls)
        assertEquals(1, replacement.closeCalls)
    }

    @Test
    fun noncooperativeLateResultCannotReplaceNewBackendApproval() = runTest {
        val oldResult = CompletableDeferred<String>()
        var oldRequestFinished = false
        val previous = FakeService(GenerationBackend.Qwen3) {
            withContext(NonCancellable) {
                val result = oldResult.await()
                oldRequestFinished = true
                result
            }
        }
        val replacement = FakeService(GenerationBackend.Qwen25) { "New backend mnemonic" }
        try {
            DesktopGenerationController(this, factory = {
                if (it == GenerationBackend.Qwen3) previous else replacement
            }).use { controller ->
                controller.workflow.generate()
                runCurrent()
                controller.selectBackend(GenerationBackend.Qwen25)
                runCurrent()
                assertFalse(oldRequestFinished)
                assertEquals(1, previous.closeCalls)

                controller.workflow.generate()
                runCurrent()
                controller.workflow.approve()
                val approvedState = controller.workflow.state.value
                assertEquals("New backend mnemonic", approvedState.draft)
                assertNotNull(approvedState.approval)

                oldResult.complete("Stale backend mnemonic")
                runCurrent()

                assertTrue(oldRequestFinished)
                assertEquals(approvedState, controller.workflow.state.value)
                assertEquals(GenerationBackend.Qwen25, controller.backend.value)
                assertEquals(1, previous.requests.size)
                assertEquals(listOf(approvedState.input), replacement.requests)
            }
        } finally {
            oldResult.complete("Release pending request")
        }
    }

    @Test
    fun switchingBeforeDispatchDoesNotSendQueuedRequestToEitherService() = runTest {
        val factory = FakeFactory()
        DesktopGenerationController(this, factory = factory::create).use { controller ->
            controller.workflow.generate()
            controller.selectBackend(GenerationBackend.Demo)
            runCurrent()

            assertTrue(factory.services.all { it.requests.isEmpty() })
            assertEquals(GenerationBackend.Demo, controller.backend.value)
            assertEquals(GenerationStatus.Ready, controller.workflow.state.value.status)
        }
    }

    @Test
    fun closeIsIdempotentCancelsOwnedResourcesButNotCallerScope() = runTest {
        var cancelled = false
        val service = FakeService(GenerationBackend.Qwen3) {
            try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
        }
        DesktopGenerationController(this, factory = { service }).use { controller ->
            val callerJob = coroutineContext[Job]!!
            controller.workflow.generate()
            runCurrent()
            assertTrue(callerJob.children.any { it.isActive })
            val closedState = controller.workflow.state.value

            controller.close()
            controller.close()
            runCurrent()

            assertTrue(cancelled)
            assertEquals(1, service.closeCalls)
            assertFalse(callerJob.children.any { it.isActive })
            assertTrue(isActive)
            var callerWorkRan = false
            launch { callerWorkRan = true }
            runCurrent()
            assertTrue(callerWorkRan)
            assertEquals(closedState, controller.workflow.state.value)
        }
        assertEquals(1, service.closeCalls)
    }

    @Test
    fun closedControllerIgnoresBackendAndWorkflowChanges() = runTest {
        val factory = FakeFactory()
        DesktopGenerationController(this, factory = factory::create).use { controller ->
            controller.workflow.updateDraft("Approved mnemonic")
            controller.workflow.approve()
            val closedState = controller.workflow.state.value
            assertNotNull(closedState.approval)
            controller.close()

            controller.selectBackend(GenerationBackend.Qwen25)
            controller.selectBackend(GenerationBackend.Demo)
            controller.selectBackend(GenerationBackend.Qwen3)
            controller.workflow.updateConcept("Ignored concept")
            controller.workflow.updateContext("Ignored context")
            controller.workflow.updateDraft("Ignored draft")
            controller.workflow.generate()
            controller.workflow.approve()
            controller.workflow.cancel()
            runCurrent()

            assertEquals(GenerationBackend.Qwen3, controller.backend.value)
            assertEquals(closedState, controller.workflow.state.value)
            assertEquals(1, factory.services.size)
            assertEquals(1, factory.services.single().closeCalls)
            assertTrue(factory.services.single().requests.isEmpty())
        }
    }
}
