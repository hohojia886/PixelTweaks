package io.github.hohojia886.pixeltweaks.presentation

import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hohojia886.pixeltweaks.data.repository.SettingsRepository
import io.github.hohojia886.pixeltweaks.utils.BatteryUtils
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SettingsViewModel: Manages UI state and business logic in Unidirectional Data Flow (UDF).
 * Encapsulates battery auto-refresh and security timeout Coroutine loops, completely decoupling
 * Activity lifecycle from background tasks.
 */
class SettingsViewModel(
    private val repository: SettingsRepository
) : ViewModel() {

    private val timeoutMillis = 3 * 60 * 1000L
    private val batteryRefreshIntervalMs = 2000L

    private val _uiState = MutableStateFlow(repository.loadInitialState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var batteryJob: Job? = null
    private var securityTimerJob: Job? = null

    init {
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        val currentTag = if (!currentLocales.isEmpty) currentLocales.get(0)?.toLanguageTag() ?: "" else ""
        if (currentTag.isNotEmpty()) {
            _uiState.update { it.copy(currentLanguageTag = currentTag) }
        }
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.ChangeLanguage -> {
                _uiState.update { it.copy(currentLanguageTag = event.languageTag) }
                repository.savePreference(PreferenceKeys.APP_LANGUAGE, event.languageTag)
                val localeList = if (event.languageTag.isEmpty()) {
                    LocaleListCompat.getEmptyLocaleList()
                } else {
                    LocaleListCompat.forLanguageTags(event.languageTag)
                }
                AppCompatDelegate.setApplicationLocales(localeList)
            }

            is SettingsEvent.ToggleBatteryInfo -> {
                _uiState.update { it.copy(enableBatteryInfo = event.enabled) }
                repository.savePreference(PreferenceKeys.ENABLE_BATTERY_INFO, event.enabled)
                if (event.enabled) {
                    startBatteryAutoRefresh()
                } else {
                    stopBatteryAutoRefresh()
                }
            }

            is SettingsEvent.RefreshBatteryInfo -> {
                fetchBatteryInfo()
            }

            is SettingsEvent.ToggleUnrestrictedScreenshots -> {
                _uiState.update { it.copy(unrestrictedScreenshots = event.enabled) }
                repository.savePreference(PreferenceKeys.ENABLE_UNRESTRICTED_SCREENSHOTS, event.enabled)
                repository.dispatchUpdateBroadcast(PreferenceKeys.ENABLE_UNRESTRICTED_SCREENSHOTS, event.enabled)
            }

            is SettingsEvent.ToggleEasyUnlock -> {
                _uiState.update { it.copy(easyUnlock = event.enabled) }
                repository.savePreference(PreferenceKeys.ENABLE_EASY_UNLOCK, event.enabled)
                repository.dispatchUpdateBroadcast(PreferenceKeys.ENABLE_EASY_UNLOCK, event.enabled)
                if (!event.enabled) {
                    _uiState.update { it.copy(easyUnlockReboot = false) }
                    repository.savePreference(PreferenceKeys.ENABLE_EASY_UNLOCK_REBOOT, false)
                    repository.dispatchUpdateBroadcast(PreferenceKeys.ENABLE_EASY_UNLOCK_REBOOT, false)
                }
            }

            is SettingsEvent.ToggleEasyUnlockReboot -> {
                _uiState.update { it.copy(easyUnlockReboot = event.enabled) }
                repository.savePreference(PreferenceKeys.ENABLE_EASY_UNLOCK_REBOOT, event.enabled)
                repository.dispatchUpdateBroadcast(PreferenceKeys.ENABLE_EASY_UNLOCK_REBOOT, event.enabled)
            }

            is SettingsEvent.ToggleAllowDowngrade -> {
                _uiState.update { it.copy(allowDowngrade = event.enabled) }
                repository.savePreference(PreferenceKeys.ALLOW_DOWNGRADE, event.enabled)
                if (event.enabled) {
                    repository.savePreference(PreferenceKeys.DOWNGRADE_TIMESTAMP, System.currentTimeMillis())
                }
                repository.dispatchUpdateBroadcast(PreferenceKeys.ALLOW_DOWNGRADE, event.enabled)
            }

            is SettingsEvent.ToggleBypassSignature -> {
                _uiState.update { it.copy(bypassSignature = event.enabled) }
                repository.savePreference(PreferenceKeys.BYPASS_SIGNATURE, event.enabled)
                if (event.enabled) {
                    repository.savePreference(PreferenceKeys.SIGNATURE_TIMESTAMP, System.currentTimeMillis())
                }
                repository.dispatchUpdateBroadcast(PreferenceKeys.BYPASS_SIGNATURE, event.enabled)
            }

            is SettingsEvent.ToggleClearAll -> {
                _uiState.update { it.copy(clearAll = event.enabled) }
                repository.savePreference(PreferenceKeys.ENABLE_CLEAR_ALL, event.enabled)
                repository.dispatchUpdateBroadcast(PreferenceKeys.ENABLE_CLEAR_ALL, event.enabled)
            }

            is SettingsEvent.ToggleTabletMode -> {
                Log.i("PXTK_Density", "onTabletModeChanged triggered: enabled=${event.enabled}")
                _uiState.update { it.copy(tabletMode = event.enabled) }
                repository.savePreference(PreferenceKeys.ENABLE_TABLET_MODE, event.enabled)
                repository.dispatchUpdateBroadcast(PreferenceKeys.ENABLE_TABLET_MODE, event.enabled)
            }

            is SettingsEvent.ToggleNetworkTraffic -> {
                _uiState.update { it.copy(networkTraffic = event.enabled) }
                repository.savePreference(PreferenceKeys.ENABLE_NETWORK_TRAFFIC, event.enabled)
                repository.dispatchUpdateBroadcast(PreferenceKeys.ENABLE_NETWORK_TRAFFIC, event.enabled)
            }

            is SettingsEvent.ChangeTrafficInterval -> {
                _uiState.update { it.copy(trafficInterval = event.interval) }
                repository.savePreference(PreferenceKeys.NETWORK_TRAFFIC_INTERVAL, event.interval)
                repository.dispatchUpdateBroadcast(PreferenceKeys.NETWORK_TRAFFIC_INTERVAL, event.interval)
            }

            is SettingsEvent.ChangeTrafficFontSize -> {
                _uiState.update { it.copy(trafficFontSize = event.size) }
                repository.savePreference(PreferenceKeys.NETWORK_TRAFFIC_FONT_SIZE, event.size)
                repository.dispatchUpdateBroadcast(PreferenceKeys.NETWORK_TRAFFIC_FONT_SIZE, event.size)
            }

            is SettingsEvent.ChangeTrafficThreshold -> {
                _uiState.update { it.copy(trafficThreshold = event.threshold) }
                repository.savePreference(PreferenceKeys.NETWORK_TRAFFIC_THRESHOLD, event.threshold)
                repository.dispatchUpdateBroadcast(PreferenceKeys.NETWORK_TRAFFIC_THRESHOLD, event.threshold)
            }

            is SettingsEvent.ToggleDtLauncher -> {
                _uiState.update { it.copy(dtLauncher = event.enabled) }
                repository.savePreference(PreferenceKeys.ENABLE_DT_LAUNCHER, event.enabled)
                repository.dispatchUpdateBroadcast(PreferenceKeys.ENABLE_DT_LAUNCHER, event.enabled)
            }

            is SettingsEvent.ToggleDtLockscreen -> {
                _uiState.update { it.copy(dtLockscreen = event.enabled) }
                repository.savePreference(PreferenceKeys.ENABLE_DT_LOCKSCREEN, event.enabled)
                repository.dispatchUpdateBroadcast(PreferenceKeys.ENABLE_DT_LOCKSCREEN, event.enabled)
            }

            is SettingsEvent.ToggleDtStatusbar -> {
                _uiState.update { it.copy(dtStatusbar = event.enabled) }
                repository.savePreference(PreferenceKeys.ENABLE_DT_STATUSBAR, event.enabled)
                repository.dispatchUpdateBroadcast(PreferenceKeys.ENABLE_DT_STATUSBAR, event.enabled)
            }

            is SettingsEvent.ToggleQsWifiFix -> {
                _uiState.update { it.copy(qsWifiFix = event.enabled) }
                repository.savePreference(PreferenceKeys.ENABLE_QS_WIFI_FIX, event.enabled)
                repository.dispatchUpdateBroadcast(PreferenceKeys.ENABLE_QS_WIFI_FIX, event.enabled)
            }

            is SettingsEvent.ToggleQsDataFix -> {
                _uiState.update { it.copy(qsDataFix = event.enabled) }
                repository.savePreference(PreferenceKeys.ENABLE_QS_DATA_FIX, event.enabled)
                repository.dispatchUpdateBroadcast(PreferenceKeys.ENABLE_QS_DATA_FIX, event.enabled)
            }

            is SettingsEvent.ToggleMasterLog -> {
                _uiState.update { it.copy(masterLog = event.enabled) }
                repository.savePreference(PreferenceKeys.ENABLE_MASTER_LOG, event.enabled)
                repository.dispatchUpdateBroadcast(PreferenceKeys.ENABLE_MASTER_LOG, event.enabled)
            }

            is SettingsEvent.ToggleLogSecurity -> {
                _uiState.update { it.copy(logSecurity = event.enabled) }
                repository.savePreference(PreferenceKeys.LOG_SECURITY, event.enabled)
                repository.dispatchUpdateBroadcast(PreferenceKeys.LOG_SECURITY, event.enabled)
            }

            is SettingsEvent.ToggleLogInterface -> {
                _uiState.update { it.copy(logInterface = event.enabled) }
                repository.savePreference(PreferenceKeys.LOG_INTERFACE, event.enabled)
                repository.dispatchUpdateBroadcast(PreferenceKeys.LOG_INTERFACE, event.enabled)
            }

            is SettingsEvent.ToggleLogGestures -> {
                _uiState.update { it.copy(logGestures = event.enabled) }
                repository.savePreference(PreferenceKeys.LOG_GESTURES, event.enabled)
                repository.dispatchUpdateBroadcast(PreferenceKeys.LOG_GESTURES, event.enabled)
            }

            is SettingsEvent.ToggleLogQuickSettings -> {
                _uiState.update { it.copy(logQuickSettings = event.enabled) }
                repository.savePreference(PreferenceKeys.LOG_QUICK_SETTINGS, event.enabled)
                repository.dispatchUpdateBroadcast(PreferenceKeys.LOG_QUICK_SETTINGS, event.enabled)
            }

            is SettingsEvent.OnResume -> {
                startBatteryAutoRefresh()
                startSecurityTimers()
                repository.syncAll()
            }

            is SettingsEvent.OnPause -> {
                stopBatteryAutoRefresh()
                stopSecurityTimers()
                repository.syncAll()
            }

            is SettingsEvent.OnScreenOff -> {
                Log.i("PXTK_Battery", "[SettingsViewModel] Screen OFF -> Pausing battery refresh")
                stopBatteryAutoRefresh()
            }

            is SettingsEvent.OnScreenOn -> {
                Log.i("PXTK_Battery", "[SettingsViewModel] Screen ON -> Resuming battery refresh")
                startBatteryAutoRefresh()
            }
        }
    }

    private fun startBatteryAutoRefresh() {
        if (!_uiState.value.enableBatteryInfo) return
        stopBatteryAutoRefresh()

        batteryJob = viewModelScope.launch {
            while (true) {
                fetchBatteryInfo()
                delay(batteryRefreshIntervalMs)
            }
        }
    }

    private fun stopBatteryAutoRefresh() {
        batteryJob?.cancel()
        batteryJob = null
    }

    private fun fetchBatteryInfo() {
        viewModelScope.launch {
            val data = withContext(Dispatchers.IO) {
                BatteryUtils.fetchBatteryInfoWithRoot()
            }
            _uiState.update {
                it.copy(
                    batteryStatus = data.status,
                    batteryVoltageMv = data.voltageMv,
                    batteryCurrentMa = data.currentMa,
                    batteryPowerWatts = data.powerWatts,
                    batteryCurrentChargeMah = data.currentChargeMah,
                    batteryMaxChargeVoltageMv = data.maxChargeVoltageMv,
                    batteryMaxChargeCurrentMa = data.maxChargeCurrentMa,
                    batteryCycles = data.cycles,
                    batteryRated = data.ratedMah,
                    batteryEstimated = data.estMah,
                    batteryCalculatedHealth = data.calculatedHealthCap,
                    batteryHealthCapIndex = data.healthCapIndex,
                    batteryOverallHealth = data.overallHealth,
                    batteryTemp = data.tempCelsius,
                    batteryResistanceAvg = data.resAvgMilli,
                    batteryResistanceNow = data.resNowMilli,
                    batteryHealthImpIndex = data.healthImpIndex,
                    batterySerialNumber = data.serialNumber,
                    batteryFirstUsage = data.firstUsageDate,
                    batteryAge = data.batteryAge,
                    batteryAafvOffset = data.aafvMilli
                )
            }
        }
    }

    private fun startSecurityTimers() {
        stopSecurityTimers()
        securityTimerJob = viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                val currentState = _uiState.value

                // Downgrade timer check
                if (currentState.allowDowngrade) {
                    val dgTime = repository.getDowngradeTimestamp()
                    val remaining = timeoutMillis - (now - dgTime)
                    if (remaining <= 0) {
                        _uiState.update { it.copy(allowDowngrade = false, allowDowngradeTimer = null) }
                        repository.savePreference(PreferenceKeys.ALLOW_DOWNGRADE, false)
                        repository.dispatchUpdateBroadcast(PreferenceKeys.ALLOW_DOWNGRADE, false)
                    } else {
                        _uiState.update { it.copy(allowDowngradeTimer = "${remaining / 1000}s") }
                    }
                } else {
                    if (currentState.allowDowngradeTimer != null) {
                        _uiState.update { it.copy(allowDowngradeTimer = null) }
                    }
                }

                // Signature timer check
                if (currentState.bypassSignature) {
                    val sigTime = repository.getSignatureTimestamp()
                    val remaining = timeoutMillis - (now - sigTime)
                    if (remaining <= 0) {
                        _uiState.update { it.copy(bypassSignature = false, bypassSignatureTimer = null) }
                        repository.savePreference(PreferenceKeys.BYPASS_SIGNATURE, false)
                        repository.dispatchUpdateBroadcast(PreferenceKeys.BYPASS_SIGNATURE, false)
                    } else {
                        _uiState.update { it.copy(bypassSignatureTimer = "${remaining / 1000}s") }
                    }
                } else {
                    if (currentState.bypassSignatureTimer != null) {
                        _uiState.update { it.copy(bypassSignatureTimer = null) }
                    }
                }

                delay(1000)
            }
        }
    }

    private fun stopSecurityTimers() {
        securityTimerJob?.cancel()
        securityTimerJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopBatteryAutoRefresh()
        stopSecurityTimers()
    }
}
