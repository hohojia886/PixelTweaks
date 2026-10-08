package io.github.hohojia886.pixeltweaks.hooks.security

import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import io.github.hohojia886.pixeltweaks.utils.IpcManager
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys
import io.github.libxposed.api.XposedModule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class PackageManagerHookTest {

    private lateinit var module: XposedModule
    private lateinit var classLoader: ClassLoader
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        module = mock(XposedModule::class.java)
        classLoader = mock(ClassLoader::class.java)
        prefs = mock(SharedPreferences::class.java)

        val appInfo = ApplicationInfo().apply { uid = 1000 }
        whenever(module.moduleApplicationInfo).thenReturn(appInfo)
        whenever(module.getRemotePreferences(IpcManager.PREF_NAME)).thenReturn(prefs)

        resetSingleton()
    }

    private fun resetSingleton() {
        val clazz = PackageManagerHook::class.java
        val instance = clazz.getField("INSTANCE")[null]
        val fields = clazz.declaredFields
        for (field in fields) {
            field.isAccessible = true
            if (field.name == "isHooked") field[instance] = false
            if (field.name == "isDowngradeEnabled") field[instance] = false
            if (field.name == "isSignatureBypassEnabled") field[instance] = false
            if (field.name == "downgradeTimestamp") field[instance] = 0L
            if (field.name == "signatureTimestamp") field[instance] = 0L
        }
    }

    @Test
    fun testHookWiring() {
        // Basic wiring test without mocking Class.class
        whenever(classLoader.loadClass(anyString())).thenReturn(Any::class.java)

        // PackageManagerHook.hook should not crash
        runCatching { PackageManagerHook.hook(module, classLoader) }
        
        verify(classLoader, atLeastOnce()).loadClass(anyString())
    }

    @Test
    fun testIsFeatureActive() {
        val clazz = PackageManagerHook::class.java
        val instance = clazz.getField("INSTANCE").get(null)
        val method = clazz.getDeclaredMethod("isFeatureActive", Boolean::class.java, Long::class.java)
        method.isAccessible = true

        val now = System.currentTimeMillis()
        
        // Enabled and recent
        assertTrue(method.invoke(instance, true, now - 1000L) as Boolean)
        
        // Disabled
        assertFalse(method.invoke(instance, false, now - 1000L) as Boolean)
        
        // Timed out (3 minutes = 180000ms)
        assertFalse(method.invoke(instance, true, now - 4 * 60 * 1000L) as Boolean)
    }

    @Test
    fun testSettingsSyncViaBroadcast() {
        whenever(prefs.getBoolean(PreferenceKeys.ALLOW_DOWNGRADE, false)).thenReturn(true)
        whenever(prefs.getLong(PreferenceKeys.DOWNGRADE_TIMESTAMP, 0L)).thenReturn(12345L)
        
        val clazz = PackageManagerHook::class.java
        val instance = clazz.getField("INSTANCE").get(null)
        val refreshMethod = clazz.getDeclaredMethod("refreshSettings", XposedModule::class.java, ClassLoader::class.java)
        refreshMethod.isAccessible = true
        refreshMethod.invoke(instance, module, javaClass.classLoader)
        
        val downgradeField = clazz.getDeclaredField("isDowngradeEnabled")
        downgradeField.isAccessible = true
        assertTrue(downgradeField.get(instance) as Boolean)
        
        val timestampField = clazz.getDeclaredField("downgradeTimestamp")
        timestampField.isAccessible = true
        assertTrue((timestampField.get(instance) as Long) > 0L)
    }
    
    @Test
    fun testTimeoutLogicInActiveProcess() {
        val now = System.currentTimeMillis()
        
        val clazz = PackageManagerHook::class.java
        val instance = clazz.getField("INSTANCE").get(null)
        val method = clazz.getDeclaredMethod("isFeatureActive", Boolean::class.java, Long::class.java)
        method.isAccessible = true

        // Set state to active
        assertTrue(method.invoke(instance, true, now) as Boolean)
        
        // Advanced time check using manual timestamp since Robolectric shadow clock can be tricky
        assertFalse(method.invoke(instance, true, now - 4 * 60 * 1000L) as Boolean)
    }

}
