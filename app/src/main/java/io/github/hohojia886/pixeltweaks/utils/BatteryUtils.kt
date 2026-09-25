package io.github.hohojia886.pixeltweaks.utils

import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.round

data class BatteryData(
    val status: String = "N/A",
    val voltageMv: Int = -1,
    val currentMa: Int = 0,
    val powerWatts: Float = 0f,
    val currentChargeMah: Int = -1,
    val maxChargeVoltageMv: Int = -1,
    val maxChargeCurrentMa: Int = -1,
    val cycles: Int = -1,
    val ratedMah: Int = -1,
    val estMah: Int = -1,
    val calculatedHealthCap: Int = -1,
    val healthCapIndex: Int = -1,
    val overallHealth: String = "N/A",
    val tempCelsius: Float = -1f,
    val resAvgMilli: Float = -1f,
    val resNowMilli: Float = -1f,
    val healthImpIndex: Int = -1,
    val serialNumber: String = "N/A",
    val firstUsageDate: String = "N/A",
    val batteryAge: String = "N/A",
    val aafvMilli: Int = -1
)

/**
 * BatteryUtils: Executes pure Root (`su -c "cat /sys/class/power_supply/battery/<node>"`)
 * inside the App process when SettingsActivity opens.
 */
object BatteryUtils {

