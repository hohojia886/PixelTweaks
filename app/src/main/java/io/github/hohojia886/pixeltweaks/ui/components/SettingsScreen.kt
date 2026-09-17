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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    // System & Security
    unrestrictedScreenshots: Boolean,
    onUnrestrictedScreenshotsChanged: (Boolean) -> Unit,
    easyUnlock: Boolean,
    onEasyUnlockChanged: (Boolean) -> Unit,
    easyUnlockReboot: Boolean,
    onEasyUnlockRebootChanged: (Boolean) -> Unit,
    allowDowngrade: Boolean,
    allowDowngradeTimer: String?,
    onAllowDowngradeChanged: (Boolean) -> Unit,
    bypassSignature: Boolean,
    bypassSignatureTimer: String?,
    onBypassSignatureChanged: (Boolean) -> Unit,
    // Interface
    clearAll: Boolean,
    onClearAllChanged: (Boolean) -> Unit,
    networkTraffic: Boolean,
    onNetworkTrafficChanged: (Boolean) -> Unit,
    trafficInterval: Int,
    onTrafficIntervalChanged: (Int) -> Unit,
    trafficFontSize: Float,
    onTrafficFontSizeChanged: (Float) -> Unit,
    trafficThreshold: Int,
    onTrafficThresholdChanged: (Int) -> Unit,
    // Gestures
    dtLauncher: Boolean,
    onDtLauncherChanged: (Boolean) -> Unit,
    dtLockscreen: Boolean,
    onDtLockscreenChanged: (Boolean) -> Unit,
    dtStatusbar: Boolean,
    onDtStatusbarChanged: (Boolean) -> Unit,
    // Quick Settings
    qsWifiFix: Boolean,
    onQsWifiFixChanged: (Boolean) -> Unit,
    qsDataFix: Boolean,
    onQsDataFixChanged: (Boolean) -> Unit,
    // Debug
    masterLog: Boolean,
    onMasterLogChanged: (Boolean) -> Unit,
    logSecurity: Boolean,
    onLogSecurityChanged: (Boolean) -> Unit,
    logInterface: Boolean,
    onLogInterfaceChanged: (Boolean) -> Unit,
    logGestures: Boolean,
    onLogGesturesChanged: (Boolean) -> Unit,
    logQuickSettings: Boolean,
    onLogQuickSettingsChanged: (Boolean) -> Unit,
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
                    checked = unrestrictedScreenshots,
                    onCheckedChange = onUnrestrictedScreenshotsChanged
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SwitchSettingItem(
                    title = stringResource(R.string.easy_unlock),
                    summary = stringResource(R.string.easy_unlock_summary),
                    checked = easyUnlock,
                    onCheckedChange = onEasyUnlockChanged
                )
                AnimatedVisibility(visible = easyUnlock) {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        SwitchSettingItem(
                            title = stringResource(R.string.easy_unlock_reboot),
                            summary = stringResource(R.string.easy_unlock_reboot_summary),
                            checked = easyUnlockReboot,
                            onCheckedChange = onEasyUnlockRebootChanged
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SwitchSettingItem(
                    title = if (allowDowngradeTimer != null) "${stringResource(R.string.allow_downgrade)} ($allowDowngradeTimer)" else stringResource(R.string.allow_downgrade),
                    summary = stringResource(R.string.allow_downgrade_summary),
                    checked = allowDowngrade,
                    onCheckedChange = onAllowDowngradeChanged,
                    isWarning = allowDowngradeTimer != null
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SwitchSettingItem(
                    title = if (bypassSignatureTimer != null) "${stringResource(R.string.bypass_signature)} ($bypassSignatureTimer)" else stringResource(R.string.bypass_signature),
                    summary = stringResource(R.string.bypass_signature_summary),
                    checked = bypassSignature,
                    onCheckedChange = onBypassSignatureChanged,
                    isWarning = bypassSignatureTimer != null
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section: Interface & Status Bar
            CategoryHeader(stringResource(R.string.category_interface), Icons.Outlined.Settings)
            SettingsCard {
                SwitchSettingItem(
                    title = stringResource(R.string.clear_all_button),
                    summary = stringResource(R.string.clear_all_button_summary),
                    checked = clearAll,
                    onCheckedChange = onClearAllChanged
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SwitchSettingItem(
                    title = stringResource(R.string.network_traffic),
                    summary = stringResource(R.string.network_traffic_summary),
                    checked = networkTraffic,
                    onCheckedChange = onNetworkTrafficChanged
                )
                AnimatedVisibility(visible = networkTraffic) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        HorizontalDivider(
                            modifier = Modifier.padding(bottom = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        
                        // Interval Slider
                        val intervalValues = listOf(1, 2, 3, 4, 5)
                        Text(
                            text = context.getString(R.string.traffic_interval_format, trafficInterval),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Slider(
                            value = intervalValues.indexOf(trafficInterval).coerceAtLeast(0).toFloat(),
                            onValueChange = { onTrafficIntervalChanged(intervalValues[it.toInt()]) },
                            valueRange = 0f..4f,
                            steps = 3
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Font Size Slider
                        val fontValues = listOf(6f, 7f, 8f, 9f, 10f)
                        Text(
                            text = context.getString(R.string.traffic_font_format, trafficFontSize.toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Slider(
                            value = fontValues.indexOf(trafficFontSize).coerceAtLeast(0).toFloat(),
                            onValueChange = { onTrafficFontSizeChanged(fontValues[it.toInt()]) },
                            valueRange = 0f..4f,
                            steps = 3
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Threshold Slider
                        val thresholdValues = listOf(0, 1, 10, 100, 1024)
                        val thresholdText = when (trafficThreshold) {
                            0 -> stringResource(R.string.traffic_threshold_always)
                            1024 -> stringResource(R.string.traffic_threshold_mb)
                            else -> stringResource(R.string.traffic_threshold_kb, trafficThreshold)
                        }
                        Text(
                            text = thresholdText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Slider(
                            value = thresholdValues.indexOf(trafficThreshold).coerceAtLeast(0).toFloat(),
                            onValueChange = { onTrafficThresholdChanged(thresholdValues[it.toInt()]) },
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
                    checked = dtLauncher,
                    onCheckedChange = onDtLauncherChanged
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SwitchSettingItem(
                    title = stringResource(R.string.dt_lockscreen),
                    summary = stringResource(R.string.dt_lockscreen_summary),
                    checked = dtLockscreen,
                    onCheckedChange = onDtLockscreenChanged
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SwitchSettingItem(
                    title = stringResource(R.string.dt_statusbar),
                    summary = stringResource(R.string.dt_statusbar_summary),
                    checked = dtStatusbar,
                    onCheckedChange = onDtStatusbarChanged
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section: Quick Settings
            CategoryHeader(stringResource(R.string.category_quick_settings), Icons.Outlined.Tune)
            SettingsCard {
                SwitchSettingItem(
                    title = stringResource(R.string.qs_wifi_fix),
                    summary = stringResource(R.string.qs_wifi_fix_summary),
                    checked = qsWifiFix,
                    onCheckedChange = onQsWifiFixChanged
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SwitchSettingItem(
                    title = stringResource(R.string.qs_data_fix),
                    summary = stringResource(R.string.qs_data_fix_summary),
                    checked = qsDataFix,
                    onCheckedChange = onQsDataFixChanged
                )
            }

            // Section: Debug Logging (if DEBUG)
            if (BuildConfig.DEBUG) {
                Spacer(modifier = Modifier.height(20.dp))
                CategoryHeader(stringResource(R.string.category_debug), Icons.Outlined.BugReport)
                SettingsCard {
                    SwitchSettingItem(
                        title = stringResource(R.string.master_log),
                        summary = stringResource(R.string.master_log_summary),
                        checked = masterLog,
                        onCheckedChange = onMasterLogChanged
                    )
                    AnimatedVisibility(visible = masterLog) {
                        Column {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            SwitchSettingItem(
                                title = stringResource(R.string.log_security),
                                summary = stringResource(R.string.log_security_summary),
                                checked = logSecurity,
                                onCheckedChange = onLogSecurityChanged
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            SwitchSettingItem(
                                title = stringResource(R.string.log_interface),
                                summary = stringResource(R.string.log_interface_summary),
                                checked = logInterface,
                                onCheckedChange = onLogInterfaceChanged
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            SwitchSettingItem(
                                title = stringResource(R.string.log_gestures),
                                summary = stringResource(R.string.log_gestures_summary),
                                checked = logGestures,
                                onCheckedChange = onLogGesturesChanged
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            SwitchSettingItem(
                                title = stringResource(R.string.log_quick_settings),
                                summary = stringResource(R.string.log_quick_settings_summary),
                                checked = logQuickSettings,
                                onCheckedChange = onLogQuickSettingsChanged
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
                color = if (isWarning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
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
