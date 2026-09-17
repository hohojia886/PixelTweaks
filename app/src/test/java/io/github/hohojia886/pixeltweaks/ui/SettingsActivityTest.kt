package io.github.hohojia886.pixeltweaks.ui

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import io.github.hohojia886.pixeltweaks.utils.IpcManager
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsActivityTest {

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val deContext = context.createDeviceProtectedStorageContext()
        deContext.getSharedPreferences(IpcManager.PREF_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }

    @Test
    fun testSettingsActivityLaunchesAndInitsPreferences() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(activity)
                val deContext = activity.createDeviceProtectedStorageContext()
                val prefs = deContext.getSharedPreferences(IpcManager.PREF_NAME, Context.MODE_PRIVATE)
                
                // Defaults should be initialized on Activity launch
                assertTrue(prefs.contains(PreferenceKeys.ENABLE_NETWORK_TRAFFIC))
                assertTrue(prefs.getBoolean(PreferenceKeys.ENABLE_NETWORK_TRAFFIC, false))
                assertTrue(prefs.getBoolean(PreferenceKeys.ENABLE_EASY_UNLOCK, false))
            }
        }
    }
}