    fun fetchBatteryInfoWithRoot(): BatteryData {
        return runCatching {
            val sysfs = "/sys/class/power_supply/battery"
            val script = """
                cat $sysfs/status 2>/dev/null
                echo "===PXTK==="
                cat $sysfs/voltage_now 2>/dev/null
                echo "===PXTK==="
                cat $sysfs/current_now 2>/dev/null
                echo "===PXTK==="
                cat $sysfs/charge_counter 2>/dev/null
                echo "===PXTK==="
                cat $sysfs/constant_charge_voltage 2>/dev/null
                echo "===PXTK==="
                cat $sysfs/constant_charge_current 2>/dev/null
                echo "===PXTK==="
                cat $sysfs/cycle_count 2>/dev/null
                echo "===PXTK==="
                cat $sysfs/charge_full_design 2>/dev/null
                echo "===PXTK==="
                cat $sysfs/charge_full 2>/dev/null
                echo "===PXTK==="
                cat $sysfs/health_capacity_index 2>/dev/null
                echo "===PXTK==="
                cat $sysfs/health 2>/dev/null
                echo "===PXTK==="
                cat $sysfs/temp 2>/dev/null
                echo "===PXTK==="
                cat $sysfs/resistance_avg 2>/dev/null
                echo "===PXTK==="
                cat $sysfs/resistance 2>/dev/null
                echo "===PXTK==="
                cat $sysfs/health_impedance_index 2>/dev/null
                echo "===PXTK==="
                cat $sysfs/serial_number 2>/dev/null
                echo "===PXTK==="
                cat $sysfs/aafv_offset 2>/dev/null
            """.trimIndent()

            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", script))
            val rawStdout = process.inputStream.bufferedReader().use { it.readText() }.trim()
            process.waitFor()

            val parts = rawStdout.split("===PXTK===").map { it.trim() }

            val status = parts.getOrNull(0)?.takeIf { it.isNotEmpty() } ?: "N/A"
            val voltageUv = parts.getOrNull(1)?.toIntOrNull() ?: -1
            val currentUa = parts.getOrNull(2)?.toIntOrNull() ?: 0
            val chargeCounterUah = parts.getOrNull(3)?.toIntOrNull() ?: -1
            val maxVoltageUv = parts.getOrNull(4)?.toIntOrNull() ?: -1
            val maxCurrentUa = parts.getOrNull(5)?.toIntOrNull() ?: -1

            val cycles = parts.getOrNull(6)?.toIntOrNull() ?: -1
            val ratedMicroAh = parts.getOrNull(7)?.toIntOrNull() ?: -1
            val estMicroAh = parts.getOrNull(8)?.toIntOrNull() ?: -1
            val healthCapIndex = parts.getOrNull(9)?.toIntOrNull() ?: -1
            val overallHealth = parts.getOrNull(10)?.takeIf { it.isNotEmpty() } ?: "N/A"
            val tempTenths = parts.getOrNull(11)?.toFloatOrNull() ?: -1f
            val resAvgMicro = parts.getOrNull(12)?.toFloatOrNull() ?: -1f
            val resNowMicro = parts.getOrNull(13)?.toFloatOrNull() ?: -1f
            val healthImpIndex = parts.getOrNull(14)?.toIntOrNull() ?: -1
            val serialNum = parts.getOrNull(15)?.takeIf { it.isNotEmpty() } ?: "N/A"
            val aafvMicro = parts.getOrNull(16)?.toIntOrNull() ?: -1

            val voltageMv = if (voltageUv > 0) voltageUv / 1000 else -1
            val currentMa = currentUa / 1000
            val powerWatts = if (voltageMv > 0) (voltageMv.toFloat() * currentMa.toFloat()) / 1_000_000.0f else 0f
            val currentChargeMah = if (chargeCounterUah > 0) chargeCounterUah / 1000 else -1
            val maxChargeVoltageMv = if (maxVoltageUv > 0) maxVoltageUv / 1000 else -1
            val maxChargeCurrentMa = if (maxCurrentUa > 0) maxCurrentUa / 1000 else -1

            val ratedMah = if (ratedMicroAh > 10000) ratedMicroAh / 1000 else ratedMicroAh
            val estMah = if (estMicroAh > 10000) estMicroAh / 1000 else estMicroAh
            val calculatedHealthCap = if (ratedMah > 0 && estMah > 0) {
                round((estMah * 100.0) / ratedMah).toInt().coerceIn(0, 100)
            } else -1

            val tempCelsius = if (tempTenths > 0) tempTenths / 10.0f else -1f
            val resAvgMilli = if (resAvgMicro > 0) resAvgMicro / 1000.0f else -1f
            val resNowMilli = if (resNowMicro > 0) resNowMicro / 1000.0f else -1f
            val aafvMilli = if (aafvMicro > 0) aafvMicro / 1000 else aafvMicro

            val (firstUsageDate, batteryAge) = parseDateFromSerialNumber(serialNum)

            BatteryData(
                status = status,
                voltageMv = voltageMv,
                currentMa = currentMa,
                powerWatts = powerWatts,
                currentChargeMah = currentChargeMah,
                maxChargeVoltageMv = maxChargeVoltageMv,
                maxChargeCurrentMa = maxChargeCurrentMa,
                cycles = cycles,
                ratedMah = ratedMah,
                estMah = estMah,
                calculatedHealthCap = calculatedHealthCap,
                healthCapIndex = healthCapIndex,
                overallHealth = overallHealth,
                tempCelsius = tempCelsius,
                resAvgMilli = resAvgMilli,
                resNowMilli = resNowMilli,
                healthImpIndex = healthImpIndex,
                serialNumber = serialNum,
                firstUsageDate = firstUsageDate,
                batteryAge = batteryAge,
                aafvMilli = aafvMilli
            )
        }.getOrDefault(BatteryData())
    }

    fun parseDateFromSerialNumber(serial: String): Pair<String, String> {
        if (serial.isEmpty() || serial == "N/A") return Pair("N/A", "N/A")

        val regex = Regex("(202[0-9])(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])")
        val match = regex.find(serial) ?: return Pair("N/A", "N/A")

        return runCatching {
            val dateStr = match.value
            val sdf = SimpleDateFormat("yyyyMMdd", Locale.US)
            sdf.isLenient = false
            val date = sdf.parse(dateStr) ?: return Pair("N/A", "N/A")

            val formattedDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
            val diffMs = System.currentTimeMillis() - date.time
            val totalDays = (diffMs / (1000L * 60 * 60 * 24)).toInt().coerceAtLeast(0)

            val years = totalDays / 365
            val remDays = totalDays % 365
            val months = remDays / 30
            val days = remDays % 30

            val ageStr = "${years}y ${months}m ${days}d"

            Pair(formattedDate, ageStr)
        }.getOrDefault(Pair("N/A", "N/A"))
    }
}
