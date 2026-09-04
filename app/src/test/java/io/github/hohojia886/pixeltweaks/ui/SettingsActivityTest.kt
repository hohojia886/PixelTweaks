package io.github.hohojia886.pixeltweaks.ui

import android.content.Context
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.android.material.materialswitch.MaterialSwitch
import io.github.hohojia886.pixeltweaks.R
import io.github.hohojia886.pixeltweaks.utils.IpcManager
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsActivityTest {

    @Before
    fun setup() {
        // Clear prefs before each test
        val context = ApplicationProvider.getApplicationContext<Context>()
        val deContext = context.createDeviceProtectedStorageContext()
        deContext.getSharedPreferences(IpcManager.PREF_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }

    @Test
    fun testEasyUnlockSwitchUpdatesPrefs() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val easyUnlockSwitch = activity.findViewById<MaterialSwitch>(R.id.switch_easy_unlock)
                
                // Toggle it ON manually
                easyUnlockSwitch.performClick() 
                
                val deContext = activity.createDeviceProtectedStorageContext()
                val prefs = deContext.getSharedPreferences(IpcManager.PREF_NAME, Context.MODE_PRIVATE)
                
                // Note: performClick() might toggle it to true or false depending on initial state.
                // Since we clear prefs in setup(), initial state should be default (true).
                // So performClick() makes it FALSE.
                // Let's explicitly set it and ensure it's written.
                easyUnlockSwitch.isChecked = false
                easyUnlockSwitch.isChecked = true
                
                assertTrue(prefs.getBoolean(PreferenceKeys.ENABLE_EASY_UNLOCK, false))
            }
        }
    }

    @Test
    fun testNetworkTrafficCardShapeAndVisibilityToggle() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val trafficSwitch = activity.findViewById<MaterialSwitch>(R.id.switch_network_traffic)
                val cardNetworkTraffic = activity.findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_network_traffic)
                val cardInterval = activity.findViewById<android.view.View>(R.id.card_traffic_interval)

                // By default Network Traffic is enabled
                trafficSwitch.isChecked = true
                kotlin.test.assertEquals(android.view.View.VISIBLE, cardInterval.visibility)
                val paramsOn = cardNetworkTraffic.layoutParams as android.widget.LinearLayout.LayoutParams
                assertTrue(paramsOn.bottomMargin > 0)

                // Toggle Network Traffic OFF
                trafficSwitch.isChecked = false
                kotlin.test.assertEquals(android.view.View.GONE, cardInterval.visibility)
                val paramsOff = cardNetworkTraffic.layoutParams as android.widget.LinearLayout.LayoutParams
                kotlin.test.assertEquals(0, paramsOff.bottomMargin)
            }
        }
    }

    @Test
    fun testWarningSpannableTexts() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val allowDowngradeSwitch = activity.findViewById<MaterialSwitch>(R.id.switch_allow_downgrade)
                allowDowngradeSwitch.isChecked = true
                val titleAllowDowngrade = activity.findViewById<TextView>(R.id.title_allow_downgrade)
                val text = titleAllowDowngrade.text
                assertTrue(text is Spanned)
                val spans = text.getSpans(0, text.length, ForegroundColorSpan::class.java)
                assertTrue(spans.isNotEmpty())
                assertTrue(text.toString().contains("s)"))
            }
        }
    }
}
