@file:Suppress("DiscouragedApi", "InternalInsetResource")

package io.github.hohojia886.pixeltweaks.hooks.battery

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.PowerManager
import android.view.Gravity
import android.view.WindowManager
import androidx.core.net.toUri
import io.github.hohojia886.pixeltweaks.utils.IpcDispatcher
import io.github.hohojia886.pixeltweaks.utils.IpcManager
import io.github.hohojia886.pixeltweaks.utils.Logger
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys
import io.github.hohojia886.pixeltweaks.utils.getFlexibleFloatExtra
import io.github.libxposed.api.XposedModule

/**
 * Renders a customizable battery energy ring around the device's front camera cutout in SystemUI.
 *
 * Automatically detects DisplayCutout geometry and draws an animated arc indicating battery percentage,
 * charging state (rainbow pulse), low-battery alert (3s double pulse per minute), 3-second unlock glow (1.3x surge + 5dp aura),
 * power save mode, and smooth 1-minute rotation.
 */
object EnergyRingHook {

    private const val TAG = "EnergyRing"
    @Volatile private var isEnabled = false
    @Volatile private var ringOnlyCharging = false
    @Volatile private var ringRotation = false
    @Volatile private var ringRadiusOffset = 0f
    @Volatile private var ringStrokeWidth = 2.0f
    @Volatile private var processPackageName: String? = null

    @Volatile private var batteryLevel = 100
    @Volatile private var isCharging = false
    @Volatile private var isPowerSaveMode = false
    @Volatile private var isScreenOn = true

    private var overlayView: EnergyRingView? = null
    private var windowManager: WindowManager? = null

    /**
     * Entry point for initializing the EnergyRing hook inside SystemUI.
     *
     * @param module Active LSPosed [XposedModule] instance.
     * @param classLoader Target process ClassLoader.
     * @param packageName Target process package name string.
     */
    fun hook(module: XposedModule, classLoader: ClassLoader, packageName: String) {
        processPackageName = packageName
        syncSettings(module, classLoader)

        IpcDispatcher.addListener { intent ->
            handleBroadcast(intent)
        }

        IpcDispatcher.initializeOnce(module, classLoader) { app ->
            registerBatteryAndScreenReceivers(app)
            app.mainExecutor.execute {
                setupOverlayView(app)
            }
        }
    }

    /**
     * Synchronizes ring settings from device or credential encrypted storage.
     * Loads preferences and updates overlay view configuration properties.
     */
    private fun syncSettings(module: XposedModule, classLoader: ClassLoader) {
        val loadedFromDe = runCatching {
            val ctx = IpcManager.getSafeContext(classLoader, processPackageName) ?: IpcManager.getSystemContext(classLoader) ?: return@runCatching false
            val uri = "content://io.github.hohojia886.pixeltweaks".toUri()
            val bundle = ctx.contentResolver.call(uri, "get", null, null) ?: return@runCatching false
            isEnabled = bundle.getBoolean(PreferenceKeys.ENABLE_CAMERA_ENERGY_RING, false)
            ringOnlyCharging = bundle.getBoolean(PreferenceKeys.RING_ONLY_CHARGING, false)
            ringRotation = bundle.getBoolean(PreferenceKeys.ENABLE_RING_ROTATION, false)
            ringRadiusOffset = bundle.getFloat(PreferenceKeys.RING_RADIUS_OFFSET, 0f)
            ringStrokeWidth = bundle.getFloat(PreferenceKeys.RING_STROKE_WIDTH, 2.0f)
            Logger.i(TAG, "Sync", "Settings loaded from DE: enabled=$isEnabled, onlyCharging=$ringOnlyCharging, rotation=$ringRotation")
            updateOverlayConfig()
            true
        }.getOrDefault(false)

        if (!loadedFromDe) {
            runCatching {
                val prefs = module.getRemotePreferences(IpcManager.PREF_NAME)
                isEnabled = prefs.getBoolean(PreferenceKeys.ENABLE_CAMERA_ENERGY_RING, false)
                ringOnlyCharging = prefs.getBoolean(PreferenceKeys.RING_ONLY_CHARGING, false)
                ringRotation = prefs.getBoolean(PreferenceKeys.ENABLE_RING_ROTATION, false)
                ringRadiusOffset = prefs.getFloat(PreferenceKeys.RING_RADIUS_OFFSET, 0f)
                ringStrokeWidth = prefs.getFloat(PreferenceKeys.RING_STROKE_WIDTH, 2.0f)
                Logger.i(TAG, "Sync", "Settings loaded from CE (fallback): enabled=$isEnabled")
                updateOverlayConfig()
            }.onFailure { e ->
                Logger.e(TAG, "Error", "Failed to load settings via RemotePrefProvider", e)
            }
        }
    }

