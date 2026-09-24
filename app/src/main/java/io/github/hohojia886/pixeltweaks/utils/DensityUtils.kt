package io.github.hohojia886.pixeltweaks.utils

import android.content.Context
import android.view.WindowManager

/**
 * DensityUtils: Utility functions for detecting screen physical resolution
 * and calculating target DPI for tablet mode (sw >= 600dp).
 */
object DensityUtils {

    /**
     * Obtains physical screen bounds in pixels (width, height).
     */
    fun getRealScreenBounds(context: Context): Pair<Int, Int> {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            ?: return Pair(0, 0)
        val bounds = windowManager.currentWindowMetrics.bounds
        return Pair(bounds.width(), bounds.height())
    }

    /**
     * Gets the minimum physical screen dimension in pixels.
     */
    fun getRealMinPx(context: Context): Int {
        val (width, height) = getRealScreenBounds(context)
        return minOf(width, height)
    }

    /**
     * Calculates the exact DPI needed to achieve smallest width sw >= 600dp for tablet mode.
     * Formula: sw = (minPx * 160) / densityDpi => targetDpi = (minPx * 160) / 600
     */
    fun calculateTabletDpi(minPx: Int): Int {
        if (minPx <= 0) return 320 // Fallback DPI
        return (minPx * 160) / 600
    }
}
