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
        assertFalse(state.allowDowngrade)
        assertFalse(state.bypassSignature)
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

    @Test
    fun testEventToggleAllowDowngradeUpdatesStateAndTimestamp() {
        viewModel.onEvent(SettingsEvent.ToggleAllowDowngrade(true))

        assertTrue(viewModel.uiState.value.allowDowngrade)
        assertTrue(repository.getDowngradeTimestamp() > 0L)

        viewModel.onEvent(SettingsEvent.ToggleAllowDowngrade(false))

        assertFalse(viewModel.uiState.value.allowDowngrade)
    }

    @Test
    fun testEventToggleBypassSignatureUpdatesStateAndTimestamp() {
        viewModel.onEvent(SettingsEvent.ToggleBypassSignature(true))

        assertTrue(viewModel.uiState.value.bypassSignature)
        assertTrue(repository.getSignatureTimestamp() > 0L)

        viewModel.onEvent(SettingsEvent.ToggleBypassSignature(false))

        assertFalse(viewModel.uiState.value.bypassSignature)
    }

    @Test
    fun testScreenOnOffEventsHandledWithoutCrashing() {
        viewModel.onEvent(SettingsEvent.ToggleBatteryInfo(true))
        viewModel.onEvent(SettingsEvent.OnScreenOff)
        viewModel.onEvent(SettingsEvent.OnScreenOn)

        assertTrue(viewModel.uiState.value.enableBatteryInfo)
    }

    @Test
    fun testCameraEnergyRingEventsUpdateState() {
        viewModel.onEvent(SettingsEvent.ToggleCameraEnergyRing(true))
        assertTrue(viewModel.uiState.value.cameraEnergyRing)

        viewModel.onEvent(SettingsEvent.ChangeRingRadiusOffset(1.2f))
        assertEquals(1.2f, viewModel.uiState.value.ringRadiusOffset)

        viewModel.onEvent(SettingsEvent.ChangeRingStrokeWidth(2.5f))
        assertEquals(2.5f, viewModel.uiState.value.ringStrokeWidth)

        viewModel.onEvent(SettingsEvent.ToggleRingOnlyCharging(true))
        assertTrue(viewModel.uiState.value.ringOnlyCharging)

        viewModel.onEvent(SettingsEvent.ToggleRingRotation(true))
        assertTrue(viewModel.uiState.value.ringRotation)
    }

    @Test
    fun testStatusbarBatteryPercentEventUpdatesState() {
        viewModel.onEvent(SettingsEvent.ToggleStatusbarBatteryPercent(true))
        assertTrue(viewModel.uiState.value.statusbarBatteryPercent)
    }

    @Test
    fun testTrafficAdjustmentsUpdateState() {
        viewModel.onEvent(SettingsEvent.ChangeTrafficFontSize(10f))
        assertEquals(10f, viewModel.uiState.value.trafficFontSize)

        viewModel.onEvent(SettingsEvent.ChangeTrafficThreshold(5))
        assertEquals(5, viewModel.uiState.value.trafficThreshold)
    }
}
