package io.github.hohojia886.pixeltweaks.hooks.quicksettings

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import io.github.hohojia886.pixeltweaks.utils.IpcDispatcher
import io.github.hohojia886.pixeltweaks.utils.IpcManager
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys
import io.github.libxposed.api.XposedModule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.util.ReflectionHelpers
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class QuickSettingsHookTest {

    private lateinit var module: XposedModule
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        ReflectionHelpers.setStaticField(android.os.Build::class.java, "DISPLAY", "CP2A.260605.001")
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

    @Test
    fun testIpcDispatcherSync() {
        val classLoader = mock(ClassLoader::class.java)
        QuickSettingsHook.hook(module, classLoader)

        val clazz = QuickSettingsHook::class.java
        val instance = clazz.getField("INSTANCE").get(null)
        val wifiField = clazz.getDeclaredField("isWifiFixEnabled")
        val dataField = clazz.getDeclaredField("isDataFixEnabled")
        wifiField.isAccessible = true
        dataField.isAccessible = true

        // Dispatch sync intent via IpcDispatcher listeners
        val syncIntent = Intent(IpcManager.ACTION_SETTINGS_SYNC).apply {
            putExtra(PreferenceKeys.ENABLE_QS_WIFI_FIX, true)
            putExtra(PreferenceKeys.ENABLE_QS_DATA_FIX, false)
        }
        
        val listenersField = IpcDispatcher::class.java.getDeclaredField("listeners").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val listeners = listenersField.get(IpcDispatcher) as List<(Intent) -> Unit>
        listeners.forEach { it.invoke(syncIntent) }

        assertTrue(wifiField.get(instance) as Boolean)
        assertFalse(dataField.get(instance) as Boolean)

        // Dispatch setting changed intent
        val changeIntent = Intent(IpcManager.ACTION_SETTING_CHANGED).apply {
            putExtra(PreferenceKeys.EXTRA_KEY, PreferenceKeys.ENABLE_QS_DATA_FIX)
            putExtra(PreferenceKeys.EXTRA_VALUE, true)
        }
        listeners.forEach { it.invoke(changeIntent) }

        assertTrue(dataField.get(instance) as Boolean)
    }

    @Test
    fun testUnsupportedBuildNumberForcesDataFixDisabled() {
        ReflectionHelpers.setStaticField(android.os.Build::class.java, "DISPLAY", "CP3A.260905.001")
        whenever(prefs.getBoolean(PreferenceKeys.ENABLE_QS_DATA_FIX, true)).thenReturn(true)

        val classLoader = mock(ClassLoader::class.java)
        QuickSettingsHook.hook(module, classLoader)

        val clazz = QuickSettingsHook::class.java
        val instance = clazz.getField("INSTANCE").get(null)
        val dataField = clazz.getDeclaredField("isDataFixEnabled").apply { isAccessible = true }

        assertFalse(dataField.get(instance) as Boolean)
    }
}
