package io.github.hohojia886.pixeltweaks.hooks.`interface`

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import io.github.hohojia886.pixeltweaks.utils.IpcManager
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys
import io.github.libxposed.api.XposedModule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.lang.ref.WeakReference
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
        whenever(module.moduleApplicationInfo).thenReturn(appInfo)
        whenever(module.getRemotePreferences(IpcManager.PREF_NAME)).thenReturn(prefs)

        resetSingleton()
    }

    private fun resetSingleton() {
        BatteryPercentHook.isEnabled = false
        BatteryPercentHook.isAndroid17CP3A = false
        BatteryPercentHook.viewInjector.pendingPercentVisibilitySync.set(false)
        BatteryPercentHook.viewInjector.trackedPercentTextViews.clear()
    }

    @Test
    fun testHandleBroadcastSyncSettings() {
        val intent = Intent(IpcManager.ACTION_SETTINGS_SYNC).apply {
            putExtra(PreferenceKeys.ENABLE_STATUSBAR_BATTERY_PERCENT, true)
        }

        BatteryPercentHook.handleBroadcast(intent, null)

        assertTrue(BatteryPercentHook.isEnabled)
    }

    @Test
    fun testStockBatteryViewTracking() {
        val testView = View(ApplicationProvider.getApplicationContext())

        assertFalse(BatteryPercentHook.suppressor.isStockBatteryView(testView))

        BatteryPercentHook.suppressor.markAsStockBatteryView(testView)

        assertTrue(BatteryPercentHook.suppressor.isStockBatteryView(testView))
    }

    @Test
    fun testSettingChangedBroadcast() {
        val intent = Intent(IpcManager.ACTION_SETTING_CHANGED).apply {
            putExtra(PreferenceKeys.EXTRA_KEY, PreferenceKeys.ENABLE_STATUSBAR_BATTERY_PERCENT)
            putExtra(PreferenceKeys.EXTRA_VALUE, true)
        }

        BatteryPercentHook.handleBroadcast(intent, null)

        assertTrue(BatteryPercentHook.isEnabled)
    }

    @Test
    fun testNoDuplicateTextViewInjection() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val root = LinearLayout(activity)
        activity.setContentView(root)

        BatteryPercentHook.viewInjector.injectTextViewToFarRight(root)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        BatteryPercentHook.viewInjector.injectTextViewToFarRight(root)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        var taggedCount = 0
        for (i in 0 until root.childCount) {
            if (root.getChildAt(i).tag == BatteryPercentHook.VIEW_TAG) {
                taggedCount++
            }
        }
        assertTrue(taggedCount == 1, "Expected exactly 1 injected battery percent TextView, got $taggedCount")
    }

    @Test
    fun testRedundantSettingsSyncDoesNotTriggerRecomposition() {
        BatteryPercentHook.composeBridge.initialize(javaClass.classLoader!!)

        val initialVersion = BatteryPercentHook.composeBridge.currentVersion
        val enableIntent = Intent(IpcManager.ACTION_SETTINGS_SYNC).apply {
            putExtra(PreferenceKeys.ENABLE_STATUSBAR_BATTERY_PERCENT, true)
        }

        // First transition false -> true must trigger recomposition
        BatteryPercentHook.handleBroadcast(enableIntent, null)
        val afterFirstSync = BatteryPercentHook.composeBridge.currentVersion
        assertTrue(afterFirstSync == initialVersion + 1, "Expected version to increment once on state change")

        // Redundant syncs with same value must be ignored without triggering extra recompositions
        BatteryPercentHook.handleBroadcast(enableIntent, null)
        BatteryPercentHook.handleBroadcast(enableIntent, null)
        assertTrue(BatteryPercentHook.composeBridge.currentVersion == afterFirstSync, "Expected no recomposition on redundant full syncs")
    }

    @Test
    fun testAtomicSameFrameVisibilitySyncOnCP3A() {
        BatteryPercentHook.composeBridge.initialize(javaClass.classLoader!!)
        BatteryPercentHook.isAndroid17CP3A = true

        val tv = TextView(ApplicationProvider.getApplicationContext()).apply {
            visibility = View.GONE
        }
        BatteryPercentHook.viewInjector.trackedPercentTextViews.add(WeakReference(tv))

        // Toggle ON: TextView stays GONE until Compose frame flush executes
        BatteryPercentHook.isEnabled = true
        BatteryPercentHook.updateViewVisibility()
        assertTrue(tv.visibility == View.GONE, "TextView visibility should wait for Compose recomposition frame")

        // Compose UnifiedBattery hook flushes pending visibility in the same animation frame
        BatteryPercentHook.viewInjector.flushPendingVisibility()
        assertTrue(tv.visibility == View.VISIBLE, "TextView should become VISIBLE atomically on Compose frame flush")

        // Toggle OFF: TextView stays VISIBLE until Compose frame flush executes
        BatteryPercentHook.isEnabled = false
        BatteryPercentHook.updateViewVisibility()
        assertTrue(tv.visibility == View.VISIBLE, "TextView should remain VISIBLE until stock battery recomposes")

        BatteryPercentHook.viewInjector.flushPendingVisibility()
        assertTrue(tv.visibility == View.GONE, "TextView should become GONE atomically on Compose frame flush")
    }
}
