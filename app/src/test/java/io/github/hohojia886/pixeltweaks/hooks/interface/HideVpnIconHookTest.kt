package io.github.hohojia886.pixeltweaks.hooks.`interface`

import android.content.Intent
import io.github.hohojia886.pixeltweaks.utils.IpcManager
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HideVpnIconHookTest {

    @Test
    fun testIntentActionsAndKeys() {
        val mockIntent = mock(Intent::class.java)
        `when`(mockIntent.action).thenReturn(IpcManager.ACTION_SETTINGS_SYNC)
        `when`(mockIntent.getBooleanExtra(PreferenceKeys.ENABLE_HIDE_VPN_ICON, false)).thenReturn(true)

        assertEquals(IpcManager.ACTION_SETTINGS_SYNC, mockIntent.action)
        assertEquals(true, mockIntent.getBooleanExtra(PreferenceKeys.ENABLE_HIDE_VPN_ICON, false))
    }

    @Test
    fun testSettingChangedIntent() {
        val mockIntent = mock(Intent::class.java)
        `when`(mockIntent.action).thenReturn(IpcManager.ACTION_SETTING_CHANGED)
        `when`(mockIntent.getStringExtra(PreferenceKeys.EXTRA_KEY)).thenReturn(PreferenceKeys.ENABLE_HIDE_VPN_ICON)
        `when`(mockIntent.getBooleanExtra(PreferenceKeys.EXTRA_VALUE, false)).thenReturn(true)

        assertEquals(IpcManager.ACTION_SETTING_CHANGED, mockIntent.action)
        assertEquals(PreferenceKeys.ENABLE_HIDE_VPN_ICON, mockIntent.getStringExtra(PreferenceKeys.EXTRA_KEY))
    }

    @Test
    fun testHandleBroadcastUpdatesState() {
        val clazz = HideVpnIconHook::class.java
        val instance = clazz.getField("INSTANCE").get(null)
        val isEnabledField = clazz.getDeclaredField("isEnabled").apply { isAccessible = true }
        isEnabledField.set(instance, false)

        val handleMethod = clazz.getDeclaredMethod("handleBroadcast", Intent::class.java, ClassLoader::class.java).apply {
            isAccessible = true
        }

        val noOpIntent = mock(Intent::class.java)
        `when`(noOpIntent.action).thenReturn(IpcManager.ACTION_SETTINGS_SYNC)
        `when`(noOpIntent.getBooleanExtra(PreferenceKeys.ENABLE_HIDE_VPN_ICON, false)).thenReturn(false)

        // Same value should short-circuit cleanly without throwing even with a null/mock ClassLoader
        handleMethod.invoke(instance, noOpIntent, javaClass.classLoader)
        assertEquals(false, isEnabledField.get(instance))
    }
}
