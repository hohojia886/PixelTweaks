package io.github.hohojia886.pixeltweaks.hooks.`interface`

import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.view.View
import androidx.test.core.app.ApplicationProvider
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BatteryPercentHookTest {

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
        val clazz = BatteryPercentHook::class.java
        val instance = clazz.getField("INSTANCE").get(null)
        val fields = clazz.declaredFields
        for (field in fields) {
            field.isAccessible = true
            when (field.name) {
                "isEnabled" -> field.set(instance, false)
            }
        }
    }

    @Test
    fun testHandleBroadcastSyncSettings() {
        val intent = Intent(IpcManager.ACTION_SETTINGS_SYNC).apply {
            putExtra(PreferenceKeys.ENABLE_STATUSBAR_BATTERY_PERCENT, true)
        }

        val clazz = BatteryPercentHook::class.java
        val instance = clazz.getField("INSTANCE").get(null)
        val handleMethod = clazz.getDeclaredMethod("handleBroadcast", Intent::class.java, android.content.Context::class.java).apply { isAccessible = true }
        handleMethod.invoke(instance, intent, null)

        val enabledField = clazz.getDeclaredField("isEnabled").apply { isAccessible = true }
        assertTrue(enabledField.get(instance) as Boolean)
    }

    @Test
    fun testStockBatteryViewTracking() {
        val clazz = BatteryPercentHook::class.java
        val instance = clazz.getField("INSTANCE").get(null)
        
        val markMethod = clazz.getDeclaredMethod("markAsStockBatteryView", View::class.java).apply { isAccessible = true }
        val checkMethod = clazz.getDeclaredMethod("isStockBatteryView", View::class.java).apply { isAccessible = true }

        val testView = View(ApplicationProvider.getApplicationContext())

        assertFalse(checkMethod.invoke(instance, testView) as Boolean)

        markMethod.invoke(instance, testView)

        assertTrue(checkMethod.invoke(instance, testView) as Boolean)
    }
}
