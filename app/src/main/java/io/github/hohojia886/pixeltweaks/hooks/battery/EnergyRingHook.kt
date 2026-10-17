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

    /**
     * Custom view class responsible for drawing ring around camera cutout.
     * Calculates cutout bounds and renders background track and active arc.
     */
    class EnergyRingView(context: Context) : View(context) {

        init {
            isClickable = false
            isFocusable = false
            isLongClickable = false
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }

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
        private var unlockGlowAnimator: ValueAnimator? = null
        private var unlockGlowAlpha = 0f
        private var lastUnlockGlowInvalidateTime = 0L
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

        private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        private val arcBounds = RectF()

        private val density: Float
            get() = resources.displayMetrics.density

        /**
         * Updates visual and operational configuration flags for the ring.
         * Evaluates visibility state and manages pulse and rotation animations.
         */
        fun updateConfig(
            enabled: Boolean,
            screenOn: Boolean,
            level: Int,
            charging: Boolean,
            powerSave: Boolean,
            onlyCharge: Boolean,
            rotation: Boolean,
            offsetDp: Float,
            strokeDp: Float,
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

        /**
         * Starts the hue pulse animation during charging state.
         * Runs ValueAnimator throttled to 30 FPS for LTPO VRR power savings.
         */
        private fun startPulseAnimation() {
            if (pulseAnimator != null) return
            pulseAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 3000L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastPulseInvalidateTime >= 33L) {
                        lastPulseInvalidateTime = now
                        animHue = it.animatedValue as Float
                        invalidate()
                    }
                }
                start()
            }
        }

        /**
         * Stops the hue pulse animation and clears the animator.
         * Cancels pulseAnimator instance and resets reference to null.
         */
        private fun stopPulseAnimation() {
            pulseAnimator?.cancel()
            pulseAnimator = null
        }

        /**
         * Starts the 1-minute full revolution rotation animation.
         * Runs 60s ValueAnimator throttled to 30 FPS for LTPO VRR power savings.
         */
        private fun startRotationAnimation() {
            if (rotationAnimator != null) return
            rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 60000L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastRotationInvalidateTime >= 33L) {
                        lastRotationInvalidateTime = now
                        invalidate()
                    }
                }
                start()
            }
        }

        /**
         * Triggers a 3-second temporary unlock animation featuring stroke surge (1.3x) and a 5dp outer neon glow aura.
         * Runs a 3000ms ValueAnimator fading out unlockGlowAlpha from 1.0f to 0.0f.
         */
        fun triggerUnlockGlowAnimation() {
            if (!isRingEnabled || !isScreenActive) return
            unlockGlowAnimator?.cancel()
            unlockGlowAnimator = ValueAnimator.ofFloat(1.0f, 0.0f).apply {
                duration = 3000L
                interpolator = LinearInterpolator()
                addUpdateListener {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastUnlockGlowInvalidateTime >= 33L) {
                        lastUnlockGlowInvalidateTime = now
                        unlockGlowAlpha = it.animatedValue as Float
                        invalidate()
                    }
                }
                start()
            }
        }

        /**
         * Stops the rotation animation and clears the animator.
         * Cancels rotationAnimator instance and resets reference to null.
         */
        private fun stopRotationAnimation() {
            rotationAnimator?.cancel()
            rotationAnimator = null
        }

        /**
         * Stops the unlock glow animation and resets glow alpha.
         * Cancels unlockGlowAnimator instance and resets reference to null.
         */
        private fun stopUnlockGlowAnimation() {
            unlockGlowAnimator?.cancel()
            unlockGlowAnimator = null
            unlockGlowAlpha = 0f
        }

        /**
         * Cleans up running animations when the view is detached.
         * Stops pulse and rotation animators to prevent memory leaks.
         */
        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            stopPulseAnimation()
            stopRotationAnimation()
            stopUnlockGlowAnimation()
        }

        /**
         * Main drawing routine calculating cutout bounds and rendering arc.
         * Calculates cutout bounds, determines color, and draws active arc.
         */
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!isRingEnabled || !isScreenActive) return

            val cutout = rootWindowInsets?.displayCutout ?: context.display.cutout
            if (cutout == null || cutout.boundingRects.isEmpty()) return

            val rect = cutout.boundingRects[0]
            centerX = rect.centerX().toFloat()
            centerY = rect.centerY().toFloat()
            baseRadius = (minOf(rect.width(), rect.height()) / 2f)

            var strokePx = strokeWidthDp * density
            if (unlockGlowAlpha > 0f) {
                strokePx *= (1f + 0.3f * unlockGlowAlpha)
            }

            val radiusPx = baseRadius + (radiusOffsetDp * density)

            trackPaint.strokeWidth = strokeWidthDp * density
            ringPaint.strokeWidth = strokePx

            arcBounds.set(
                centerX - radiusPx,
                centerY - radiusPx,
                centerX + radiusPx,
                centerY + radiusPx,
            )

            canvas.drawArc(arcBounds, 0f, 360f, false, trackPaint)

            var ringColor = BatteryColorManager.getRingColor(isChargingState, isPowerSaveState, currentLevel, animHue)

            // Low battery pulsing alert: when level <= 10% and not charging/power save, pulse twice (3s = 1.5s x 2) every 60s
            if (currentLevel <= 10 && !isChargingState && !isPowerSaveState) {
                val nowMs = System.currentTimeMillis()
                val msInMinute = nowMs % 60000L
                if (msInMinute < 3000L) {
                    val pulseProgress = (msInMinute % 1500L) / 1500f
                    val pulseFactor = 0.4f + 0.6f * kotlin.math.abs(kotlin.math.sin(pulseProgress * Math.PI)).toFloat()
                    val baseAlpha = android.graphics.Color.alpha(ringColor)
                    val finalAlpha = (baseAlpha * pulseFactor).toInt().coerceIn(0, 255)
                    ringColor = android.graphics.Color.argb(
                        finalAlpha,
                        android.graphics.Color.red(ringColor),
                        android.graphics.Color.green(ringColor),
                        android.graphics.Color.blue(ringColor)
                    )
                    postInvalidateDelayed(33L)
                } else {
                    val delayToNextMinute = 60000L - msInMinute
                    postInvalidateDelayed(delayToNextMinute.coerceAtLeast(100L))
                }
            }

            val rotationProgress = (System.currentTimeMillis() % 60000L) / 60000f
            val rotationAngle = rotationProgress * 360f
            val startAngle = -90f + if (isRotationEnabled) rotationAngle else 0f
            val sweepAngle = 360f * (currentLevel / 100f)

            // Unlock 3-second glow animation: render secondary outer glow arc (5dp expansion) if unlockGlowAlpha > 0f
            if (unlockGlowAlpha > 0f) {
                glowPaint.strokeWidth = strokePx + (5f * density * unlockGlowAlpha)
                val glowAlphaInt = (160 * unlockGlowAlpha).toInt().coerceIn(0, 255)
                glowPaint.color = android.graphics.Color.argb(
                    glowAlphaInt,
                    android.graphics.Color.red(ringColor),
                    android.graphics.Color.green(ringColor),
                    android.graphics.Color.blue(ringColor)
                )
                canvas.drawArc(arcBounds, startAngle, sweepAngle, false, glowPaint)
            }

            ringPaint.color = ringColor
            canvas.drawArc(arcBounds, startAngle, sweepAngle, false, ringPaint)
        }
    }
}
