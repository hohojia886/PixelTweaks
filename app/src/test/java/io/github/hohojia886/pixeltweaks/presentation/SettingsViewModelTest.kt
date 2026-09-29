package io.github.hohojia886.pixeltweaks.presentation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.hohojia886.pixeltweaks.data.repository.SettingsRepository
import io.github.hohojia886.pixeltweaks.utils.IpcManager
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsViewModelTest {

    private lateinit var context: Context
    private lateinit var repository: SettingsRepository
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val deContext = context.createDeviceProtectedStorageContext()
        deContext.getSharedPreferences(IpcManager.PREF_NAME, Context.MODE_PRIVATE).edit().clear().apply()

        repository = SettingsRepository(context)
        viewModel = SettingsViewModel(repository)
    }

    @Test
    fun testInitialUiStateMatchesRepositoryDefaults() {
        val state = viewModel.uiState.value

        assertTrue(state.networkTraffic)
        assertFalse(state.tabletMode)
        assertFalse(state.enableBatteryInfo)
        assertTrue(state.unrestrictedScreenshots)
    }

    @Test
    fun testEventToggleBatteryInfoUpdatesState() {
        viewModel.onEvent(SettingsEvent.ToggleBatteryInfo(true))

        assertTrue(viewModel.uiState.value.enableBatteryInfo)

        viewModel.onEvent(SettingsEvent.ToggleBatteryInfo(false))

        assertFalse(viewModel.uiState.value.enableBatteryInfo)
    }

    @Test
    fun testEventToggleTabletModeUpdatesState() {
        viewModel.onEvent(SettingsEvent.ToggleTabletMode(true))

        assertTrue(viewModel.uiState.value.tabletMode)
    }

    @Test
    fun testEventChangeTrafficIntervalUpdatesState() {
        viewModel.onEvent(SettingsEvent.ChangeTrafficInterval(3))

        assertEquals(3, viewModel.uiState.value.trafficInterval)
    }
}
