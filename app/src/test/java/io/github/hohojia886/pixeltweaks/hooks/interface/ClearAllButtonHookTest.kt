package io.github.hohojia886.pixeltweaks.hooks.`interface`

import android.content.Context
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
import org.robolectric.RuntimeEnvironment
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ClearAllButtonHookTest {

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
        val clazz = ClearAllButtonHook::class.java
        val instance = clazz.getField("INSTANCE").get(null)
        val fields = clazz.declaredFields
        for (field in fields) {
            field.isAccessible = true
            when (field.name) {
                "isEnabled" -> field.set(instance, true)
                "receiverRegistered" -> field.set(instance, false)
            }
        }
    }

    @Test
    fun testInitialLoad() {
        whenever(prefs.getBoolean(PreferenceKeys.ENABLE_CLEAR_ALL, true)).thenReturn(false)

        val classLoader = mock(ClassLoader::class.java)
        ClearAllButtonHook.hook(module, classLoader)

        val clazz = ClearAllButtonHook::class.java
        val instance = clazz.getField("INSTANCE").get(null)
        val enabledField = clazz.getDeclaredField("isEnabled").apply { isAccessible = true }
        assertFalse(enabledField.get(instance) as Boolean)
    }

    @Test
    fun testIsTabletMode() {
        val context = RuntimeEnvironment.getApplication()
        val config = context.resources.configuration
        config.smallestScreenWidthDp = 320
        context.resources.updateConfiguration(config, context.resources.displayMetrics)

        val method = ClearAllButtonHook::class.java.getDeclaredMethod("isTabletMode", Context::class.java)
        method.isAccessible = true
        assertFalse(method.invoke(ClearAllButtonHook, context) as Boolean)

        config.smallestScreenWidthDp = 600
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
        assertTrue(method.invoke(ClearAllButtonHook, context) as Boolean)
    }

}
