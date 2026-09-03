package io.github.hohojia886.pixeltweaks.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IpcManagerTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        prefs = context.getSharedPreferences(IpcManager.PREF_NAME, Context.MODE_PRIVATE)
    }

    @Test
    fun testSyncAllSettingsSendsBroadcast() {
        IpcManager.syncAllSettings(context, prefs)
        
        val shadowApp = shadowOf(context as android.app.Application)
        val broadcastIntent = shadowApp.broadcastIntents.last()
        
        assertEquals(IpcManager.ACTION_SETTINGS_SYNC, broadcastIntent.action)
        assertTrue(broadcastIntent.hasExtra(PreferenceKeys.ENABLE_CLEAR_ALL))
    }

    @Test
    fun testSendUpdateBroadcast() {
        val key = PreferenceKeys.ENABLE_EASY_UNLOCK
        val value = true
        IpcManager.sendUpdateBroadcast(context, key, value)
        
        val shadowApp = shadowOf(context as android.app.Application)
        val broadcastIntent = shadowApp.broadcastIntents.last()
        
        assertEquals(IpcManager.ACTION_SETTING_CHANGED, broadcastIntent.action)
        assertEquals(key, broadcastIntent.getStringExtra(PreferenceKeys.EXTRA_KEY))
        assertEquals(value, broadcastIntent.getBooleanExtra(PreferenceKeys.EXTRA_VALUE, false))
    }
}
