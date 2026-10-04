package dev.mnemolink.desktop

import dev.mnemolink.app.data.demo.DemoMnemonicService
import dev.mnemolink.app.domain.GenerationInput
import dev.mnemolink.app.domain.GenerationStatus
import dev.mnemolink.app.workflow.GenerationWorkflow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoadNoteInputTest {
    @Test
    fun `identical input clears draft and approval`() = runTest {
        val workflow = GenerationWorkflow(DemoMnemonicService(), this)
        workflow.generate()
        advanceUntilIdle()
        workflow.approve()
        assertEquals(GenerationStatus.Approved, workflow.state.value.status)
        val input = workflow.state.value.input

        workflow.loadNoteInput(input)

        assertEquals(input, workflow.state.value.input)
        assertTrue(workflow.state.value.draft.isEmpty())
        assertNull(workflow.state.value.approval)
        workflow.close()
    }

    @Test
    fun `load cancels pending generation and replaces both fields`() = runTest {
        val workflow = GenerationWorkflow(DemoMnemonicService(), this)
        workflow.generate()
        val input = GenerationInput("new concept", "new context")

        workflow.loadNoteInput(input)
        advanceUntilIdle()

        assertEquals(input, workflow.state.value.input)
        assertTrue(workflow.state.value.draft.isEmpty())
        assertNull(workflow.state.value.approval)
        assertTrue(workflow.state.value.status != GenerationStatus.Generating)
        workflow.close()
    }
}
