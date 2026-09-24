package io.github.hohojia886.pixeltweaks.utils

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DensityUtilsTest {

    @Test
    fun testCalculateTabletDpiForVariousResolutions() {
        // 1080p (e.g. 1080 x 2400)
        assertEquals(288, DensityUtils.calculateTabletDpi(1080))

        // 1440p (e.g. 1440 x 3120)
        assertEquals(384, DensityUtils.calculateTabletDpi(1440))

        // Pixel 8 Pro (1344 x 2992)
        assertEquals(358, DensityUtils.calculateTabletDpi(1344))

        // 720p (720 x 1600)
        assertEquals(192, DensityUtils.calculateTabletDpi(720))

        // Invalid / Fallback
        assertEquals(320, DensityUtils.calculateTabletDpi(0))
        assertEquals(320, DensityUtils.calculateTabletDpi(-1))
    }

    @Test
    fun testResultingSmallestWidthIsAtLeast600Dp() {
        val testResolutions = listOf(1080, 1440, 1344, 720, 1008)
        testResolutions.forEach { minPx ->
            val targetDpi = DensityUtils.calculateTabletDpi(minPx)
            val swDp = (minPx * 160.0) / targetDpi
            assertTrue(
                swDp >= 600.0,
                "Resulting swDp ($swDp) for minPx=$minPx and targetDpi=$targetDpi should be >= 600.0"
            )
        }
    }
}
