package io.github.hohojia886.pixeltweaks.hooks.interaction

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
class EasyUnlockHookTest {

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
        val clazz = EasyUnlockHook::class.java
        val instance = clazz.getField("INSTANCE").get(null)
        val fields = clazz.declaredFields
        for (field in fields) {
            field.isAccessible = true
            when (field.name) {
                "isEnabled" -> field.set(instance, true)
                "isBypassActive" -> field.set(instance, false)
                "isFirstUnlockDone" -> field.set(instance, false)
                "learnedPinLength" -> field.set(instance, -1)
            }
        }
    }

    @Test
    fun testInitialSync() {
        whenever(prefs.getBoolean(PreferenceKeys.ENABLE_EASY_UNLOCK, true)).thenReturn(false)
        whenever(prefs.getBoolean(PreferenceKeys.ENABLE_EASY_UNLOCK_REBOOT, false)).thenReturn(true)
        whenever(prefs.getInt(PreferenceKeys.EXPECTED_PASS_LEN, -1)).thenReturn(6)

        val classLoader = mock(ClassLoader::class.java)
        EasyUnlockHook.hook(module, classLoader, "com.android.systemui")

        val clazz = EasyUnlockHook::class.java
        val instance = clazz.getField("INSTANCE").get(null)
        val enabledField = clazz.getDeclaredField("isEnabled").apply { isAccessible = true }
        assertFalse(enabledField.get(instance) as Boolean)

        val bypassField = clazz.getDeclaredField("isBypassActive").apply { isAccessible = true }
        assertTrue(bypassField.get(instance) as Boolean)

        val lenField = clazz.getDeclaredField("learnedPinLength").apply { isAccessible = true }
        assertEquals(6, lenField.get(instance) as Int)
    }

    @Test
    fun testHandleBroadcast() {
        val intent = Intent(IpcManager.ACTION_SETTINGS_SYNC).apply {
            putExtra(PreferenceKeys.ENABLE_EASY_UNLOCK, false)
            putExtra(PreferenceKeys.ENABLE_EASY_UNLOCK_REBOOT, true)
            putExtra(PreferenceKeys.EXPECTED_PASS_LEN, 4)
        }

        val clazz = EasyUnlockHook::class.java
        val instance = clazz.getField("INSTANCE").get(null)
        val handleMethod = clazz.getDeclaredMethod("handleBroadcast", Intent::class.java)
        handleMethod.isAccessible = true
        handleMethod.invoke(instance, intent)

        val enabledField = clazz.getDeclaredField("isEnabled").apply { isAccessible = true }
        assertFalse(enabledField.get(instance) as Boolean)

        val bypassField = clazz.getDeclaredField("isBypassActive").apply { isAccessible = true }
        assertTrue(bypassField.get(instance) as Boolean)

        val lenField = clazz.getDeclaredField("learnedPinLength").apply { isAccessible = true }
        assertEquals(4, lenField.get(instance) as Int)
    }

}
