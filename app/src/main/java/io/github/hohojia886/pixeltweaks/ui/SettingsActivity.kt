package io.github.hohojia886.pixeltweaks.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.hohojia886.pixeltweaks.data.repository.SettingsRepository
import io.github.hohojia886.pixeltweaks.presentation.SettingsEvent
import io.github.hohojia886.pixeltweaks.presentation.SettingsViewModel
import io.github.hohojia886.pixeltweaks.ui.components.SettingsScreen
import io.github.hohojia886.pixeltweaks.ui.theme.PixelTweaksTheme

/**
 * SettingsActivity: Clean View Layer in MVVM + UDF architecture.
 * Binds SettingsViewModel's StateFlow to Jetpack Compose UI and forwards user/lifecycle events.
 */
class SettingsActivity : ComponentActivity() {

    private lateinit var viewModel: SettingsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val repository = SettingsRepository(applicationContext)
        viewModel = SettingsViewModel(repository)

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            PixelTweaksTheme {
                SettingsScreen(
                    uiState = uiState,
                    onEvent = viewModel::onEvent
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onEvent(SettingsEvent.OnResume)
    }

    override fun onPause() {
        super.onPause()
        viewModel.onEvent(SettingsEvent.OnPause)
    }
}
