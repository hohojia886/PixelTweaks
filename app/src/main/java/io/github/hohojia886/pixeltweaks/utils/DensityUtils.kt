package io.github.hohojia886.pixeltweaks.utils

import android.content.Context
import android.view.WindowManager

/**
 * Utility functions for calculating physical display dimensions and target DPI for Tablet Mode.
 */
object DensityUtils {

    /**
     * Obtains physical display bounds in pixels as a (width, height) Pair.
     *
     * @param context Host process Context used to resolve [WindowManager].
     * @return A [Pair] containing physical screen width and height in pixels.
     */
    fun getRealScreenBounds(context: Context): Pair<Int, Int> {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            ?: return Pair(0, 0)
        val bounds = windowManager.currentWindowMetrics.bounds
        return Pair(bounds.width(), bounds.height())
    }

    /**
     * Calculates the minimum physical screen dimension in pixels.
     *
     * @param context Host process Context.
     * @return The smaller pixel dimension between screen width and height.
     */
    fun getRealMinPx(context: Context): Int {
        val (width, height) = getRealScreenBounds(context)
        return minOf(width, height)
    }

    /**
     * Calculates target DPI required to force smallest width (`sw >= 600dp`) for Tablet Mode.
     * Formula: `targetDpi = (minPx * 160) / 600`.
     *
     * @param minPx The minimum physical screen pixel width.
     * @return Calculated integer DPI value.
     */
    fun calculateTabletDpi(minPx: Int): Int {
        if (minPx <= 0) return 320
        return (minPx * 160) / 600
    }
}
