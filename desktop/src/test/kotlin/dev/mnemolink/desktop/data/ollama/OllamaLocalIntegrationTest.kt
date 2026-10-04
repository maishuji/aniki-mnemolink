package dev.mnemolink.desktop.data.ollama

import dev.mnemolink.app.domain.GenerationInput
import dev.mnemolink.app.domain.GenerationValidation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in only: sends synthetic data to the already installed local model; never downloads one. */
class OllamaLocalIntegrationTest {
    @Test
    fun `installed qwen3 returns a valid synthetic draft`() = runBlocking {
        assumeTrue(System.getenv("MNEMOLINK_OLLAMA_TEST") == "true")
        OllamaMnemonicService("qwen3:14b").use { service ->
            val draft = service.generate(
                GenerationInput("ubiquitous", "Mot anglais : présent partout.")
            )
            assertNull(GenerationValidation.draftError(draft))
        }
    }
}
