package io.github.hohojia886.pixeltweaks.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.hohojia886.pixeltweaks.BuildConfig
import io.github.hohojia886.pixeltweaks.R
import io.github.hohojia886.pixeltweaks.presentation.SettingsEvent
import io.github.hohojia886.pixeltweaks.presentation.SettingsUiState
import io.github.hohojia886.pixeltweaks.utils.DensityUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {

            // Section: System & Security
            CategoryHeader(stringResource(R.string.category_security), Icons.Outlined.Security)
            SettingsCard {
                SwitchSettingItem(
                    title = stringResource(R.string.unrestricted_screenshots),
                    summary = stringResource(R.string.unrestricted_screenshots_summary),
                    checked = uiState.unrestrictedScreenshots,
                    onCheckedChange = { onEvent(SettingsEvent.ToggleUnrestrictedScreenshots(it)) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SwitchSettingItem(
                    title = stringResource(R.string.easy_unlock),
                    summary = stringResource(R.string.easy_unlock_summary),
                    checked = uiState.easyUnlock,
                    onCheckedChange = { onEvent(SettingsEvent.ToggleEasyUnlock(it)) }
                )
                AnimatedVisibility(visible = uiState.easyUnlock) {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        SwitchSettingItem(
                            title = stringResource(R.string.easy_unlock_reboot),
                            summary = stringResource(R.string.easy_unlock_reboot_summary),
                            checked = uiState.easyUnlockReboot,
                            onCheckedChange = { onEvent(SettingsEvent.ToggleEasyUnlockReboot(it)) }
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SwitchSettingItem(
                    title = if (uiState.allowDowngradeTimer != null) "${stringResource(R.string.allow_downgrade)} (${uiState.allowDowngradeTimer})" else stringResource(R.string.allow_downgrade),
                    summary = stringResource(R.string.allow_downgrade_summary),
                    checked = uiState.allowDowngrade,
                    onCheckedChange = { onEvent(SettingsEvent.ToggleAllowDowngrade(it)) },
                    isWarning = uiState.allowDowngradeTimer != null
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SwitchSettingItem(
                    title = if (uiState.bypassSignatureTimer != null) "${stringResource(R.string.bypass_signature)} (${uiState.bypassSignatureTimer})" else stringResource(R.string.bypass_signature),
                    summary = stringResource(R.string.bypass_signature_summary),
                    checked = uiState.bypassSignature,
                    onCheckedChange = { onEvent(SettingsEvent.ToggleBypassSignature(it)) },
                    isWarning = uiState.bypassSignatureTimer != null
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section: Interface & Status Bar
            CategoryHeader(stringResource(R.string.category_interface), Icons.Outlined.Settings)
            SettingsCard {
                SwitchSettingItem(
                    title = stringResource(R.string.clear_all_button),
                    summary = stringResource(R.string.clear_all_button_summary),
                    checked = uiState.clearAll,
                    onCheckedChange = { onEvent(SettingsEvent.ToggleClearAll(it)) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                val targetDpi = remember(context) {
                    val minPx = DensityUtils.getRealMinPx(context)
                    DensityUtils.calculateTabletDpi(minPx)
                }
                val tabletSummary = if (uiState.tabletMode) {
                    stringResource(R.string.tablet_mode_active_summary, targetDpi)
                } else {
                    stringResource(R.string.tablet_mode_summary, targetDpi)
                }
                SwitchSettingItem(
                    title = stringResource(R.string.tablet_mode),
                    summary = tabletSummary,
                    checked = uiState.tabletMode,
                    onCheckedChange = { onEvent(SettingsEvent.ToggleTabletMode(it)) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SwitchSettingItem(
                    title = stringResource(R.string.network_traffic),
                    summary = stringResource(R.string.network_traffic_summary),
                    checked = uiState.networkTraffic,
                    onCheckedChange = { onEvent(SettingsEvent.ToggleNetworkTraffic(it)) }
                )
                AnimatedVisibility(visible = uiState.networkTraffic) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        HorizontalDivider(
                            modifier = Modifier.padding(bottom = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        
                        // Interval Slider
                        val intervalValues = listOf(1, 2, 3, 4, 5)
                        Text(
                            text = context.getString(R.string.traffic_interval_format, uiState.trafficInterval),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Slider(
                            value = intervalValues.indexOf(uiState.trafficInterval).coerceAtLeast(0).toFloat(),
                            onValueChange = { onEvent(SettingsEvent.ChangeTrafficInterval(intervalValues[it.toInt()])) },
                            valueRange = 0f..4f,
                            steps = 3
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Font Size Slider
                        val fontValues = listOf(6f, 7f, 8f, 9f, 10f)
                        Text(
                            text = context.getString(R.string.traffic_font_format, uiState.trafficFontSize.toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Slider(
                            value = fontValues.indexOf(uiState.trafficFontSize).coerceAtLeast(0).toFloat(),
                            onValueChange = { onEvent(SettingsEvent.ChangeTrafficFontSize(fontValues[it.toInt()])) },
                            valueRange = 0f..4f,
                            steps = 3
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Threshold Slider
                        val thresholdValues = listOf(0, 1, 10, 100, 1024)
                        val thresholdText = when (uiState.trafficThreshold) {
                            0 -> stringResource(R.string.traffic_threshold_always)
                            1024 -> stringResource(R.string.traffic_threshold_mb)
                            else -> stringResource(R.string.traffic_threshold_kb, uiState.trafficThreshold)
                        }
                        Text(
                            text = thresholdText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Slider(
                            value = thresholdValues.indexOf(uiState.trafficThreshold).coerceAtLeast(0).toFloat(),
                            onValueChange = { onEvent(SettingsEvent.ChangeTrafficThreshold(thresholdValues[it.toInt()])) },
                            valueRange = 0f..4f,
                            steps = 3
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section: Interaction & Gestures
            CategoryHeader(stringResource(R.string.category_interaction), Icons.Outlined.Gesture)
            SettingsCard {
                SwitchSettingItem(
                    title = stringResource(R.string.dt_launcher),
                    summary = stringResource(R.string.dt_launcher_summary),
                    checked = uiState.dtLauncher,
                    onCheckedChange = { onEvent(SettingsEvent.ToggleDtLauncher(it)) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SwitchSettingItem(
                    title = stringResource(R.string.dt_lockscreen),
                    summary = stringResource(R.string.dt_lockscreen_summary),
                    checked = uiState.dtLockscreen,
                    onCheckedChange = { onEvent(SettingsEvent.ToggleDtLockscreen(it)) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SwitchSettingItem(
                    title = stringResource(R.string.dt_statusbar),
                    summary = stringResource(R.string.dt_statusbar_summary),
                    checked = uiState.dtStatusbar,
                    onCheckedChange = { onEvent(SettingsEvent.ToggleDtStatusbar(it)) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section: Quick Settings
            CategoryHeader(stringResource(R.string.category_quick_settings), Icons.Outlined.Tune)
            SettingsCard {
                SwitchSettingItem(
                    title = stringResource(R.string.qs_wifi_fix),
                    summary = stringResource(R.string.qs_wifi_fix_summary),
                    checked = uiState.qsWifiFix,
                    onCheckedChange = { onEvent(SettingsEvent.ToggleQsWifiFix(it)) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SwitchSettingItem(
                    title = stringResource(R.string.qs_data_fix),
                    summary = stringResource(R.string.qs_data_fix_summary),
                    checked = uiState.qsDataFix,
                    onCheckedChange = { onEvent(SettingsEvent.ToggleQsDataFix(it)) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section: Battery Info (Toggle & 18 Metrics - All 2-Line Layout)
            CategoryHeader(stringResource(R.string.category_battery), Icons.Outlined.BatteryChargingFull)
            SettingsCard {
                SwitchSettingItem(
                    title = stringResource(R.string.enable_battery_info),
                    summary = stringResource(R.string.enable_battery_info_summary),
                    checked = uiState.enableBatteryInfo,
                    onCheckedChange = { onEvent(SettingsEvent.ToggleBatteryInfo(it)) }
                )

                if (uiState.enableBatteryInfo) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 1. Charging Status
                    BatteryMetricItem(stringResource(R.string.battery_status), uiState.batteryStatus, isColumnLayout = true)

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 2. Real-time Power & Current
                    val powerStr = stringResource(R.string.battery_power_format, uiState.batteryPowerWatts, uiState.batteryCurrentMa)
                    BatteryMetricItem(stringResource(R.string.battery_power_current), powerStr, isColumnLayout = true)

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 3. Real-time Voltage
                    val voltStr = if (uiState.batteryVoltageMv > 0) stringResource(R.string.battery_voltage_format, uiState.batteryVoltageMv / 1000.0f, uiState.batteryVoltageMv) else stringResource(R.string.battery_unknown)
                    BatteryMetricItem(stringResource(R.string.battery_voltage), voltStr, isColumnLayout = true)

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 4. Current Stored Charge
                    val chargeStr = if (uiState.batteryCurrentChargeMah > 0) stringResource(R.string.battery_capacity_format, uiState.batteryCurrentChargeMah) else stringResource(R.string.battery_unknown)
                    BatteryMetricItem(stringResource(R.string.battery_current_stored), chargeStr, isColumnLayout = true)

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 5. Max Charge Limits
                    val limitsStr = if (uiState.batteryMaxChargeVoltageMv > 0 && uiState.batteryMaxChargeCurrentMa > 0) {
                        stringResource(R.string.battery_max_limits_format, uiState.batteryMaxChargeVoltageMv, uiState.batteryMaxChargeCurrentMa)
                    } else stringResource(R.string.battery_unknown)
                    BatteryMetricItem(stringResource(R.string.battery_max_limits), limitsStr, isColumnLayout = true)

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 6. Cycle Count
                    val cyclesStr = if (uiState.batteryCycles >= 0) stringResource(R.string.battery_cycles_format, uiState.batteryCycles) else stringResource(R.string.battery_unknown)
                    BatteryMetricItem(stringResource(R.string.battery_cycles), cyclesStr, isColumnLayout = true)

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 7. Design Capacity
                    val ratedStr = if (uiState.batteryRated > 0) stringResource(R.string.battery_capacity_format, uiState.batteryRated) else stringResource(R.string.battery_unknown)
                    BatteryMetricItem(stringResource(R.string.battery_rated_capacity), ratedStr, isColumnLayout = true)

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 8. Full Charge Capacity
                    val estStr = if (uiState.batteryEstimated > 0) stringResource(R.string.battery_capacity_format, uiState.batteryEstimated) else stringResource(R.string.battery_unknown)
                    BatteryMetricItem(stringResource(R.string.battery_estimated_capacity), estStr, isColumnLayout = true)

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 9. Calculated Capacity Health
                    val calcHealthStr = if (uiState.batteryCalculatedHealth > 0) stringResource(R.string.battery_health_format, uiState.batteryCalculatedHealth) else stringResource(R.string.battery_unknown)
                    BatteryMetricItem(stringResource(R.string.battery_calculated_health), calcHealthStr, isColumnLayout = true)

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 10. Health Capacity Index
                    val healthCapStr = if (uiState.batteryHealthCapIndex > 0) stringResource(R.string.battery_health_format, uiState.batteryHealthCapIndex) else stringResource(R.string.battery_unknown)
                    BatteryMetricItem(stringResource(R.string.battery_health_capacity_index), healthCapStr, isColumnLayout = true)

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 11. Overall Health
                    BatteryMetricItem(stringResource(R.string.battery_overall_health), uiState.batteryOverallHealth, isColumnLayout = true)

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 12. Temperature
                    val tempStr = if (uiState.batteryTemp > 0) stringResource(R.string.battery_temp_format, uiState.batteryTemp) else stringResource(R.string.battery_unknown)
                    BatteryMetricItem(stringResource(R.string.battery_temperature), tempStr, isColumnLayout = true)

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 13. Average Resistance
                    val resAvgStr = if (uiState.batteryResistanceAvg > 0) stringResource(R.string.battery_milli_format, uiState.batteryResistanceAvg) else stringResource(R.string.battery_unknown)
                    BatteryMetricItem(stringResource(R.string.battery_resistance_avg), resAvgStr, isColumnLayout = true)

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 14. Current Resistance
                    val resNowStr = if (uiState.batteryResistanceNow > 0) stringResource(R.string.battery_milli_format, uiState.batteryResistanceNow) else stringResource(R.string.battery_unknown)
                    BatteryMetricItem(stringResource(R.string.battery_resistance_now), resNowStr, isColumnLayout = true)

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 15. Health Impedance Index
                    val healthImpStr = if (uiState.batteryHealthImpIndex > 0) stringResource(R.string.battery_health_format, uiState.batteryHealthImpIndex) else stringResource(R.string.battery_unknown)
                    BatteryMetricItem(stringResource(R.string.battery_health_impedance_index), healthImpStr, isColumnLayout = true)

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 16. Serial Number
                    BatteryMetricItem(stringResource(R.string.battery_serial_number), uiState.batterySerialNumber, isColumnLayout = true)

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 17. Manufacture Date & Age
                    val ageStr = if (uiState.batteryFirstUsage != "N/A") stringResource(R.string.battery_first_usage_age_format, uiState.batteryFirstUsage, uiState.batteryAge) else stringResource(R.string.battery_unknown)
                    BatteryMetricItem(stringResource(R.string.battery_first_usage), ageStr, isColumnLayout = true)

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 18. AAFV Offset
                    val aafvStr = if (uiState.batteryAafvOffset >= 0) stringResource(R.string.battery_aafv_format, uiState.batteryAafvOffset) else stringResource(R.string.battery_unknown)
                    BatteryMetricItem(stringResource(R.string.battery_aafv_offset), aafvStr, isColumnLayout = true)
                }
            }

            // Section: Debug Logging (if DEBUG)
            if (BuildConfig.DEBUG) {
                Spacer(modifier = Modifier.height(20.dp))
                CategoryHeader(stringResource(R.string.category_debug), Icons.Outlined.BugReport)
                SettingsCard {
                    SwitchSettingItem(
                        title = stringResource(R.string.master_log),
                        summary = stringResource(R.string.master_log_summary),
                        checked = uiState.masterLog,
                        onCheckedChange = { onEvent(SettingsEvent.ToggleMasterLog(it)) }
                    )
                    AnimatedVisibility(visible = uiState.masterLog) {
                        Column {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            SwitchSettingItem(
                                title = stringResource(R.string.log_security),
                                summary = stringResource(R.string.log_security_summary),
                                checked = uiState.logSecurity,
                                onCheckedChange = { onEvent(SettingsEvent.ToggleLogSecurity(it)) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            SwitchSettingItem(
                                title = stringResource(R.string.log_interface),
                                summary = stringResource(R.string.log_interface_summary),
                                checked = uiState.logInterface,
                                onCheckedChange = { onEvent(SettingsEvent.ToggleLogInterface(it)) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            SwitchSettingItem(
                                title = stringResource(R.string.log_gestures),
                                summary = stringResource(R.string.log_gestures_summary),
                                checked = uiState.logGestures,
                                onCheckedChange = { onEvent(SettingsEvent.ToggleLogGestures(it)) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            SwitchSettingItem(
                                title = stringResource(R.string.log_quick_settings),
                                summary = stringResource(R.string.log_quick_settings_summary),
                                checked = uiState.logQuickSettings,
                                onCheckedChange = { onEvent(SettingsEvent.ToggleLogQuickSettings(it)) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // GitHub Repository Link
            OutlinedCard(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/hohojia886/PixelTweaks"))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "GitHub Repository",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "View source code & releases",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun BatteryMetricItem(title: String, value: String, isColumnLayout: Boolean = false) {
    if (isColumnLayout) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun CategoryHeader(text: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column {
            content()
        }
    }
}

@Composable
fun SwitchSettingItem(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isWarning: Boolean = false
) {
    val icon: (@Composable () -> Unit) = {
        if (checked) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                modifier = Modifier.size(SwitchDefaults.IconSize),
                tint = MaterialTheme.colorScheme.primary
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = null,
                modifier = Modifier.size(SwitchDefaults.IconSize),
                tint = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            thumbContent = icon,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedIconColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                uncheckedIconColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        )
    }
}


