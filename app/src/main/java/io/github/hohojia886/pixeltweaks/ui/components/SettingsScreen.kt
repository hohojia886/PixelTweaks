package io.github.hohojia886.pixeltweaks.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.hohojia886.pixeltweaks.BuildConfig
import io.github.hohojia886.pixeltweaks.R
import io.github.hohojia886.pixeltweaks.presentation.SettingsEvent
import io.github.hohojia886.pixeltweaks.presentation.SettingsUiState
import io.github.hohojia886.pixeltweaks.utils.DensityUtils
import io.github.hohojia886.pixeltweaks.utils.isQsDataFixSupported

/**
 * Main Jetpack Compose screen for the Settings UI.
 *
 * @param uiState Current immutable [SettingsUiState] state instance.
 * @param onEvent Event callback lambda to dispatch user intents to [io.github.hohojia886.pixeltweaks.presentation.SettingsViewModel].
 */
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
                    title = stringResource(R.string.hide_vpn_icon),
                    summary = stringResource(R.string.hide_vpn_icon_summary),
                    checked = uiState.hideVpnIcon,
                    onCheckedChange = { onEvent(SettingsEvent.ToggleHideVpnIcon(it)) }
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SwitchSettingItem(
                    title = stringResource(R.string.statusbar_battery_percent),
                    summary = stringResource(R.string.statusbar_battery_percent_summary),
                    checked = uiState.statusbarBatteryPercent,
                    onCheckedChange = { onEvent(SettingsEvent.ToggleStatusbarBatteryPercent(it)) }
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

            CategoryHeader(stringResource(R.string.category_quick_settings), Icons.Outlined.Tune)
            SettingsCard {
                SwitchSettingItem(
                    title = stringResource(R.string.qs_wifi_fix),
                    summary = stringResource(R.string.qs_wifi_fix_summary),
                    checked = uiState.qsWifiFix,
                    onCheckedChange = { onEvent(SettingsEvent.ToggleQsWifiFix(it)) }
                )
                if (isQsDataFixSupported()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SwitchSettingItem(
                        title = stringResource(R.string.qs_data_fix),
                        summary = stringResource(R.string.qs_data_fix_summary),
                        checked = uiState.qsDataFix,
                        onCheckedChange = { onEvent(SettingsEvent.ToggleQsDataFix(it)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            BatterySettingsSection(uiState = uiState, onEvent = onEvent)

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

            Spacer(modifier = Modifier.height(20.dp))

            CategoryHeader(stringResource(R.string.category_others), Icons.Outlined.MoreHoriz)
            OthersCard(
                currentLanguageTag = uiState.currentLanguageTag,
                onLanguageSelected = { tag -> onEvent(SettingsEvent.ChangeLanguage(tag)) }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
