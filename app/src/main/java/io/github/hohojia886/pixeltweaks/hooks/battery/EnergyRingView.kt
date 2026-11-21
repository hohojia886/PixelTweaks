package io.github.hohojia886.pixeltweaks.hooks.battery

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.toColorInt
import io.github.hohojia886.pixeltweaks.utils.BatteryColorManager
import kotlin.math.abs
import kotlin.math.sin

/**
 * Custom overlay [View] responsible for drawing a battery energy ring around the front camera cutout.
 *
 * Calculates DisplayCutout bounds dynamically and renders a background track, level arc,
 * charging rainbow hue pulse, low-battery alert pulse, 3-second unlock glow aura, and 1-minute rotation.
 *
 * @param context SystemUI application context.
 */
internal class EnergyRingView(context: Context) : View(context) {

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
    private var lastPulseInvalidateTime = 0L

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
     *
     * @param enabled Master toggle for the camera energy ring.
     * @param screenOn Whether the device display is currently on.
     * @param level Current battery percentage in the 0..100 range.
     * @param charging Whether the battery is charging or full on charger.
     * @param powerSave Whether Android power save mode is active.
     * @param onlyCharge True to display the ring only while charging.
     * @param rotation True to enable continuous 1-minute arc rotation.
     * @param offsetDp Additional radius offset in dp around the camera cutout.
     * @param strokeDp Base stroke width in dp.
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
     * Stops the hue pulse animation and clears the animator reference.
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
     * Stops the rotation animation and clears the animator reference.
     */
    private fun stopRotationAnimation() {
        rotationAnimator?.cancel()
        rotationAnimator = null
    }

    /**
     * Stops the unlock glow animation and resets glow alpha.
     */
    private fun stopUnlockGlowAnimation() {
        unlockGlowAnimator?.cancel()
        unlockGlowAnimator = null
        unlockGlowAlpha = 0f
    }

    /**
     * Cleans up running animations when the view is detached to prevent memory leaks.
     */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopPulseAnimation()
        stopRotationAnimation()
        stopUnlockGlowAnimation()
    }

    /**
     * Main drawing routine calculating cutout bounds and rendering the background track and active arc.
     *
     * @param canvas Target software [Canvas] to draw into.
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
                val pulseFactor = 0.4f + 0.6f * abs(sin(pulseProgress * Math.PI)).toFloat()
                val baseAlpha = Color.alpha(ringColor)
                val finalAlpha = (baseAlpha * pulseFactor).toInt().coerceIn(0, 255)
                ringColor = Color.argb(
                    finalAlpha,
                    Color.red(ringColor),
                    Color.green(ringColor),
                    Color.blue(ringColor)
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
            glowPaint.color = Color.argb(
                glowAlphaInt,
                Color.red(ringColor),
                Color.green(ringColor),
                Color.blue(ringColor)
            )
            canvas.drawArc(arcBounds, startAngle, sweepAngle, false, glowPaint)
        }

        ringPaint.color = ringColor
        canvas.drawArc(arcBounds, startAngle, sweepAngle, false, ringPaint)
    }
}