    /**
     * Handles IPC broadcasts to update overlay configuration dynamically.
     * Processes full sync and setting changed intents to refresh view.
     */
    private fun handleBroadcast(intent: Intent) {
        when (intent.action) {
            IpcManager.ACTION_SETTINGS_SYNC -> {
                isEnabled = intent.getBooleanExtra(PreferenceKeys.ENABLE_CAMERA_ENERGY_RING, false)
                ringOnlyCharging = intent.getBooleanExtra(PreferenceKeys.RING_ONLY_CHARGING, false)
                ringRotation = intent.getBooleanExtra(PreferenceKeys.ENABLE_RING_ROTATION, false)
                ringRadiusOffset = intent.getFlexibleFloatExtra(PreferenceKeys.RING_RADIUS_OFFSET, 0f)
                ringStrokeWidth = intent.getFlexibleFloatExtra(PreferenceKeys.RING_STROKE_WIDTH, 2.0f)
                Logger.i(TAG, "Sync", "Full sync received: enabled=$isEnabled")
                updateOverlayConfig()
            }
            IpcManager.ACTION_SETTING_CHANGED -> {
                val key = intent.getStringExtra(PreferenceKeys.EXTRA_KEY)
                when (key) {
                    PreferenceKeys.ENABLE_CAMERA_ENERGY_RING -> {
                        isEnabled = intent.getBooleanExtra(PreferenceKeys.EXTRA_VALUE, false)
                        Logger.i(TAG, "Sync", "Setting [enable_camera_energy_ring] updated to $isEnabled")
                        updateOverlayConfig()
                    }
                    PreferenceKeys.RING_ONLY_CHARGING -> {
                        ringOnlyCharging = intent.getBooleanExtra(PreferenceKeys.EXTRA_VALUE, false)
                        Logger.i(TAG, "Sync", "Setting [ring_only_charging] updated to $ringOnlyCharging")
                        updateOverlayConfig()
                    }
                    PreferenceKeys.ENABLE_RING_ROTATION -> {
                        ringRotation = intent.getBooleanExtra(PreferenceKeys.EXTRA_VALUE, false)
                        Logger.i(TAG, "Sync", "Setting [enable_ring_rotation] updated to $ringRotation")
                        updateOverlayConfig()
                    }
                    PreferenceKeys.RING_RADIUS_OFFSET -> {
                        ringRadiusOffset = intent.getFlexibleFloatExtra(PreferenceKeys.EXTRA_VALUE, 0f)
                        Logger.i(TAG, "Sync", "Setting [ring_radius_offset] updated to $ringRadiusOffset")
                        updateOverlayConfig()
                    }
                    PreferenceKeys.RING_STROKE_WIDTH -> {
                        ringStrokeWidth = intent.getFlexibleFloatExtra(PreferenceKeys.EXTRA_VALUE, 2.0f)
                        Logger.i(TAG, "Sync", "Setting [ring_stroke_width] updated to $ringStrokeWidth")
                        updateOverlayConfig()
                    }
                }
            }
        }
    }

