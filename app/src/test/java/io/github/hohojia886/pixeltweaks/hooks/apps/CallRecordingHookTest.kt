package io.github.hohojia886.pixeltweaks.hooks.apps

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
class CallRecordingHookTest {

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
        val clazz = CallRecordingHook::class.java
        runCatching {
            val instance = clazz.getField("INSTANCE").get(null)
            clazz.getDeclaredField("isRecordingEnabled").apply { isAccessible = true; set(instance, true) }
            clazz.getDeclaredField("isSilenceEnabled").apply { isAccessible = true; set(instance, true) }
        }
    }

    @Test
    fun testSyncState() {
        val clazz = CallRecordingHook::class.java
        val instance = runCatching { clazz.getField("INSTANCE").get(null) }.getOrNull() ?: return
        val recField = runCatching { clazz.getDeclaredField("isRecordingEnabled") }.getOrNull() ?: return
        val silField = runCatching { clazz.getDeclaredField("isSilenceEnabled") }.getOrNull() ?: return
        recField.isAccessible = true
        silField.isAccessible = true

        whenever(prefs.getBoolean(PreferenceKeys.ENABLE_CALL_RECORDING, true)).thenReturn(false)
        whenever(prefs.getBoolean(PreferenceKeys.DISABLE_VOICE_ANNOUNCEMENT, true)).thenReturn(false)

        val syncMethod = runCatching { clazz.getDeclaredMethod("syncState", XposedModule::class.java) }.getOrNull() ?: return
        syncMethod.isAccessible = true
        syncMethod.invoke(instance, module)

        assertFalse(recField.get(instance) as Boolean)
        assertFalse(silField.get(instance) as Boolean)
    }

}
