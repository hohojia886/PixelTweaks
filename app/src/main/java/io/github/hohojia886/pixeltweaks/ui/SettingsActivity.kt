package io.github.hohojia886.pixeltweaks.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.hohojia886.pixeltweaks.data.repository.SettingsRepository
import io.github.hohojia886.pixeltweaks.presentation.SettingsEvent
import io.github.hohojia886.pixeltweaks.presentation.SettingsViewModel
import io.github.hohojia886.pixeltweaks.ui.components.SettingsScreen
import io.github.hohojia886.pixeltweaks.ui.theme.PixelTweaksTheme

/**
 * Main Settings Activity hosting the Jetpack Compose user interface.
 *
 * Binds [SettingsViewModel] state to [SettingsScreen] and registers screen state receivers
 * to pause/resume background battery polling when screen turns off/on.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var viewModel: SettingsViewModel

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> viewModel.onEvent(SettingsEvent.OnScreenOff)
                Intent.ACTION_SCREEN_ON -> viewModel.onEvent(SettingsEvent.OnScreenOn)
            }
        }
    }

    /**
     * Initializes the Activity, constructs dependencies, and sets up Compose UI content.
     *
     * @param savedInstanceState Bundle containing previously saved activity state.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val repository = SettingsRepository(applicationContext)
        viewModel = SettingsViewModel(repository)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenStateReceiver, filter)

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

    /**
     * Resumes background tasks when activity becomes visible.
     * Triggers OnResume event to restart ViewModel timers.
     */
    override fun onResume() {
        super.onResume()
        viewModel.onEvent(SettingsEvent.OnResume)
    }

    /**
     * Pauses background tasks when activity loses user focus.
     * Triggers OnPause event to halt ViewModel polling jobs.
     */
    override fun onPause() {
        super.onPause()
        viewModel.onEvent(SettingsEvent.OnPause)
    }

    /**
     * Cleans up registered receivers to prevent memory leaks.
     * Unregisters screenStateReceiver on activity destruction.
     */
    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(screenStateReceiver) }
    }
}
