package io.github.hohojia886.pixeltweaks.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.hohojia886.pixeltweaks.R
import io.github.hohojia886.pixeltweaks.presentation.SettingsEvent
import io.github.hohojia886.pixeltweaks.presentation.SettingsUiState
import kotlin.math.abs

/**
 * Battery category section rendering Camera Energy Ring customization and live battery diagnostics metrics.
 *
 * Isolates frequently refreshing battery telemetry in its own composable scope so periodic polling
 * does not force recomposition of unrelated settings categories.
 *
 * @param uiState Current immutable [SettingsUiState] state instance.
 * @param onEvent Event callback lambda to dispatch user intents.
 */
@Composable
internal fun BatterySettingsSection(
    uiState: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit
) {
    val context = LocalContext.current

    CategoryHeader(stringResource(R.string.category_battery), Icons.Outlined.BatteryChargingFull)
    SettingsCard {
        SwitchSettingItem(
            title = stringResource(R.string.camera_energy_ring),
            summary = stringResource(R.string.camera_energy_ring_summary),
            checked = uiState.cameraEnergyRing,
            onCheckedChange = { onEvent(SettingsEvent.ToggleCameraEnergyRing(it)) }
        )
        AnimatedVisibility(visible = uiState.cameraEnergyRing) {
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SwitchSettingItem(
                    title = stringResource(R.string.ring_only_charging),
                    summary = stringResource(R.string.ring_only_charging_summary),
                    checked = uiState.ringOnlyCharging,
                    onCheckedChange = { onEvent(SettingsEvent.ToggleRingOnlyCharging(it)) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SwitchSettingItem(
                    title = stringResource(R.string.enable_ring_rotation),
                    summary = stringResource(R.string.enable_ring_rotation_summary),
                    checked = uiState.ringRotation,
                    onCheckedChange = { onEvent(SettingsEvent.ToggleRingRotation(it)) }
                )
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    HorizontalDivider(
                        modifier = Modifier.padding(bottom = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    val radiusValues = listOf(-2.0f, -1.0f, 0.0f, 1.0f, 2.0f)
                    val radiusIndex = radiusValues.minByOrNull { abs(it - uiState.ringRadiusOffset) }?.let { radiusValues.indexOf(it) } ?: 2
                    Text(
                        text = context.getString(R.string.ring_radius_offset_format, uiState.ringRadiusOffset),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = radiusIndex.toFloat(),
                        onValueChange = { onEvent(SettingsEvent.ChangeRingRadiusOffset(radiusValues[it.toInt().coerceIn(0, 4)])) },
                        valueRange = 0f..4f,
                        steps = 3
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val strokeValues = listOf(1.0f, 1.5f, 2.0f, 2.5f, 3.0f)
                    val strokeIndex = strokeValues.minByOrNull { abs(it - uiState.ringStrokeWidth) }?.let { strokeValues.indexOf(it) } ?: 2
                    Text(
                        text = context.getString(R.string.ring_stroke_width_format, uiState.ringStrokeWidth),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = strokeIndex.toFloat(),
                        onValueChange = { onEvent(SettingsEvent.ChangeRingStrokeWidth(strokeValues[it.toInt().coerceIn(0, 4)])) },
                        valueRange = 0f..4f,
                        steps = 3
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        SwitchSettingItem(
            title = stringResource(R.string.enable_battery_info),
            summary = stringResource(R.string.enable_battery_info_summary),
            checked = uiState.enableBatteryInfo,
            onCheckedChange = { onEvent(SettingsEvent.ToggleBatteryInfo(it)) }
        )

        if (uiState.enableBatteryInfo) {
            BatteryDiagnosticsMetrics(uiState)
        }
    }
}

/**
 * Renders the detailed sysfs battery health, voltage, resistance, and capacity diagnostic metrics.
 *
 * @param uiState Current immutable [SettingsUiState] containing formatted battery telemetry.
 */
@Composable
private fun BatteryDiagnosticsMetrics(uiState: SettingsUiState) {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    BatteryMetricItem(stringResource(R.string.battery_status), uiState.batteryStatus, isColumnLayout = true)

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    val powerStr = stringResource(R.string.battery_power_format, uiState.batteryPowerWatts, uiState.batteryCurrentMa)
    BatteryMetricItem(stringResource(R.string.battery_power_current), powerStr, isColumnLayout = true)

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    val voltStr = if (uiState.batteryVoltageMv > 0) stringResource(R.string.battery_voltage_format, uiState.batteryVoltageMv / 1000.0f, uiState.batteryVoltageMv) else stringResource(R.string.battery_unknown)
    BatteryMetricItem(stringResource(R.string.battery_voltage), voltStr, isColumnLayout = true)

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    val chargeStr = if (uiState.batteryCurrentChargeMah > 0) stringResource(R.string.battery_capacity_format, uiState.batteryCurrentChargeMah) else stringResource(R.string.battery_unknown)
    BatteryMetricItem(stringResource(R.string.battery_current_stored), chargeStr, isColumnLayout = true)

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    val limitsStr = if (uiState.batteryMaxChargeVoltageMv > 0 && uiState.batteryMaxChargeCurrentMa > 0) {
        stringResource(R.string.battery_max_limits_format, uiState.batteryMaxChargeVoltageMv, uiState.batteryMaxChargeCurrentMa)
    } else stringResource(R.string.battery_unknown)
    BatteryMetricItem(stringResource(R.string.battery_max_limits), limitsStr, isColumnLayout = true)

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    val cyclesStr = if (uiState.batteryCycles >= 0) stringResource(R.string.battery_cycles_format, uiState.batteryCycles) else stringResource(R.string.battery_unknown)
    BatteryMetricItem(stringResource(R.string.battery_cycles), cyclesStr, isColumnLayout = true)

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    val ratedStr = if (uiState.batteryRated > 0) stringResource(R.string.battery_capacity_format, uiState.batteryRated) else stringResource(R.string.battery_unknown)
    BatteryMetricItem(stringResource(R.string.battery_rated_capacity), ratedStr, isColumnLayout = true)

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    val estStr = if (uiState.batteryEstimated > 0) stringResource(R.string.battery_capacity_format, uiState.batteryEstimated) else stringResource(R.string.battery_unknown)
    BatteryMetricItem(stringResource(R.string.battery_estimated_capacity), estStr, isColumnLayout = true)

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    val calcHealthStr = if (uiState.batteryCalculatedHealth > 0) stringResource(R.string.battery_health_format, uiState.batteryCalculatedHealth) else stringResource(R.string.battery_unknown)
    BatteryMetricItem(stringResource(R.string.battery_calculated_health), calcHealthStr, isColumnLayout = true)

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    val healthCapStr = if (uiState.batteryHealthCapIndex > 0) stringResource(R.string.battery_health_format, uiState.batteryHealthCapIndex) else stringResource(R.string.battery_unknown)
    BatteryMetricItem(stringResource(R.string.battery_health_capacity_index), healthCapStr, isColumnLayout = true)

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    BatteryMetricItem(stringResource(R.string.battery_overall_health), uiState.batteryOverallHealth, isColumnLayout = true)

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    val tempStr = if (uiState.batteryTemp > 0) stringResource(R.string.battery_temp_format, uiState.batteryTemp) else stringResource(R.string.battery_unknown)
    BatteryMetricItem(stringResource(R.string.battery_temperature), tempStr, isColumnLayout = true)

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    val resAvgStr = if (uiState.batteryResistanceAvg > 0) stringResource(R.string.battery_milli_format, uiState.batteryResistanceAvg) else stringResource(R.string.battery_unknown)
    BatteryMetricItem(stringResource(R.string.battery_resistance_avg), resAvgStr, isColumnLayout = true)

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    val resNowStr = if (uiState.batteryResistanceNow > 0) stringResource(R.string.battery_milli_format, uiState.batteryResistanceNow) else stringResource(R.string.battery_unknown)
    BatteryMetricItem(stringResource(R.string.battery_resistance_now), resNowStr, isColumnLayout = true)

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    val healthImpStr = if (uiState.batteryHealthImpIndex > 0) stringResource(R.string.battery_health_format, uiState.batteryHealthImpIndex) else stringResource(R.string.battery_unknown)
    BatteryMetricItem(stringResource(R.string.battery_health_impedance_index), healthImpStr, isColumnLayout = true)

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    BatteryMetricItem(stringResource(R.string.battery_serial_number), uiState.batterySerialNumber, isColumnLayout = true)

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    val ageStr = if (uiState.batteryFirstUsage != "N/A") stringResource(R.string.battery_first_usage_age_format, uiState.batteryFirstUsage, uiState.batteryAge) else stringResource(R.string.battery_unknown)
    BatteryMetricItem(stringResource(R.string.battery_first_usage), ageStr, isColumnLayout = true)

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    val aafvStr = if (uiState.batteryAafvOffset >= 0) stringResource(R.string.battery_aafv_format, uiState.batteryAafvOffset) else stringResource(R.string.battery_unknown)
    BatteryMetricItem(stringResource(R.string.battery_aafv_offset), aafvStr, isColumnLayout = true)
}
