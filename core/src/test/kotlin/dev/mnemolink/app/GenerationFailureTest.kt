package dev.mnemolink.app

import dev.mnemolink.app.domain.MnemonicFailure
import dev.mnemolink.app.domain.MnemonicGenerationException
import dev.mnemolink.app.domain.MnemonicService
import dev.mnemolink.app.workflow.GenerationWorkflow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GenerationFailureTest {
    @Test
    fun `categorized failures expose only fixed message even if exception overrides message`() =
        runTest {
            val service = MnemonicService {
                throw object : MnemonicGenerationException(MnemonicFailure.ModelUnavailable) {
                    override val message = "private-secret"
                }
            }
            val workflow = GenerationWorkflow(service, this)
            workflow.generate()
            advanceUntilIdle()
            assertEquals(
                MnemonicFailure.ModelUnavailable.userMessage,
                workflow.state.value.errorMessage
            )
            workflow.close()
        }

    @Test
    fun `unknown failures never expose exception message`() = runTest {
        val workflow = GenerationWorkflow(MnemonicService { error("private-secret") }, this)
        workflow.generate()
        advanceUntilIdle()
        assertFalse(workflow.state.value.errorMessage.orEmpty().contains("private-secret"))
        workflow.close()
    }
}
