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
 * SettingsActivity: Clean View Layer in MVVM + UDF architecture.
 * Binds SettingsViewModel's StateFlow to Jetpack Compose UI and forwards user/lifecycle events.
 * Registers Screen ON/OFF BroadcastReceiver for instant battery timer power-saving.
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

    override fun onResume() {
        super.onResume()
        viewModel.onEvent(SettingsEvent.OnResume)
    }

    override fun onPause() {
        super.onPause()
        viewModel.onEvent(SettingsEvent.OnPause)
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(screenStateReceiver) }
    }
}
