package io.github.hohojia886.pixeltweaks.utils

import android.content.Intent
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LoggerTest {

    @Before
    fun setUp() {
        Logger.isMasterEnabled = false
        Logger.logSecurity = true
        Logger.logInterface = true
        Logger.logGestures = true
        Logger.logQuickSettings = true
    }

    @Test
    fun testIsSubEnabledMapping() {
        assertTrue(Logger.isSubEnabled("Security"))
        assertTrue(Logger.isSubEnabled("EasyUnlock"))
        assertTrue(Logger.isSubEnabled("Screenshot"))
        assertTrue(Logger.isSubEnabled("HideVpn"))

        assertTrue(Logger.isSubEnabled("ClearAll"))
        assertTrue(Logger.isSubEnabled("Traffic"))
        assertTrue(Logger.isSubEnabled("Battery"))
        assertTrue(Logger.isSubEnabled("EnergyRing"))

        assertTrue(Logger.isSubEnabled("DT2S"))
        assertTrue(Logger.isSubEnabled("QuickSettings"))

        assertTrue(Logger.isSubEnabled("Hook"))
        assertTrue(Logger.isSubEnabled("UnknownTag"))

        Logger.logSecurity = false
        assertFalse(Logger.isSubEnabled("Security"))
        assertFalse(Logger.isSubEnabled("EasyUnlock"))
        assertFalse(Logger.isSubEnabled("Screenshot"))
        assertFalse(Logger.isSubEnabled("HideVpn"))

        Logger.logInterface = false
        assertFalse(Logger.isSubEnabled("ClearAll"))
        assertFalse(Logger.isSubEnabled("Traffic"))
        assertFalse(Logger.isSubEnabled("Battery"))
        assertFalse(Logger.isSubEnabled("EnergyRing"))

        Logger.logGestures = false
        assertFalse(Logger.isSubEnabled("DT2S"))

        Logger.logQuickSettings = false
        assertFalse(Logger.isSubEnabled("QuickSettings"))
    }

    @Test
    fun testHandleBroadcastSyncSettings() {
        val intent = Intent("io.github.hohojia886.pixeltweaks.SETTINGS_SYNC").apply {
            putExtra(PreferenceKeys.ENABLE_MASTER_LOG, true)
            putExtra(PreferenceKeys.LOG_SECURITY, false)
            putExtra(PreferenceKeys.LOG_INTERFACE, false)
            putExtra(PreferenceKeys.LOG_GESTURES, false)
            putExtra(PreferenceKeys.LOG_QUICK_SETTINGS, false)
        }

        Logger.handleBroadcast(intent)

        assertTrue(Logger.isMasterEnabled)
        assertFalse(Logger.logSecurity)
        assertFalse(Logger.logInterface)
        assertFalse(Logger.logGestures)
        assertFalse(Logger.logQuickSettings)
    }

    @Test
    fun testHandleBroadcastSettingChanged() {
        val intent = Intent("io.github.hohojia886.pixeltweaks.SETTING_CHANGED").apply {
            putExtra(PreferenceKeys.EXTRA_KEY, PreferenceKeys.ENABLE_MASTER_LOG)
            putExtra(PreferenceKeys.EXTRA_VALUE, true)
        }

        Logger.handleBroadcast(intent)

        assertTrue(Logger.isMasterEnabled)
    }

    @Test
    fun testMockLoggerCapturedMessages() {
        var lastTag = ""
        var lastMsg = ""

        val testLogger = object : Logger.Logger {
            override fun v(tag: String, msg: String) { lastTag = tag; lastMsg = msg }
            override fun d(tag: String, msg: String) { lastTag = tag; lastMsg = msg }
            override fun i(tag: String, msg: String) { lastTag = tag; lastMsg = msg }
            override fun w(tag: String, msg: String) { lastTag = tag; lastMsg = msg }
            override fun e(tag: String, msg: String, tr: Throwable?) { lastTag = tag; lastMsg = msg }
        }

        Logger.logger = testLogger
        Logger.isMasterEnabled = true

        Logger.i("Security", "Active", "Bypassed package check")
        assertEquals("PXTK_Security", lastTag)
        assertEquals("[Active] Bypassed package check", lastMsg)

        Logger.e("Hook", "Error", "Hook initialization failed")
        assertEquals("PXTK_Hook", lastTag)
        assertEquals("[Error] Hook initialization failed", lastMsg)
    }
}
