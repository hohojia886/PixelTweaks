package io.github.hohojia886.pixeltweaks.hooks

import io.github.hohojia886.pixeltweaks.hooks.interaction.EasyUnlockHook
import io.github.hohojia886.pixeltweaks.hooks.system.PackageManagerHook
import io.github.hohojia886.pixeltweaks.hooks.interaction.DoubleTapToSleepHook
import io.github.hohojia886.pixeltweaks.hooks.system.QuickSettingsHook
import io.github.libxposed.api.XposedModule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.os.IBinder

class HookWiringTest {

    @Test
    fun testEasyUnlockHookTargetsExpectedClasses() {
        val module = mock(XposedModule::class.java)
        val classLoader = mock(ClassLoader::class.java)
        val prefs = mock(SharedPreferences::class.java)
        val appInfo = ApplicationInfo().apply { uid = 1000 }

        whenever(module.getRemotePreferences(any())).thenReturn(prefs)
        whenever(module.getModuleApplicationInfo()).thenReturn(appInfo)
        
        whenever(classLoader.loadClass(any())).thenReturn(Any::class.java)

        EasyUnlockHook.hook(module, classLoader, "com.android.systemui")
        
        verify(classLoader, atLeastOnce()).loadClass(any())
    }

    @Test
    fun testPackageManagerHookTargetsExpectedClasses() {
        val module = mock(XposedModule::class.java)
        val classLoader = mock(ClassLoader::class.java)
        val prefs = mock(SharedPreferences::class.java)
        val appInfo = ApplicationInfo().apply { uid = 1000 }

        whenever(module.getRemotePreferences(any())).thenReturn(prefs)
        whenever(module.getModuleApplicationInfo()).thenReturn(appInfo)
        
        // Mock ServiceManager and binder logic
        val mockClass = Any::class.java
        whenever(classLoader.loadClass(any())).thenReturn(mockClass)

        // PackageManagerHook.hook should not crash and should attempt to load classes
        PackageManagerHook.hook(module, classLoader)
        
        verify(classLoader, atLeastOnce()).loadClass(any())
    }
}
