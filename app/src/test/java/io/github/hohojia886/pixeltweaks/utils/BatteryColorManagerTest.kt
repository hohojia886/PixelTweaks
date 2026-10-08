package io.github.hohojia886.pixeltweaks.utils

import android.graphics.Color
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BatteryColorManagerTest {

    @Test
    fun testGetRingColorCriticalBatteryReturnsRed() {
        val color = BatteryColorManager.getRingColor(
            isCharging = false,
            isPowerSave = false,
            level = 8,
            animHue = 0f
        )
        assertEquals(expected = BatteryColorManager.COLOR_RED, actual = color)
    }

    @Test
    fun testGetRingColorPowerSaveModeReturnsOrangeYellow() {
        val color = BatteryColorManager.getRingColor(
            isCharging = false,
            isPowerSave = true,
            level = 50,
            animHue = 0f
        )
        assertEquals(expected = BatteryColorManager.COLOR_ORANGE_YELLOW, actual = color)
    }

    @Test
    fun testGetRingColorNormalReturnsGreen() {
        val color = BatteryColorManager.getRingColor(
            isCharging = false,
            isPowerSave = false,
            level = 80,
            animHue = 0f
        )
        assertEquals(expected = BatteryColorManager.COLOR_GREEN, actual = color)
    }

    @Test
    fun testGetRingColorChargingReturnsHsvColor() {
        val hue = 120f
        val expectedColor = Color.HSVToColor(floatArrayOf(hue, 0.85f, 1.0f))
        val color = BatteryColorManager.getRingColor(
            isCharging = true,
            isPowerSave = false,
            level = 50,
            animHue = hue
        )
        assertEquals(expected = expectedColor, actual = color)
    }

    @Test
    fun testGetPercentTextColorNormalReturnsDefaultTint() {
        val defaultTint = Color.WHITE
        val color = BatteryColorManager.getPercentTextColor(
            isCharging = false,
            isPowerSave = false,
            level = 80,
            animHue = 0f,
            defaultTint = defaultTint
        )
        assertEquals(expected = defaultTint, actual = color)
    }

    @Test
    fun testGetPercentTextColorCriticalBatteryReturnsRed() {
        val color = BatteryColorManager.getPercentTextColor(
            isCharging = false,
            isPowerSave = false,
            level = 5,
            animHue = 0f,
            defaultTint = Color.WHITE
        )
        assertEquals(expected = BatteryColorManager.COLOR_RED, actual = color)
    }
}
