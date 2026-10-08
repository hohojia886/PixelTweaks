package io.github.hohojia886.pixeltweaks.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.hohojia886.pixeltweaks.utils.IpcManager
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys
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
class SettingsRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: SettingsRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val deContext = context.createDeviceProtectedStorageContext()
        context.getSharedPreferences(IpcManager.PREF_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        deContext.getSharedPreferences(IpcManager.PREF_NAME, Context.MODE_PRIVATE).edit().clear().apply()

        repository = SettingsRepository(context)
    }

    @Test
    fun testLoadInitialStateDefaults() {
        val state = repository.loadInitialState()

        assertTrue(state.networkTraffic)
        assertFalse(state.statusbarBatteryPercent)
        assertFalse(state.cameraEnergyRing)
        assertEquals(0f, state.ringRadiusOffset)
        assertEquals(2.0f, state.ringStrokeWidth)
    }

    @Test
    fun testSavePreferenceUpdatesBothCeAndDe() {
        repository.savePreference(PreferenceKeys.ENABLE_CAMERA_ENERGY_RING, true)
        repository.savePreference(PreferenceKeys.RING_RADIUS_OFFSET, 1.5f)

        val state = repository.loadInitialState()

        assertTrue(state.cameraEnergyRing)
        assertEquals(1.5f, state.ringRadiusOffset)
    }
}
