package io.github.hohojia886.pixeltweaks.hooks.battery

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.net.Uri
import android.os.BatteryManager
import android.view.DisplayCutout
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import io.github.hohojia886.pixeltweaks.utils.IpcManager
import io.github.hohojia886.pixeltweaks.utils.Logger
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys
import io.github.hohojia886.pixeltweaks.utils.hookBefore
import io.github.libxposed.api.XposedModule
import kotlin.math.max
import kotlin.math.min

/**
 * EnergyRingHook: Renders a tight, dynamic battery energy ring around the front camera cutout.
 * Features PHDP-style cutoutPath & minOf geometric radius algorithms, real-time UI fine-tuning sliders,
 * status-bar-bounded overlay params (prevents PackageInstaller tapjacking/app update blocks),
 * screen-off lifecycle power-saving, and "Only While Charging" toggle support.
 */
object EnergyRingHook {

    private const val TAG = "EnergyRing"
    @Volatile private var isEnabled = false
    @Volatile private var ringOnlyCharging = false
    @Volatile private var ringRadiusOffset = 0f
    @Volatile private var ringStrokeWidth = 2.0f
    @Volatile private var processPackageName: String? = null

    @Volatile private var batteryLevel = 100
    @Volatile private var isCharging = false
    @Volatile private var isScreenOn = true

    private var overlayView: EnergyRingView? = null
    private var windowManager: WindowManager? = null

    fun hook(module: XposedModule, classLoader: ClassLoader, packageName: String) {
        processPackageName = packageName
        syncSettings(module, classLoader)

        runCatching {
            val appClass = classLoader.loadClass("android.app.Application")
            module.hookBefore(appClass.getDeclaredMethod("onCreate")) { chain ->
                val app = chain.thisObject as? Context
                if (app != null) {
                    IpcManager.registerSecureReceiver(app, module.getModuleApplicationInfo().uid) { intent ->
                        handleBroadcast(intent)
                    }

                    // Register Battery & Screen Lifecycle Receivers
                    registerBatteryAndScreenReceivers(app)

                    // Attach Overlay View on Main Thread
                    app.mainExecutor.execute {
                        setupOverlayView(app)
                    }
                }
            }
        }.onFailure { e ->
            Logger.e(TAG, "Error", "Failed to setup Application.onCreate hook", e)
        }
    }

    private fun syncSettings(module: XposedModule, classLoader: ClassLoader) {
        val loadedFromDe = runCatching {
            val ctx = IpcManager.getSafeContext(classLoader, processPackageName) ?: IpcManager.getSystemContext(classLoader) ?: return@runCatching false
            val uri = Uri.parse("content://io.github.hohojia886.pixeltweaks")
            val bundle = ctx.contentResolver.call(uri, "get", null, null) ?: return@runCatching false
            isEnabled = bundle.getBoolean(PreferenceKeys.ENABLE_CAMERA_ENERGY_RING, false)
            ringOnlyCharging = bundle.getBoolean(PreferenceKeys.RING_ONLY_CHARGING, false)
            ringRadiusOffset = bundle.getFloat(PreferenceKeys.RING_RADIUS_OFFSET, 0f)
            ringStrokeWidth = bundle.getFloat(PreferenceKeys.RING_STROKE_WIDTH, 2.0f)
            Logger.i(TAG, "Sync", "Settings loaded from DE: enabled=$isEnabled, onlyCharging=$ringOnlyCharging, offset=$ringRadiusOffset, stroke=$ringStrokeWidth")
            true
        }.getOrDefault(false)

        if (!loadedFromDe) {
            runCatching {
                val prefs = module.getRemotePreferences(IpcManager.PREF_NAME)
                isEnabled = prefs.getBoolean(PreferenceKeys.ENABLE_CAMERA_ENERGY_RING, false)
                ringOnlyCharging = prefs.getBoolean(PreferenceKeys.RING_ONLY_CHARGING, false)
                ringRadiusOffset = prefs.getFloat(PreferenceKeys.RING_RADIUS_OFFSET, 0f)
                ringStrokeWidth = prefs.getFloat(PreferenceKeys.RING_STROKE_WIDTH, 2.0f)
                Logger.i(TAG, "Sync", "Settings loaded from CE (fallback): enabled=$isEnabled")
            }.onFailure { e ->
                Logger.e(TAG, "Error", "Failed to sync settings from CE/DE", e)
            }
        }
    }

