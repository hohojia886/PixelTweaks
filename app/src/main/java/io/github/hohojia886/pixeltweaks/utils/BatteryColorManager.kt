package io.github.hohojia886.pixeltweaks.utils

import android.graphics.Color
import androidx.core.graphics.toColorInt

/**
 * BatteryColorManager: Unified single source of truth for battery color determination across the module.
 * Centralizes the exact color logic shared between EnergyRingHook and BatteryPercentHook:
 * - Charging: 3-second HSV rainbow gradient pulse (throttled to 30 FPS for LTPO 30Hz VRR power savings)
 * - Critical (<= 10%): Red (#FF3B30)
 * - Power Save Mode: Orange-Yellow (#FF9500)
 * - Normal: Green (#00E676) for Energy Ring, or Status Bar Theme Tint (defaultTint) for Battery Percentage Text
 */
object BatteryColorManager {

    val COLOR_RED: Int = "#FF3B30".toColorInt()
    val COLOR_ORANGE_YELLOW: Int = "#FF9500".toColorInt()
    val COLOR_GREEN: Int = "#00E676".toColorInt()

    /**
     * Resolves the exact color for the Energy Ring.
     */
    fun getRingColor(isCharging: Boolean, isPowerSave: Boolean, level: Int, animHue: Float): Int {
        return when {
            isCharging -> Color.HSVToColor(floatArrayOf(animHue, 0.85f, 1.0f))
            level <= 10 -> COLOR_RED
            isPowerSave -> COLOR_ORANGE_YELLOW
            else -> COLOR_GREEN
        }
    }

    /**
     * Resolves the exact color for the Status Bar Battery Percentage Text.
     */
    fun getPercentTextColor(isCharging: Boolean, isPowerSave: Boolean, level: Int, animHue: Float, defaultTint: Int): Int {
        return when {
            isCharging -> Color.HSVToColor(floatArrayOf(animHue, 0.85f, 1.0f))
            level <= 10 -> COLOR_RED
            isPowerSave -> COLOR_ORANGE_YELLOW
            else -> defaultTint
        }
    }
}
