package io.github.hohojia886.pixeltweaks.hooks.`interface`.battery

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.BatteryManager
import android.os.Handler
import android.os.PowerManager
import android.os.SystemClock
import android.view.animation.LinearInterpolator
import io.github.hohojia886.pixeltweaks.utils.BatteryColorManager
import io.github.hohojia886.pixeltweaks.utils.Logger

/**
 * Tracks system battery level, charging state, power save mode, screen power state, and status bar tint.
 *
 * Manages the 30 FPS LTPO-throttled rainbow pulse animation during charging and dispatches formatted
 * percentage text and computed text color callbacks to UI consumers.
 *
 * @param logTag Logger subsystem tag.
 * @param mainHandler Main-looper [Handler] used for animator lifecycle management.
 * @param onTextChanged Callback invoked whenever the formatted battery percentage string updates.
 * @param onColorChanged Callback invoked whenever the computed effective text color updates.
 */
internal class BatteryStateController(
    private val logTag: String,
    private val mainHandler: Handler,
    private val onTextChanged: (text: String, color: Int) -> Unit,
    private val onColorChanged: (color: Int) -> Unit,
) {

    @Volatile var currentPercentText: String = ""
        private set

    @Volatile private var currentTint: Int = Color.WHITE
    @Volatile private var currentLevel: Int = 100
    @Volatile private var isCharging: Boolean = false
    @Volatile private var isPowerSaveMode: Boolean = false
    @Volatile private var isScreenOn: Boolean = true
    @Volatile private var animHue: Float = 0f
    @Volatile private var lastPulseInvalidateTime: Long = 0L

    private var pulseAnimator: ValueAnimator? = null

    /**
     * Computes final text color using system tint, charging animation hue, power save mode, and battery level.
     *
     * @return ARGB color integer for the status bar percentage indicator.
     */
    fun getEffectiveTextColor(): Int {
        return BatteryColorManager.getPercentTextColor(
            isCharging,
            isPowerSaveMode,
            currentLevel,
            animHue,
            currentTint,
        )
    }

    /**
     * Updates the active status bar icon tint and notifies color observers.
     *
     * @param tint Resolved status bar foreground tint color.
     */
    fun applyTint(tint: Int) {
        currentTint = tint
        onColorChanged(getEffectiveTextColor())
    }

    /**
     * Subscribes to system battery, screen power, and power save mode intents and seeds initial sticky state.
     *
     * @param context SystemUI application [Context].
     */
    fun registerReceiver(context: Context) {
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
                            handleBatteryChanged(intent)
                        }
                        Intent.ACTION_SCREEN_OFF -> {
                            isScreenOn = false
                            stopPulseAnimation()
                        }
                        Intent.ACTION_SCREEN_ON -> {
                            isScreenOn = true
                            if (isCharging) startPulseAnimation() else stopPulseAnimation()
                        }
                        PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                            val pManager = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
                            isPowerSaveMode = pManager?.isPowerSaveMode ?: false
                            onColorChanged(getEffectiveTextColor())
                        }
                    }
                }
            }
            val stickyIntent = context.registerReceiver(receiver, filter)
            if (stickyIntent != null) {
                handleBatteryChanged(stickyIntent, forcePulseSync = true)
            }
        }.onFailure { e ->
            Logger.e(logTag, "Error", "Failed to register sticky battery receiver", e)
        }
    }

    /**
     * Parses an [Intent.ACTION_BATTERY_CHANGED] payload and propagates text/color updates.
     *
     * @param intent Battery status broadcast intent.
     * @param forcePulseSync True during initial sticky sync to align animator state unconditionally.
     */
    private fun handleBatteryChanged(intent: Intent, forcePulseSync: Boolean = false) {
        val rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (rawLevel >= 0 && scale > 0) {
            currentLevel = (rawLevel * 100) / scale
            currentPercentText = "$currentLevel%"
            onTextChanged(currentPercentText, getEffectiveTextColor())
        }
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val newCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        if (forcePulseSync || newCharging != isCharging) {
            isCharging = newCharging
            if (isCharging && isScreenOn) startPulseAnimation() else stopPulseAnimation()
        }
        onColorChanged(getEffectiveTextColor())
    }

    /**
     * Animates hue values to provide a pulsing charging indicator, throttled to 30 FPS for LTPO VRR efficiency.
     */
    private fun startPulseAnimation() {
        if (!isScreenOn || pulseAnimator != null) return
        mainHandler.post {
            if (!isScreenOn || pulseAnimator != null) return@post
            pulseAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 3000L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastPulseInvalidateTime >= 33L) {
                        lastPulseInvalidateTime = now
                        animHue = it.animatedValue as Float
                        onColorChanged(getEffectiveTextColor())
                    }
                }
                start()
            }
        }
    }

    /**
     * Terminates the hue pulse animation and refreshes text colors on the main thread.
     */
    private fun stopPulseAnimation() {
        mainHandler.post {
            pulseAnimator?.cancel()
            pulseAnimator = null
            onColorChanged(getEffectiveTextColor())
        }
    }
}
