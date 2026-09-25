package io.github.hohojia886.pixeltweaks.utils

import org.junit.Test
import kotlin.math.round
import kotlin.test.assertEquals

class BatteryUtilsTest {

    @Test
    fun testBatteryMetricsUnitsAndFormatting() {
        val cycles = 244
        val ratedMicroAh = 5035000
        val estMicroAh = 4700000
        val tempTenths = 265f
        val resAvgMicro = 62700f
        val resNowMicro = 70312f
        val aafvMicro = 20000

        val ratedMah = ratedMicroAh / 1000
        val estMah = estMicroAh / 1000
        val tempCelsius = tempTenths / 10.0f
        val resAvgMilli = resAvgMicro / 1000.0f
        val resNowMilli = resNowMicro / 1000.0f
        val aafvMilli = aafvMicro / 1000

        assertEquals(244, cycles)
        assertEquals(5035, ratedMah)
        assertEquals(4700, estMah)
        assertEquals(26.5f, tempCelsius)
        assertEquals(62.7f, resAvgMilli)
        assertEquals(70.312f, resNowMilli)
        assertEquals(20, aafvMilli)
    }

    @Test
    fun testRealtimePowerCalculation() {
        val voltageUv = 4185234
        val currentUa = 1500000

        val voltageMv = voltageUv / 1000
        val currentMa = currentUa / 1000
        val powerWatts = (voltageMv.toFloat() * currentMa.toFloat()) / 1_000_000.0f

        assertEquals(4185, voltageMv)
        assertEquals(1500, currentMa)
        assertEquals(6.2775f, powerWatts, 0.01f)
    }

    @Test
    fun testCalculatedCapacityHealth() {
        val ratedMah = 5035
        val estMah = 4700
        val calculatedHealth = round((estMah * 100.0) / ratedMah).toInt()

        assertEquals(93, calculatedHealth)
    }

    @Test
    fun testDateParsingFromSerialNumber() {
        val serial = "13G8230046501AA496007C901176202408286810R"
        val (formattedDate, _) = BatteryUtils.parseDateFromSerialNumber(serial)

        assertEquals("2024-08-28", formattedDate)
    }
}
