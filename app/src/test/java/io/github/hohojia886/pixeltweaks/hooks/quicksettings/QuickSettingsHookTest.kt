package io.github.hohojia886.pixeltweaks.hooks.quicksettings

import android.content.Context
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
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
class QuickSettingsHookTest {

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
        val clazz = QuickSettingsHook::class.java
        val instance = clazz.getField("INSTANCE").get(null)
        val fields = clazz.declaredFields
        for (field in fields) {
            field.isAccessible = true
            if (field.name == "isWifiFixEnabled") field.set(instance, true)
            if (field.name == "isDataFixEnabled") field.set(instance, true)
            if (field.name == "receiverRegistered") field.set(instance, false)
        }
    }

    @Test
    fun testInitialLoad() {
        whenever(prefs.getBoolean(PreferenceKeys.ENABLE_QS_WIFI_FIX, true)).thenReturn(false)
        whenever(prefs.getBoolean(PreferenceKeys.ENABLE_QS_DATA_FIX, true)).thenReturn(false)

        val classLoader = mock(ClassLoader::class.java)
        QuickSettingsHook.hook(module, classLoader)

        val clazz = QuickSettingsHook::class.java
        val instance = clazz.getField("INSTANCE").get(null)
        val wifiField = clazz.getDeclaredField("isWifiFixEnabled")
        wifiField.isAccessible = true
        assertFalse(wifiField.get(instance) as Boolean)

        val dataField = clazz.getDeclaredField("isDataFixEnabled")
        dataField.isAccessible = true
        assertFalse(dataField.get(instance) as Boolean)
    }

}
