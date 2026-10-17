package io.github.hohojia886.pixeltweaks.utils

import android.graphics.Color
import androidx.core.graphics.toColorInt

/**
 * BatteryColorManager: Unified single source of truth for battery colors.
 * Centralizes color determination shared between EnergyRing and BatteryPercent.
 */
object BatteryColorManager {

    val COLOR_RED: Int = "#FF3B30".toColorInt()
    val COLOR_ORANGE_YELLOW: Int = "#FF9500".toColorInt()
    val COLOR_GREEN: Int = "#00E676".toColorInt()

    /**
     * Resolves the exact ARGB color for the Energy Ring around the camera cutout.
     *
     * @param isCharging True if the device is currently charging.
     * @param isPowerSave True if system battery saver mode is active.
     * @param level Current battery percentage level (0-100).
     * @param animHue Animated hue value used for HSV rainbow pulse while charging.
     * @return Calculated integer ARGB color value.
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
     * Resolves the exact ARGB color for the Status Bar Battery Percentage Text.
     *
     * @param isCharging True if the device is currently charging.
     * @param isPowerSave True if system battery saver mode is active.
     * @param level Current battery percentage level (0-100).
     * @param animHue Animated hue value used for HSV rainbow pulse while charging.
     * @param defaultTint Status bar theme tint color used as default non-charging color.
     * @return Calculated integer ARGB color value.
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
