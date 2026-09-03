package io.github.hohojia886.pixeltweaks.hooks.ui

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
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ScreenshotHookTest {

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
        val clazz = ScreenshotHook::class.java
        val instance = clazz.getField("INSTANCE").get(null)
        val fields = clazz.declaredFields
        for (field in fields) {
            field.isAccessible = true
            when (field.name) {
                "isEnabled" -> field.set(instance, true)
                "isSystemHooked" -> field.set(instance, false)
            }
        }
    }

    @Test
    fun testSyncSettings() {
        whenever(prefs.getBoolean(PreferenceKeys.ENABLE_UNRESTRICTED_SCREENSHOTS, true)).thenReturn(false)

        val clazz = ScreenshotHook::class.java
        val instance = clazz.getField("INSTANCE").get(null)
        val syncMethod = clazz.getDeclaredMethod("syncSettings", XposedModule::class.java)
        syncMethod.isAccessible = true
        syncMethod.invoke(instance, module)

        val enabledField = clazz.getDeclaredField("isEnabled").apply { isAccessible = true }
        assertFalse(enabledField.get(instance) as Boolean)
    }

}
