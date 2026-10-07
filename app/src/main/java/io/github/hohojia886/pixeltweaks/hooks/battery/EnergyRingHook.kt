@file:Suppress("DiscouragedApi", "InternalInsetResource")

package io.github.hohojia886.pixeltweaks.hooks.battery

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.BatteryManager
import android.os.PowerManager
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import io.github.hohojia886.pixeltweaks.utils.BatteryColorManager
import io.github.hohojia886.pixeltweaks.utils.IpcDispatcher
import io.github.hohojia886.pixeltweaks.utils.IpcManager
import io.github.hohojia886.pixeltweaks.utils.Logger
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys
import io.github.libxposed.api.XposedModule

/**
 * EnergyRingHook: Renders a tight, dynamic battery energy ring around the front camera cutout.
 * Features PHDP-style cutoutPath & minOf geometric radius algorithms, real-time UI fine-tuning sliders,
 * status-bar-bounded overlay params (prevents PackageInstaller tapjacking/app update blocks),
 * screen-off lifecycle power-saving, "Only While Charging" toggle, system Power Save mode support, and 2-minute 30FPS LTPO-optimized rotation.
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

    fun hook(module: XposedModule, classLoader: ClassLoader, packageName: String) {
        processPackageName = packageName
        syncSettings(module, classLoader)

        // Subscribe to process-level IPC broadcasts via IpcDispatcher
        IpcDispatcher.addListener { intent ->
            handleBroadcast(intent)
        }

        // Setup overlay view and battery/screen receivers when Context is ready
        IpcDispatcher.initializeOnce(module, classLoader) { app ->
            registerBatteryAndScreenReceivers(app)
            app.mainExecutor.execute {
                setupOverlayView(app)
            }
        }
    }

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

    private fun handleBroadcast(intent: Intent) {
        when (intent.action) {
            IpcManager.ACTION_SETTINGS_SYNC -> {
                isEnabled = intent.getBooleanExtra(PreferenceKeys.ENABLE_CAMERA_ENERGY_RING, false)
                ringOnlyCharging = intent.getBooleanExtra(PreferenceKeys.RING_ONLY_CHARGING, false)
                ringRotation = intent.getBooleanExtra(PreferenceKeys.ENABLE_RING_ROTATION, false)
                ringRadiusOffset = intent.getFloatExtra(PreferenceKeys.RING_RADIUS_OFFSET, 0f)
                ringStrokeWidth = intent.getFloatExtra(PreferenceKeys.RING_STROKE_WIDTH, 2.0f)
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
                        ringRadiusOffset = intent.getFloatExtra(PreferenceKeys.EXTRA_VALUE, 0f)
                        Logger.i(TAG, "Sync", "Setting [ring_radius_offset] updated to $ringRadiusOffset")
                        updateOverlayConfig()
                    }
                    PreferenceKeys.RING_STROKE_WIDTH -> {
                        ringStrokeWidth = intent.getFloatExtra(PreferenceKeys.EXTRA_VALUE, 2.0f)
                        Logger.i(TAG, "Sync", "Setting [ring_stroke_width] updated to $ringStrokeWidth")
                        updateOverlayConfig()
                    }
                }
            }
        }
    }

    private fun registerBatteryAndScreenReceivers(context: Context) {
        runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            isPowerSaveMode = pm?.isPowerSaveMode ?: false

            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            }
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    when (intent.action) {
                        Intent.ACTION_BATTERY_CHANGED -> {
                            val rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                            if (rawLevel >= 0 && scale > 0) {
                                batteryLevel = (rawLevel * 100) / scale
                            }
                            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
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
                        }
                    }
                }
            }
            context.registerReceiver(receiver, filter)
        }.onFailure { e ->
            Logger.e(TAG, "Error", "Failed to register battery, screen, and power save receivers", e)
        }
    }

    private fun getStatusBarHeight(context: Context): Int {
        val resId = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        val heightFromRes = if (resId > 0) context.resources.getDimensionPixelSize(resId) else 0
        val density = context.resources.displayMetrics.density
        return if (heightFromRes > 0) heightFromRes else (density * 48f).toInt()
    }

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
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }

            wm.addView(view, params)
            view.updateConfig(isEnabled, isScreenOn, batteryLevel, isCharging, isPowerSaveMode, ringOnlyCharging, ringRotation, ringRadiusOffset, ringStrokeWidth)
            Logger.i(TAG, "Success", "EnergyRing overlay view attached successfully")
        }.onFailure { e ->
            Logger.e(TAG, "Error", "Failed to setup EnergyRing overlay view", e)
        }
    }

    private fun updateOverlayConfig() {
        overlayView?.post {
            overlayView?.updateConfig(isEnabled, isScreenOn, batteryLevel, isCharging, isPowerSaveMode, ringOnlyCharging, ringRotation, ringRadiusOffset, ringStrokeWidth)
        }
    }

    class EnergyRingView(context: Context) : View(context) {

        private var isRingEnabled = false
        private var isScreenActive = true
        private var currentLevel = 100
        private var isChargingState = false
        private var isPowerSaveState = false
        private var onlyCharging = false
        private var isRotationEnabled = false
        private var radiusOffsetDp = 0f
        private var strokeWidthDp = 2.0f

        private var animHue = 0f
        private var pulseAnimator: ValueAnimator? = null
        private var rotationAnimator: ValueAnimator? = null
        private var lastRotationInvalidateTime = 0L

        private var centerX = -1f
        private var centerY = -1f
        private var baseRadius = -1f

        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = "#33FFFFFF".toColorInt()
            strokeCap = Paint.Cap.ROUND
        }

        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = "#00E676".toColorInt()
            strokeCap = Paint.Cap.ROUND
        }

        private val arcBounds = RectF()

        private val density: Float
            get() = resources.displayMetrics.density

        fun updateConfig(
            enabled: Boolean,
            screenOn: Boolean,
            level: Int,
            charging: Boolean,
            powerSave: Boolean,
            onlyCharge: Boolean,
            rotation: Boolean,
            offsetDp: Float,
            strokeDp: Float
        ) {
            isRingEnabled = enabled
            isScreenActive = screenOn
            currentLevel = level
            isChargingState = charging
            isPowerSaveState = powerSave
            onlyCharging = onlyCharge
            isRotationEnabled = rotation
            radiusOffsetDp = offsetDp
            strokeWidthDp = strokeDp

            val shouldBeVisible = isRingEnabled && isScreenActive && (!onlyCharging || isChargingState)
            visibility = if (shouldBeVisible) VISIBLE else GONE

            if (shouldBeVisible) {
                if (isChargingState) {
                    startPulseAnimation()
                } else {
                    stopPulseAnimation()
                }

                if (isRotationEnabled) {
                    startRotationAnimation()
                } else {
                    stopRotationAnimation()
                }
            } else {
                stopPulseAnimation()
                stopRotationAnimation()
            }

            requestLayout()
            invalidate()
        }

        private var lastPulseInvalidateTime = 0L

        private fun startPulseAnimation() {
            if (pulseAnimator != null) return
            pulseAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 3000L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastPulseInvalidateTime >= 33L) { // Throttle to 30 FPS max (33ms) for LTPO 30Hz VRR
                        lastPulseInvalidateTime = now
                        animHue = it.animatedValue as Float
                        invalidate()
                    }
                }
                start()
            }
        }

        private fun stopPulseAnimation() {
            pulseAnimator?.cancel()
            pulseAnimator = null
        }

        private fun startRotationAnimation() {
            if (rotationAnimator != null) return
            rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 60000L // 60s (1 minute) per full revolution
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastRotationInvalidateTime >= 33L) { // Throttle invalidation to 30 FPS max (33ms) for LTPO 30Hz VRR
                        lastRotationInvalidateTime = now
                        invalidate()
                    }
                }
                start()
            }
        }

        private fun stopRotationAnimation() {
            rotationAnimator?.cancel()
            rotationAnimator = null
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            stopPulseAnimation()
            stopRotationAnimation()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!isRingEnabled || !isScreenActive) return

            val cutout = rootWindowInsets?.displayCutout
            if (cutout == null || cutout.boundingRects.isEmpty()) return

            val rect = cutout.boundingRects[0]
            centerX = rect.centerX().toFloat()
            centerY = rect.centerY().toFloat()
            baseRadius = (minOf(rect.width(), rect.height()) / 2f)

            val strokePx = strokeWidthDp * density
            val radiusPx = baseRadius + (radiusOffsetDp * density)

            trackPaint.strokeWidth = strokePx
            ringPaint.strokeWidth = strokePx

            arcBounds.set(
                centerX - radiusPx,
                centerY - radiusPx,
                centerX + radiusPx,
                centerY + radiusPx
            )

            // 1. Draw Background Track Arc
            canvas.drawArc(arcBounds, 0f, 360f, false, trackPaint)

            // 2. Determine Color
            val ringColor = BatteryColorManager.getRingColor(isChargingState, isPowerSaveState, currentLevel, animHue)

            ringPaint.color = ringColor

            // 3. Calculate 1-Minute Clock Rotation Angle (60s = 360°)
            val rotationProgress = (System.currentTimeMillis() % 60000L) / 60000f
            val rotationAngle = rotationProgress * 360f
            val startAngle = -90f + if (isRotationEnabled) rotationAngle else 0f
            val sweepAngle = 360f * (currentLevel / 100f)

            // Draw Active Energy Ring Arc (Clean, smooth 1-minute rotation)
            canvas.drawArc(arcBounds, startAngle, sweepAngle, false, ringPaint)
        }
    }
}
