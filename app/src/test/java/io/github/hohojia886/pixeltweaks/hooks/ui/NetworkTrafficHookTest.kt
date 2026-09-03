package io.github.hohojia886.pixeltweaks.hooks.ui

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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class NetworkTrafficHookTest {

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
        val clazz = NetworkTrafficHook::class.java
        val instance = clazz.getField("INSTANCE").get(null)
        val fields = clazz.declaredFields
        for (field in fields) {
            field.isAccessible = true
            when (field.name) {
                "isEnabled" -> field.set(instance, true)
                "updateInterval" -> field.set(instance, 1000L)
                "autoHideThreshold" -> field.set(instance, 1024L)
                "fontSizeSp" -> field.set(instance, 8f)
            }
        }
    }

    @Test
    fun testHandleSync() {
        val intent = Intent(IpcManager.ACTION_SETTINGS_SYNC).apply {
            putExtra(PreferenceKeys.ENABLE_NETWORK_TRAFFIC, false)
            putExtra(PreferenceKeys.NETWORK_TRAFFIC_INTERVAL, 2)
            putExtra(PreferenceKeys.NETWORK_TRAFFIC_THRESHOLD, 5)
            putExtra(PreferenceKeys.NETWORK_TRAFFIC_FONT_SIZE, 10f)
        }

        val clazz = NetworkTrafficHook::class.java
        val instance = clazz.getField("INSTANCE").get(null)
        val handleMethod = clazz.getDeclaredMethod("handleSync", Intent::class.java)
        handleMethod.isAccessible = true
        handleMethod.invoke(instance, intent)

        val enabledField = clazz.getDeclaredField("isEnabled").apply { isAccessible = true }
        assertFalse(enabledField.get(instance) as Boolean)

        val intervalField = clazz.getDeclaredField("updateInterval").apply { isAccessible = true }
        assertEquals(2000L, intervalField.get(instance) as Long)

        val thresholdField = clazz.getDeclaredField("autoHideThreshold").apply { isAccessible = true }
        assertEquals(5120L, thresholdField.get(instance) as Long)

        val sizeField = clazz.getDeclaredField("fontSizeSp").apply { isAccessible = true }
        assertEquals(10f, sizeField.get(instance) as Float)
    }

}