    private fun handleBroadcast(intent: Intent) {
        when (intent.action) {
            IpcManager.ACTION_SETTINGS_SYNC -> {
                isEnabled = intent.getBooleanExtra(PreferenceKeys.ENABLE_CAMERA_ENERGY_RING, false)
                ringOnlyCharging = intent.getBooleanExtra(PreferenceKeys.RING_ONLY_CHARGING, false)
                ringRadiusOffset = intent.getFloatExtra(PreferenceKeys.RING_RADIUS_OFFSET, 0f)
                ringStrokeWidth = intent.getFloatExtra(PreferenceKeys.RING_STROKE_WIDTH, 2.0f)
                Logger.i(TAG, "Sync", "Full sync received: enabled=$isEnabled, onlyCharging=$ringOnlyCharging, offset=$ringRadiusOffset, stroke=$ringStrokeWidth")
                overlayView?.updateConfig(isEnabled, isScreenOn, batteryLevel, isCharging, ringOnlyCharging, ringRadiusOffset, ringStrokeWidth)
            }
            IpcManager.ACTION_SETTING_CHANGED -> {
                val key = intent.getStringExtra(PreferenceKeys.EXTRA_KEY)
                when (key) {
                    PreferenceKeys.ENABLE_CAMERA_ENERGY_RING -> {
                        isEnabled = intent.getBooleanExtra(PreferenceKeys.EXTRA_VALUE, false)
                        Logger.i(TAG, "Sync", "Setting [enable_camera_energy_ring] updated to $isEnabled")
                    }
                    PreferenceKeys.RING_ONLY_CHARGING -> {
                        ringOnlyCharging = intent.getBooleanExtra(PreferenceKeys.EXTRA_VALUE, false)
                        Logger.i(TAG, "Sync", "Setting [ring_only_charging] updated to $ringOnlyCharging")
                    }
                    PreferenceKeys.RING_RADIUS_OFFSET -> {
                        ringRadiusOffset = intent.getFloatExtra(PreferenceKeys.EXTRA_VALUE, 0f)
                        Logger.i(TAG, "Sync", "Setting [ring_radius_offset] updated to $ringRadiusOffset")
                    }
                    PreferenceKeys.RING_STROKE_WIDTH -> {
                        ringStrokeWidth = intent.getFloatExtra(PreferenceKeys.EXTRA_VALUE, 2.0f)
                        Logger.i(TAG, "Sync", "Setting [ring_stroke_width] updated to $ringStrokeWidth")
                    }
                }
                overlayView?.updateConfig(isEnabled, isScreenOn, batteryLevel, isCharging, ringOnlyCharging, ringRadiusOffset, ringStrokeWidth)
            }
        }
    }