    /**
     * Registers system broadcast receivers for battery, screen, and power save.
     * Updates overlay view configuration upon state change intents.
     */
    private fun registerBatteryAndScreenReceivers(context: Context) {
        runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            isPowerSaveMode = pm?.isPowerSaveMode ?: false

            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            }
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    when (intent.action) {
                        Intent.ACTION_BATTERY_CHANGED -> {
                            val rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                            if ((rawLevel >= 0) && (scale > 0)) {
                                batteryLevel = (rawLevel * 100) / scale
                            }
                            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                            isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING) || (status == BatteryManager.BATTERY_STATUS_FULL)
                            val pManager = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
                            isPowerSaveMode = pManager?.isPowerSaveMode ?: false
                            overlayView?.updateConfig(isEnabled, isScreenOn, batteryLevel, isCharging, isPowerSaveMode, ringOnlyCharging, ringRotation, ringRadiusOffset, ringStrokeWidth)
                        }
                        PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                            val pManager = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
                            isPowerSaveMode = pManager?.isPowerSaveMode ?: false
                            overlayView?.updateConfig(isEnabled, isScreenOn, batteryLevel, isCharging, isPowerSaveMode, ringOnlyCharging, ringRotation, ringRadiusOffset, ringStrokeWidth)
                        }
                        Intent.ACTION_SCREEN_OFF -> {
                            isScreenOn = false
                            overlayView?.updateConfig(isEnabled, isScreenOn, batteryLevel, isCharging, isPowerSaveMode, ringOnlyCharging, ringRotation, ringRadiusOffset, ringStrokeWidth)
                        }
                        Intent.ACTION_SCREEN_ON -> {
                            isScreenOn = true
                            overlayView?.updateConfig(isEnabled, isScreenOn, batteryLevel, isCharging, isPowerSaveMode, ringOnlyCharging, ringRotation, ringRadiusOffset, ringStrokeWidth)
                            overlayView?.triggerUnlockGlowAnimation()
                        }
                        Intent.ACTION_USER_PRESENT -> {
                            overlayView?.triggerUnlockGlowAnimation()
                        }
                    }
                }
            }
            context.registerReceiver(receiver, filter)
        }.onFailure { e ->
            Logger.e(TAG, "Error", "Failed to register battery, screen, and power save receivers", e)
        }
    }

    /**
     * Retrieves status bar height dynamically from resources or density.
     * Evaluates status_bar_height dimension or falls back to 48dp.
     */
    private fun getStatusBarHeight(context: Context): Int {
        val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val heightFromRes = if (resId > 0) context.resources.getDimensionPixelSize(resId) else 0
        val density = context.resources.displayMetrics.density
        return if (heightFromRes > 0) heightFromRes else (density * 48f).toInt()
    }

    /**
     * Sets up the WindowManager overlay for the EnergyRingView.
     * Instantiates view with TYPE_SECURE_SYSTEM_OVERLAY params.
     */
    @SuppressLint("WrongConstant")
    private fun setupOverlayView(context: Context) {
        runCatching {
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            val wm = windowManager ?: return@runCatching

            if (overlayView != null) return@runCatching

            val view = EnergyRingView(context)
            overlayView = view

            val statusBarHeight = getStatusBarHeight(context)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                statusBarHeight,
                2015,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }

            wm.addView(view, params)
            view.updateConfig(isEnabled, isScreenOn, batteryLevel, isCharging, isPowerSaveMode, ringOnlyCharging, ringRotation, ringRadiusOffset, ringStrokeWidth)
            Logger.i(TAG, "Success", "EnergyRing overlay view attached successfully")
        }.onFailure { e ->
            Logger.e(TAG, "Error", "Failed to setup EnergyRing overlay view", e)
        }
    }

    /**
     * Triggers a UI thread update for the overlay's configuration.
     * Posts updateConfig call to overlay view handler queue.
     */
    private fun updateOverlayConfig() {
        overlayView?.post {
            overlayView?.updateConfig(isEnabled, isScreenOn, batteryLevel, isCharging, isPowerSaveMode, ringOnlyCharging, ringRotation, ringRadiusOffset, ringStrokeWidth)
        }
    }
}
