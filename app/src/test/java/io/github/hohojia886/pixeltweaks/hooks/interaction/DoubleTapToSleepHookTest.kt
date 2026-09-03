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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class DoubleTapToSleepHookTest {

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
        val clazz = DoubleTapToSleepHook::class.java
        val instance = clazz.getField("INSTANCE").get(null)
        val fields = clazz.declaredFields
        for (field in fields) {
            field.isAccessible = true
            when (field.name) {
                "isDtLauncherEnabled" -> field.set(instance, true)
                "isDtLockscreenEnabled" -> field.set(instance, true)
                "isDtStatusbarEnabled" -> field.set(instance, true)
                "isBouncerShowing" -> field.set(instance, false)
                "lastUnlockInteractionTime" -> field.set(instance, 0L)
            }
        }
    }

    @Test
    fun testInitialSync() {
        whenever(prefs.getBoolean(PreferenceKeys.ENABLE_DT_LAUNCHER, true)).thenReturn(false)
        whenever(prefs.getBoolean(PreferenceKeys.ENABLE_DT_LOCKSCREEN, true)).thenReturn(true)
        whenever(prefs.getBoolean(PreferenceKeys.ENABLE_DT_STATUSBAR, true)).thenReturn(false)

        val classLoader = mock(ClassLoader::class.java)
        DoubleTapToSleepHook.hook(module, classLoader)

        val clazz = DoubleTapToSleepHook::class.java
        val instance = clazz.getField("INSTANCE").get(null)
        val launcherField = clazz.getDeclaredField("isDtLauncherEnabled").apply { isAccessible = true }
        assertFalse(launcherField.get(instance) as Boolean)

        val lockscreenField = clazz.getDeclaredField("isDtLockscreenEnabled").apply { isAccessible = true }
        assertTrue(lockscreenField.get(instance) as Boolean)

        val statusbarField = clazz.getDeclaredField("isDtStatusbarEnabled").apply { isAccessible = true }
        assertFalse(statusbarField.get(instance) as Boolean)
    }

    @Test
    fun testHandleSync() {
        val intent = Intent(IpcManager.ACTION_SETTINGS_SYNC).apply {
            putExtra(PreferenceKeys.ENABLE_DT_LAUNCHER, true)
            putExtra(PreferenceKeys.ENABLE_DT_LOCKSCREEN, false)
            putExtra(PreferenceKeys.ENABLE_DT_STATUSBAR, true)
        }

        val clazz = DoubleTapToSleepHook::class.java
        val instance = clazz.getField("INSTANCE").get(null)
        val handleMethod = clazz.getDeclaredMethod("handleSync", Intent::class.java)
        handleMethod.isAccessible = true
        handleMethod.invoke(instance, intent)

        val launcherField = clazz.getDeclaredField("isDtLauncherEnabled").apply { isAccessible = true }
        assertTrue(launcherField.get(instance) as Boolean)

        val lockscreenField = clazz.getDeclaredField("isDtLockscreenEnabled").apply { isAccessible = true }
        assertFalse(lockscreenField.get(instance) as Boolean)
    }

}