    private fun registerBatteryAndScreenReceivers(context: Context) {
        runCatching {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
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

                            overlayView?.updateConfig(isEnabled, isScreenOn, batteryLevel, isCharging, ringOnlyCharging, ringRadiusOffset, ringStrokeWidth)
                        }
                        Intent.ACTION_SCREEN_OFF -> {
                            isScreenOn = false
                            Logger.i(TAG, "Lifecycle", "Screen OFF -> Pausing energy ring rendering & animation")
                            overlayView?.updateConfig(isEnabled, isScreenOn, batteryLevel, isCharging, ringOnlyCharging, ringRadiusOffset, ringStrokeWidth)
                        }
                        Intent.ACTION_SCREEN_ON -> {
                            isScreenOn = true
                            Logger.i(TAG, "Lifecycle", "Screen ON -> Resuming energy ring rendering")
                            overlayView?.updateConfig(isEnabled, isScreenOn, batteryLevel, isCharging, ringOnlyCharging, ringRadiusOffset, ringStrokeWidth)
                        }
                    }
                }
            }
            context.registerReceiver(receiver, filter)
        }.onFailure { e ->
            Logger.e(TAG, "Error", "Failed to register battery and screen receivers", e)
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

            val view = EnergyRingView(context)
            overlayView = view

            val statusBarHeight = getStatusBarHeight(context)

            // CRITICAL: Height is bounded strictly to top status bar area (prevents blocking PackageInstaller app updates / tapjacking)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                statusBarHeight,
                2038, // TYPE_APPLICATION_OVERLAY
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }

            wm.addView(view, params)
            view.updateConfig(isEnabled, isScreenOn, batteryLevel, isCharging, ringOnlyCharging, ringRadiusOffset, ringStrokeWidth)
            Logger.i(TAG, "Overlay", "EnergyRingOverlayView added to WindowManager (top status bar height: ${statusBarHeight}px)")
        }.onFailure { e ->
            Logger.e(TAG, "Error", "Failed to setup overlay view", e)
        }
    }

    /**
     * Custom View that renders the Tight Camera Punch-Hole Energy Ring
     */
    private class EnergyRingView(context: Context) : View(context) {

        private var isVisibleEnabled = false
        private var isScreenOnState = true
        private var currentLevel = 100
        private var isChargingState = false
        private var onlyChargingState = false

        private var radiusOffsetDp = 0f
        private var strokeWidthDp = 2.0f

        private var animHue = 0f
        private var animator: ValueAnimator? = null

        private var centerX = -1f
        private var centerY = -1f
        private var baseRadius = -1f

        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.parseColor("#33FFFFFF")
            strokeCap = Paint.Cap.ROUND
        }

        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
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
            onlyCharging: Boolean,
            radiusOffset: Float,
            strokeWidth: Float
        ) {
            this.isVisibleEnabled = enabled
            this.isScreenOnState = screenOn
            this.currentLevel = level.coerceIn(0, 100)
            this.isChargingState = charging
            this.onlyChargingState = onlyCharging
            this.radiusOffsetDp = radiusOffset
            this.strokeWidthDp = strokeWidth

            val shouldDisplay = enabled && screenOn && (!onlyCharging || charging)
            visibility = if (shouldDisplay) VISIBLE else GONE

            val pxStroke = max(1f, strokeWidthDp * density)
            trackPaint.strokeWidth = pxStroke
            ringPaint.strokeWidth = pxStroke

            if (shouldDisplay && charging) {
                startChargingAnimation()
            } else {
                stopChargingAnimation()
            }

            if (shouldDisplay) {
                detectCutoutPosition()
                postInvalidate()
            }
        }

        private fun startChargingAnimation() {
            if (animator?.isRunning == true) return
            animator = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 2000L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { anim ->
                    animHue = anim.animatedValue as Float
                    postInvalidate()
                }
                start()
            }
        }

        private fun stopChargingAnimation() {
            animator?.cancel()
            animator = null
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            detectCutoutPosition()
        }

        override fun onApplyWindowInsets(insets: WindowInsets?): WindowInsets {
            detectCutoutPosition()
            return super.onApplyWindowInsets(insets)
        }

        /**
         * Cutout Detection Algorithm (PHDP Strategy):
         * 1. Primary: Use displayCutout.cutoutPath (API 31+) and compute exact bounds.
         * 2. Fallback: Use displayCutout.boundingRects and minOf(width, height) / 2f.
         */
        private fun detectCutoutPosition() {
            runCatching {
                val cutout: DisplayCutout? = rootWindowInsets?.displayCutout
                var detected = false

                // 1. Primary: cutoutPath (Android 12+ API 31+)
                val cutoutPath: Path? = cutout?.cutoutPath
                if (cutoutPath != null) {
                    val pathBounds = RectF()
                    cutoutPath.computeBounds(pathBounds, true)
                    if (pathBounds.width() > 0f && pathBounds.height() > 0f) {
                        centerX = pathBounds.centerX()
                        centerY = pathBounds.centerY()
                        baseRadius = max(pathBounds.width(), pathBounds.height()) / 2f
                        detected = true
                    }
                }

                // 2. Fallback: boundingRects + minOf (Excludes status bar height padding!)
                if (!detected && cutout != null && cutout.boundingRects.isNotEmpty()) {
                    val rect = cutout.boundingRects.firstOrNull { it.top == 0 } ?: cutout.boundingRects[0]
                    centerX = rect.exactCenterX()
                    centerY = rect.exactCenterY()
                    baseRadius = min(rect.width().toFloat(), rect.height().toFloat()) / 2f
                    detected = true
                }

                // 3. Fallback 2: Default top center estimate
                if (!detected) {
                    val displayMetrics = resources.displayMetrics
                    centerX = displayMetrics.widthPixels / 2f
                    centerY = density * 24f
                    baseRadius = density * 6f
                }

                // Apply tight padding (+1.5dp) + User Radius Offset
                val finalRadius = max(1f, baseRadius + (density * 1.5f) + (radiusOffsetDp * density))

                arcBounds.set(
                    centerX - finalRadius,
                    centerY - finalRadius,
                    centerX + finalRadius,
                    centerY + finalRadius
                )
            }.onFailure {
                val displayMetrics = resources.displayMetrics
                centerX = displayMetrics.widthPixels / 2f
                centerY = density * 24f
                val finalRadius = max(1f, (density * 6.5f) + (radiusOffsetDp * density))
                arcBounds.set(
                    centerX - finalRadius,
                    centerY - finalRadius,
                    centerX + finalRadius,
                    centerY + finalRadius
                )
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            if (!isVisibleEnabled || !isScreenOnState || baseRadius <= 0f) return

            // 1. Draw track
            canvas.drawArc(arcBounds, 0f, 360f, false, trackPaint)

            // 2. Determine Color
            val ringColor = when {
                isChargingState -> {
                    Color.HSVToColor(floatArrayOf(animHue, 0.85f, 1.0f))
                }
                currentLevel <= 10 -> {
                    Color.parseColor("#FF3B30") // Red
                }
                currentLevel <= 20 -> {
                    Color.parseColor("#FFCC00") // Yellow
                }
                else -> {
                    Color.parseColor("#00E676") // Green/Cyan Accent
                }
            }

            ringPaint.color = ringColor

            // 3. Draw Active Energy Ring Arc (starts at 12 o'clock, -90 degrees)
            val sweepAngle = 360f * (currentLevel / 100f)
            canvas.drawArc(arcBounds, -90f, sweepAngle, false, ringPaint)
        }
    }
}
