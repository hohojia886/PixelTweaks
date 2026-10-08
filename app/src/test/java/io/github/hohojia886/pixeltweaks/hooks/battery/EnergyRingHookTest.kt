package io.github.hohojia886.pixeltweaks.hooks.battery

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import io.github.hohojia886.pixeltweaks.utils.IpcManager
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys
import io.github.libxposed.api.XposedModule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EnergyRingHookTest {

    private lateinit var module: XposedModule
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        module = mock(XposedModule::class.java)
        prefs = mock(SharedPreferences::class.java)

        val appInfo = ApplicationInfo().apply { uid = 1000 }
        whenever(module.getModuleApplicationInfo()).thenReturn(appInfo)
        whenever(module.getRemotePreferences(IpcManager.PREF_NAME)).thenReturn(prefs)

        resetSingleton()
    }

    private fun resetSingleton() {
        val clazz = EnergyRingHook::class.java
        val instance = clazz.getField("INSTANCE").get(null)
        val fields = clazz.declaredFields
        for (field in fields) {
            field.isAccessible = true
            when (field.name) {
                "isEnabled" -> field.set(instance, false)
                "ringOnlyCharging" -> field.set(instance, false)
                "ringRotation" -> field.set(instance, false)
                "ringRadiusOffset" -> field.set(instance, 0f)
                "ringStrokeWidth" -> field.set(instance, 2.0f)
            }
        }
    }

    @Test
    fun testHandleBroadcastSyncSettings() {
        val intent = Intent(IpcManager.ACTION_SETTINGS_SYNC).apply {
            putExtra(PreferenceKeys.ENABLE_CAMERA_ENERGY_RING, true)
            putExtra(PreferenceKeys.RING_ONLY_CHARGING, true)
            putExtra(PreferenceKeys.ENABLE_RING_ROTATION, true)
            putExtra(PreferenceKeys.RING_RADIUS_OFFSET, 1.5f)
            putExtra(PreferenceKeys.RING_STROKE_WIDTH, 2.5f)
        }

        val clazz = EnergyRingHook::class.java
        val instance = clazz.getField("INSTANCE").get(null)
        val handleMethod = clazz.getDeclaredMethod("handleBroadcast", Intent::class.java).apply { isAccessible = true }
        handleMethod.invoke(instance, intent)

        val enabledField = clazz.getDeclaredField("isEnabled").apply { isAccessible = true }
        assertTrue(enabledField.get(instance) as Boolean)

        val onlyChargingField = clazz.getDeclaredField("ringOnlyCharging").apply { isAccessible = true }
        assertTrue(onlyChargingField.get(instance) as Boolean)

        val rotationField = clazz.getDeclaredField("ringRotation").apply { isAccessible = true }
        assertTrue(rotationField.get(instance) as Boolean)

        val offsetField = clazz.getDeclaredField("ringRadiusOffset").apply { isAccessible = true }
        assertEquals(1.5f, offsetField.get(instance) as Float)

        val strokeField = clazz.getDeclaredField("ringStrokeWidth").apply { isAccessible = true }
        assertEquals(2.5f, strokeField.get(instance) as Float)
    }

    @Test
    fun testHandleBroadcastSettingChanged() {
        val intent = Intent(IpcManager.ACTION_SETTING_CHANGED).apply {
            putExtra(PreferenceKeys.EXTRA_KEY, PreferenceKeys.ENABLE_CAMERA_ENERGY_RING)
            putExtra(PreferenceKeys.EXTRA_VALUE, true)
        }

        val clazz = EnergyRingHook::class.java
        val instance = clazz.getField("INSTANCE").get(null)
        val handleMethod = clazz.getDeclaredMethod("handleBroadcast", Intent::class.java).apply { isAccessible = true }
        handleMethod.invoke(instance, intent)

        val enabledField = clazz.getDeclaredField("isEnabled").apply { isAccessible = true }
        assertTrue(enabledField.get(instance) as Boolean)
    }
}
