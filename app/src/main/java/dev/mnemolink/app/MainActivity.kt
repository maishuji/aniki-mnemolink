package dev.mnemolink.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.mnemolink.app.feature.generate.GenerationScreen
import dev.mnemolink.app.feature.generate.GenerationViewModel

class MainActivity : ComponentActivity() {
    private val generationViewModel: GenerationViewModel by viewModels {
        viewModelFactory {
            initializer {
                GenerationViewModel((application as MnemoLinkApplication).container.mnemonicService)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            ) {
                val state by generationViewModel.state.collectAsStateWithLifecycle()
                GenerationScreen(
                    state = state,
                    onConceptChange = generationViewModel::updateConcept,
                    onContextChange = generationViewModel::updateContext,
                    onGenerate = generationViewModel::generate,
                    onDraftChange = generationViewModel::updateDraft,
                    onApprove = generationViewModel::approve,
                    onCancel = generationViewModel::cancel
                )
            }
        }
    }
}
