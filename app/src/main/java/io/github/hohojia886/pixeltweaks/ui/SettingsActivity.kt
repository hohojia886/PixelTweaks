package io.github.hohojia886.pixeltweaks.ui

import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.hohojia886.pixeltweaks.ui.components.SettingsScreen
import io.github.hohojia886.pixeltweaks.ui.theme.PixelTweaksTheme
import io.github.hohojia886.pixeltweaks.utils.BatteryUtils
import io.github.hohojia886.pixeltweaks.utils.IpcManager
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys

/**
 * SettingsActivity: The main configuration interface for PixelTweaks built with Jetpack Compose & Material 3.
 * Manages dual-preference synchronization (CE/DE storage), real-time IPC broadcasts
 * for setting updates, and UI state orchestration for all functional modules.
 */
class SettingsActivity : ComponentActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    private val timeoutMillis = 3 * 60 * 1000L

    // System & Security State
    private var unrestrictedScreenshots by mutableStateOf(true)
    private var easyUnlock by mutableStateOf(true)
    private var easyUnlockReboot by mutableStateOf(false)
    private var allowDowngrade by mutableStateOf(false)
    private var allowDowngradeTimer by mutableStateOf<String?>(null)
    private var bypassSignature by mutableStateOf(false)
    private var bypassSignatureTimer by mutableStateOf<String?>(null)

    // Battery Info State (18 Metrics & Toggle)
    private var enableBatteryInfo by mutableStateOf(false)
    private var batteryStatus by mutableStateOf("N/A")
    private var batteryVoltageMv by mutableIntStateOf(-1)
    private var batteryCurrentMa by mutableIntStateOf(0)
    private var batteryPowerWatts by mutableFloatStateOf(0f)
    private var batteryCurrentChargeMah by mutableIntStateOf(-1)
    private var batteryMaxChargeVoltageMv by mutableIntStateOf(-1)
    private var batteryMaxChargeCurrentMa by mutableIntStateOf(-1)

    private var batteryCycles by mutableIntStateOf(-1)
    private var batteryRated by mutableIntStateOf(-1)
    private var batteryEstimated by mutableIntStateOf(-1)
    private var batteryCalculatedHealth by mutableIntStateOf(-1)
    private var batteryHealthCapIndex by mutableIntStateOf(-1)
    private var batteryOverallHealth by mutableStateOf("N/A")
    private var batteryTemp by mutableFloatStateOf(-1f)
    private var batteryResistanceAvg by mutableFloatStateOf(-1f)
    private var batteryResistanceNow by mutableFloatStateOf(-1f)
    private var batteryHealthImpIndex by mutableIntStateOf(-1)
    private var batterySerialNumber by mutableStateOf("N/A")
    private var batteryFirstUsage by mutableStateOf("N/A")
    private var batteryAge by mutableStateOf("N/A")
    private var batteryAafvOffset by mutableIntStateOf(-1)

    // Interface State
    private var clearAll by mutableStateOf(true)
    private var tabletMode by mutableStateOf(false)
    private var networkTraffic by mutableStateOf(true)
    private var trafficInterval by mutableIntStateOf(1)
    private var trafficFontSize by mutableFloatStateOf(8f)
    private var trafficThreshold by mutableIntStateOf(1)

    // Gestures State
    private var dtLauncher by mutableStateOf(true)
    private var dtLockscreen by mutableStateOf(true)
    private var dtStatusbar by mutableStateOf(true)

    // Quick Settings State
    private var qsWifiFix by mutableStateOf(true)
    private var qsDataFix by mutableStateOf(true)

    // Debug Logging State (4 Categories)
    private var masterLog by mutableStateOf(false)
    private var logSecurity by mutableStateOf(true)
    private var logInterface by mutableStateOf(true)
    private var logGestures by mutableStateOf(true)
    private var logQuickSettings by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val deContext = createDeviceProtectedStorageContext()
        val dePrefs = deContext.getSharedPreferences(IpcManager.PREF_NAME, MODE_PRIVATE)
        val cePrefs = getSharedPreferences(IpcManager.PREF_NAME, MODE_PRIVATE)

        // Initialize / sync default preferences
        initPreferences(cePrefs, dePrefs)
        loadState(dePrefs)

        setupTimers(cePrefs, dePrefs)

        setContent {
            PixelTweaksTheme {
                SettingsScreen(
                    enableBatteryInfo = enableBatteryInfo,
                    onEnableBatteryInfoChanged = {
                        enableBatteryInfo = it
                        saveDoublePref(PreferenceKeys.ENABLE_BATTERY_INFO, it, cePrefs, dePrefs)
                        if (it) {
                            startBatteryAutoRefresh()
                        } else {
                            stopBatteryAutoRefresh()
                        }
                    },
                    batteryStatus = batteryStatus,
                    batteryVoltageMv = batteryVoltageMv,
                    batteryCurrentMa = batteryCurrentMa,
                    batteryPowerWatts = batteryPowerWatts,
                    batteryCurrentChargeMah = batteryCurrentChargeMah,
                    batteryMaxChargeVoltageMv = batteryMaxChargeVoltageMv,
                    batteryMaxChargeCurrentMa = batteryMaxChargeCurrentMa,
                    batteryCycles = batteryCycles,
                    batteryRated = batteryRated,
                    batteryEstimated = batteryEstimated,
                    batteryCalculatedHealth = batteryCalculatedHealth,
                    batteryHealthCapIndex = batteryHealthCapIndex,
                    batteryOverallHealth = batteryOverallHealth,
                    batteryTemp = batteryTemp,
                    batteryResistanceAvg = batteryResistanceAvg,
                    batteryResistanceNow = batteryResistanceNow,
                    batteryHealthImpIndex = batteryHealthImpIndex,
                    batterySerialNumber = batterySerialNumber,
                    batteryFirstUsage = batteryFirstUsage,
                    batteryAge = batteryAge,
                    batteryAafvOffset = batteryAafvOffset,
                    unrestrictedScreenshots = unrestrictedScreenshots,
                    onUnrestrictedScreenshotsChanged = {
                        unrestrictedScreenshots = it
                        saveDoublePref(PreferenceKeys.ENABLE_UNRESTRICTED_SCREENSHOTS, it, cePrefs, dePrefs)
                        IpcManager.sendUpdateBroadcast(this, PreferenceKeys.ENABLE_UNRESTRICTED_SCREENSHOTS, it)
                    },
                    easyUnlock = easyUnlock,
                    onEasyUnlockChanged = {
                        easyUnlock = it
                        saveDoublePref(PreferenceKeys.ENABLE_EASY_UNLOCK, it, cePrefs, dePrefs)
                        IpcManager.sendUpdateBroadcast(this, PreferenceKeys.ENABLE_EASY_UNLOCK, it)
                        if (!it) {
                            easyUnlockReboot = false
                            saveDoublePref(PreferenceKeys.ENABLE_EASY_UNLOCK_REBOOT, false, cePrefs, dePrefs)
                            IpcManager.sendUpdateBroadcast(this, PreferenceKeys.ENABLE_EASY_UNLOCK_REBOOT, false)
                        }
                    },
                    easyUnlockReboot = easyUnlockReboot,
                    onEasyUnlockRebootChanged = {
                        easyUnlockReboot = it
                        saveDoublePref(PreferenceKeys.ENABLE_EASY_UNLOCK_REBOOT, it, cePrefs, dePrefs)
                        IpcManager.sendUpdateBroadcast(this, PreferenceKeys.ENABLE_EASY_UNLOCK_REBOOT, it)
                    },
                    allowDowngrade = allowDowngrade,
                    allowDowngradeTimer = allowDowngradeTimer,
                    onAllowDowngradeChanged = {
                        allowDowngrade = it
                        saveDoublePref(PreferenceKeys.ALLOW_DOWNGRADE, it, cePrefs, dePrefs)
                        if (it) {
                            saveDoublePref(PreferenceKeys.DOWNGRADE_TIMESTAMP, System.currentTimeMillis(), cePrefs, dePrefs)
                        }
                        IpcManager.sendUpdateBroadcast(this, PreferenceKeys.ALLOW_DOWNGRADE, it)
                    },
                    bypassSignature = bypassSignature,
                    bypassSignatureTimer = bypassSignatureTimer,
                    onBypassSignatureChanged = {
                        bypassSignature = it
                        saveDoublePref(PreferenceKeys.BYPASS_SIGNATURE, it, cePrefs, dePrefs)
                        if (it) {
                            saveDoublePref(PreferenceKeys.SIGNATURE_TIMESTAMP, System.currentTimeMillis(), cePrefs, dePrefs)
                        }
                        IpcManager.sendUpdateBroadcast(this, PreferenceKeys.BYPASS_SIGNATURE, it)
                    },
                    clearAll = clearAll,
                    onClearAllChanged = {
                        clearAll = it
                        saveDoublePref(PreferenceKeys.ENABLE_CLEAR_ALL, it, cePrefs, dePrefs)
                        IpcManager.sendUpdateBroadcast(this, PreferenceKeys.ENABLE_CLEAR_ALL, it)
                    },
                    tabletMode = tabletMode,
                    onTabletModeChanged = { enabled ->
                        Log.i("PXTK_Density", "onTabletModeChanged triggered: enabled=$enabled")
                        tabletMode = enabled
                        saveDoublePref(PreferenceKeys.ENABLE_TABLET_MODE, enabled, cePrefs, dePrefs)
                        IpcManager.sendUpdateBroadcast(this, PreferenceKeys.ENABLE_TABLET_MODE, enabled)
                    },
                    networkTraffic = networkTraffic,
                    onNetworkTrafficChanged = {
                        networkTraffic = it
                        saveDoublePref(PreferenceKeys.ENABLE_NETWORK_TRAFFIC, it, cePrefs, dePrefs)
                        IpcManager.sendUpdateBroadcast(this, PreferenceKeys.ENABLE_NETWORK_TRAFFIC, it)
                    },
                    trafficInterval = trafficInterval,
                    onTrafficIntervalChanged = {
                        trafficInterval = it
                        saveDoublePref(PreferenceKeys.NETWORK_TRAFFIC_INTERVAL, it, cePrefs, dePrefs)
                        IpcManager.sendUpdateBroadcast(this, PreferenceKeys.NETWORK_TRAFFIC_INTERVAL, it)
                    },
                    trafficFontSize = trafficFontSize,
                    onTrafficFontSizeChanged = {
                        trafficFontSize = it
                        saveDoublePref(PreferenceKeys.NETWORK_TRAFFIC_FONT_SIZE, it, cePrefs, dePrefs)
                        IpcManager.sendUpdateBroadcast(this, PreferenceKeys.NETWORK_TRAFFIC_FONT_SIZE, it)
                    },
                    trafficThreshold = trafficThreshold,
                    onTrafficThresholdChanged = {
                        trafficThreshold = it
                        saveDoublePref(PreferenceKeys.NETWORK_TRAFFIC_THRESHOLD, it, cePrefs, dePrefs)
                        IpcManager.sendUpdateBroadcast(this, PreferenceKeys.NETWORK_TRAFFIC_THRESHOLD, it)
                    },
                    dtLauncher = dtLauncher,
                    onDtLauncherChanged = {
                        dtLauncher = it
                        saveDoublePref(PreferenceKeys.ENABLE_DT_LAUNCHER, it, cePrefs, dePrefs)
                        IpcManager.sendUpdateBroadcast(this, PreferenceKeys.ENABLE_DT_LAUNCHER, it)
                    },
                    dtLockscreen = dtLockscreen,
                    onDtLockscreenChanged = {
                        dtLockscreen = it
                        saveDoublePref(PreferenceKeys.ENABLE_DT_LOCKSCREEN, it, cePrefs, dePrefs)
                        IpcManager.sendUpdateBroadcast(this, PreferenceKeys.ENABLE_DT_LOCKSCREEN, it)
                    },
                    dtStatusbar = dtStatusbar,
                    onDtStatusbarChanged = {
                        dtStatusbar = it
                        saveDoublePref(PreferenceKeys.ENABLE_DT_STATUSBAR, it, cePrefs, dePrefs)
                        IpcManager.sendUpdateBroadcast(this, PreferenceKeys.ENABLE_DT_STATUSBAR, it)
                    },
                    qsWifiFix = qsWifiFix,
                    onQsWifiFixChanged = {
                        qsWifiFix = it
                        saveDoublePref(PreferenceKeys.ENABLE_QS_WIFI_FIX, it, cePrefs, dePrefs)
                        IpcManager.sendUpdateBroadcast(this, PreferenceKeys.ENABLE_QS_WIFI_FIX, it)
                    },
                    qsDataFix = qsDataFix,
                    onQsDataFixChanged = {
                        qsDataFix = it
                        saveDoublePref(PreferenceKeys.ENABLE_QS_DATA_FIX, it, cePrefs, dePrefs)
                        IpcManager.sendUpdateBroadcast(this, PreferenceKeys.ENABLE_QS_DATA_FIX, it)
                    },
                    masterLog = masterLog,
                    onMasterLogChanged = {
                        masterLog = it
                        saveDoublePref(PreferenceKeys.ENABLE_MASTER_LOG, it, cePrefs, dePrefs)
                        IpcManager.sendUpdateBroadcast(this, PreferenceKeys.ENABLE_MASTER_LOG, it)
                    },
                    logSecurity = logSecurity,
                    onLogSecurityChanged = {
                        logSecurity = it
                        saveDoublePref(PreferenceKeys.LOG_SECURITY, it, cePrefs, dePrefs)
                        IpcManager.sendUpdateBroadcast(this, PreferenceKeys.LOG_SECURITY, it)
                    },
                    logInterface = logInterface,
                    onLogInterfaceChanged = {
                        logInterface = it
                        saveDoublePref(PreferenceKeys.LOG_INTERFACE, it, cePrefs, dePrefs)
                        IpcManager.sendUpdateBroadcast(this, PreferenceKeys.LOG_INTERFACE, it)
                    },
                    logGestures = logGestures,
                    onLogGesturesChanged = {
                        logGestures = it
                        saveDoublePref(PreferenceKeys.LOG_GESTURES, it, cePrefs, dePrefs)
                        IpcManager.sendUpdateBroadcast(this, PreferenceKeys.LOG_GESTURES, it)
                    },
                    logQuickSettings = logQuickSettings,
                    onLogQuickSettingsChanged = {
                        logQuickSettings = it
                        saveDoublePref(PreferenceKeys.LOG_QUICK_SETTINGS, it, cePrefs, dePrefs)
                        IpcManager.sendUpdateBroadcast(this, PreferenceKeys.LOG_QUICK_SETTINGS, it)
                    }
                )
            }
        }
    }

    private fun initPreferences(cePrefs: SharedPreferences, dePrefs: SharedPreferences) {
        if (!cePrefs.contains(PreferenceKeys.ENABLE_NETWORK_TRAFFIC) && !dePrefs.contains(PreferenceKeys.ENABLE_NETWORK_TRAFFIC)) {
            saveDoublePref(PreferenceKeys.ENABLE_NETWORK_TRAFFIC, true, cePrefs, dePrefs)
            saveDoublePref(PreferenceKeys.NETWORK_TRAFFIC_INTERVAL, 1, cePrefs, dePrefs)
            saveDoublePref(PreferenceKeys.NETWORK_TRAFFIC_FONT_SIZE, 8f, cePrefs, dePrefs)
            saveDoublePref(PreferenceKeys.NETWORK_TRAFFIC_THRESHOLD, 1, cePrefs, dePrefs)
            saveDoublePref(PreferenceKeys.ENABLE_EASY_UNLOCK, true, cePrefs, dePrefs)
            saveDoublePref(PreferenceKeys.ENABLE_EASY_UNLOCK_REBOOT, false, cePrefs, dePrefs)
            saveDoublePref(PreferenceKeys.ENABLE_QS_WIFI_FIX, true, cePrefs, dePrefs)
            saveDoublePref(PreferenceKeys.ENABLE_QS_DATA_FIX, true, cePrefs, dePrefs)
            saveDoublePref(PreferenceKeys.ENABLE_CLEAR_ALL, true, cePrefs, dePrefs)
            saveDoublePref(PreferenceKeys.ENABLE_TABLET_MODE, false, cePrefs, dePrefs)
            saveDoublePref(PreferenceKeys.ENABLE_BATTERY_INFO, false, cePrefs, dePrefs)
            saveDoublePref(PreferenceKeys.ENABLE_DT_LAUNCHER, true, cePrefs, dePrefs)
            saveDoublePref(PreferenceKeys.ENABLE_DT_LOCKSCREEN, true, cePrefs, dePrefs)
            saveDoublePref(PreferenceKeys.ENABLE_DT_STATUSBAR, true, cePrefs, dePrefs)
            saveDoublePref(PreferenceKeys.ENABLE_UNRESTRICTED_SCREENSHOTS, true, cePrefs, dePrefs)
            saveDoublePref(PreferenceKeys.ALLOW_DOWNGRADE, false, cePrefs, dePrefs)
            saveDoublePref(PreferenceKeys.BYPASS_SIGNATURE, false, cePrefs, dePrefs)
            saveDoublePref(PreferenceKeys.LOG_SECURITY, true, cePrefs, dePrefs)
            saveDoublePref(PreferenceKeys.LOG_INTERFACE, true, cePrefs, dePrefs)
            saveDoublePref(PreferenceKeys.LOG_GESTURES, true, cePrefs, dePrefs)
            saveDoublePref(PreferenceKeys.LOG_QUICK_SETTINGS, true, cePrefs, dePrefs)
            IpcManager.syncAllSettings(this, dePrefs)
        } else {
            if (cePrefs.all.isNotEmpty() && dePrefs.all.isEmpty()) {
                cePrefs.all.forEach { (k, v) -> if (v != null) saveDoublePref(k, v, cePrefs, dePrefs) }
                IpcManager.syncAllSettings(this, dePrefs)
            } else if (dePrefs.all.isNotEmpty() && cePrefs.all.isEmpty()) {
                dePrefs.all.forEach { (k, v) -> if (v != null) saveDoublePref(k, v, cePrefs, dePrefs) }
                IpcManager.syncAllSettings(this, dePrefs)
            }
        }
    }

    private fun loadState(dePrefs: SharedPreferences) {
        enableBatteryInfo = dePrefs.getBoolean(PreferenceKeys.ENABLE_BATTERY_INFO, false)
        unrestrictedScreenshots = dePrefs.getBoolean(PreferenceKeys.ENABLE_UNRESTRICTED_SCREENSHOTS, true)
        easyUnlock = dePrefs.getBoolean(PreferenceKeys.ENABLE_EASY_UNLOCK, true)
        easyUnlockReboot = dePrefs.getBoolean(PreferenceKeys.ENABLE_EASY_UNLOCK_REBOOT, false)
        allowDowngrade = dePrefs.getBoolean(PreferenceKeys.ALLOW_DOWNGRADE, false)
        bypassSignature = dePrefs.getBoolean(PreferenceKeys.BYPASS_SIGNATURE, false)

        clearAll = dePrefs.getBoolean(PreferenceKeys.ENABLE_CLEAR_ALL, true)
        tabletMode = dePrefs.getBoolean(PreferenceKeys.ENABLE_TABLET_MODE, false)
        networkTraffic = dePrefs.getBoolean(PreferenceKeys.ENABLE_NETWORK_TRAFFIC, true)
        trafficInterval = dePrefs.getInt(PreferenceKeys.NETWORK_TRAFFIC_INTERVAL, 1)
        trafficFontSize = dePrefs.getFloat(PreferenceKeys.NETWORK_TRAFFIC_FONT_SIZE, 8f)
        trafficThreshold = dePrefs.getInt(PreferenceKeys.NETWORK_TRAFFIC_THRESHOLD, 1)

        dtLauncher = dePrefs.getBoolean(PreferenceKeys.ENABLE_DT_LAUNCHER, true)
        dtLockscreen = dePrefs.getBoolean(PreferenceKeys.ENABLE_DT_LOCKSCREEN, true)
        dtStatusbar = dePrefs.getBoolean(PreferenceKeys.ENABLE_DT_STATUSBAR, true)

        qsWifiFix = dePrefs.getBoolean(PreferenceKeys.ENABLE_QS_WIFI_FIX, true)
        qsDataFix = dePrefs.getBoolean(PreferenceKeys.ENABLE_QS_DATA_FIX, true)

        masterLog = dePrefs.getBoolean(PreferenceKeys.ENABLE_MASTER_LOG, false)
        logSecurity = dePrefs.getBoolean(PreferenceKeys.LOG_SECURITY, true)
        logInterface = dePrefs.getBoolean(PreferenceKeys.LOG_INTERFACE, true)
        logGestures = dePrefs.getBoolean(PreferenceKeys.LOG_GESTURES, true)
        logQuickSettings = dePrefs.getBoolean(PreferenceKeys.LOG_QUICK_SETTINGS, true)
    }

    private fun setupTimers(cePrefs: SharedPreferences, dePrefs: SharedPreferences) {
        timerRunnable = object : Runnable {
            override fun run() {
                val now = System.currentTimeMillis()

                if (allowDowngrade) {
                    val dgTime = dePrefs.getLong(PreferenceKeys.DOWNGRADE_TIMESTAMP, 0L)
                    val remaining = timeoutMillis - (now - dgTime)
                    if (remaining <= 0) {
                        allowDowngrade = false
                        allowDowngradeTimer = null
                        saveDoublePref(PreferenceKeys.ALLOW_DOWNGRADE, false, cePrefs, dePrefs)
                        IpcManager.sendUpdateBroadcast(this@SettingsActivity, PreferenceKeys.ALLOW_DOWNGRADE, false)
                    } else {
                        allowDowngradeTimer = "${remaining / 1000}s"
                    }
                } else {
                    allowDowngradeTimer = null
                }

                if (bypassSignature) {
                    val sigTime = dePrefs.getLong(PreferenceKeys.SIGNATURE_TIMESTAMP, 0L)
                    val remaining = timeoutMillis - (now - sigTime)
                    if (remaining <= 0) {
                        bypassSignature = false
                        bypassSignatureTimer = null
                        saveDoublePref(PreferenceKeys.BYPASS_SIGNATURE, false, cePrefs, dePrefs)
                        IpcManager.sendUpdateBroadcast(this@SettingsActivity, PreferenceKeys.BYPASS_SIGNATURE, false)
                    } else {
                        bypassSignatureTimer = "${remaining / 1000}s"
                    }
                } else {
                    bypassSignatureTimer = null
                }

                handler.postDelayed(this, 1000)
            }
        }
        handler.post(timerRunnable!!)
    }

    private var batteryTimerHandler: Handler? = null
    private var batteryTimerRunnable: Runnable? = null
    private val batteryRefreshIntervalMs = 2000L // Auto-refresh battery stats every 2 seconds

    override fun onResume() {
        super.onResume()
        startBatteryAutoRefresh()
    }

    override fun onPause() {
        super.onPause()
        stopBatteryAutoRefresh()
        val deContext = createDeviceProtectedStorageContext()
        val dePrefs = deContext.getSharedPreferences(IpcManager.PREF_NAME, MODE_PRIVATE)
        IpcManager.syncAllSettings(this, dePrefs)
    }

    override fun onDestroy() {
        super.onDestroy()
        timerRunnable?.let { handler.removeCallbacks(it) }
        stopBatteryAutoRefresh()
    }

    private fun startBatteryAutoRefresh() {
        if (!enableBatteryInfo) return
        if (batteryTimerHandler == null) {
            batteryTimerHandler = Handler(Looper.getMainLooper())
        }
        stopBatteryAutoRefresh()

        batteryTimerRunnable = object : Runnable {
            override fun run() {
                Thread {
                    val data = BatteryUtils.fetchBatteryInfoWithRoot()
                    runOnUiThread {
                        batteryStatus = data.status
                        batteryVoltageMv = data.voltageMv
                        batteryCurrentMa = data.currentMa
                        batteryPowerWatts = data.powerWatts
                        batteryCurrentChargeMah = data.currentChargeMah
                        batteryMaxChargeVoltageMv = data.maxChargeVoltageMv
                        batteryMaxChargeCurrentMa = data.maxChargeCurrentMa

                        batteryCycles = data.cycles
                        batteryRated = data.ratedMah
                        batteryEstimated = data.estMah
                        batteryCalculatedHealth = data.calculatedHealthCap
                        batteryHealthCapIndex = data.healthCapIndex
                        batteryOverallHealth = data.overallHealth
                        batteryTemp = data.tempCelsius
                        batteryResistanceAvg = data.resAvgMilli
                        batteryResistanceNow = data.resNowMilli
                        batteryHealthImpIndex = data.healthImpIndex
                        batterySerialNumber = data.serialNumber
                        batteryFirstUsage = data.firstUsageDate
                        batteryAge = data.batteryAge
                        batteryAafvOffset = data.aafvMilli
                        Log.i("PXTK_Battery", "[SettingsActivity Root] Fetched 18 Battery Metrics: Status=$batteryStatus, Power=${batteryPowerWatts}W, Voltage=${batteryVoltageMv}mV, Cycles=$batteryCycles, Rated=$batteryRated")
                    }
                }.start()

                batteryTimerHandler?.postDelayed(this, batteryRefreshIntervalMs)
            }
        }
        batteryTimerHandler?.post(batteryTimerRunnable!!)
    }

    private fun stopBatteryAutoRefresh() {
        batteryTimerRunnable?.let { batteryTimerHandler?.removeCallbacks(it) }
        batteryTimerRunnable = null
    }

    private fun saveDoublePref(key: String, value: Any, ce: SharedPreferences, de: SharedPreferences) {
        val ceEdit = ce.edit()
        val deEdit = de.edit()
        when (value) {
            is Boolean -> { ceEdit.putBoolean(key, value); deEdit.putBoolean(key, value) }
            is Int -> { ceEdit.putInt(key, value); deEdit.putInt(key, value) }
            is Float -> { ceEdit.putFloat(key, value); deEdit.putFloat(key, value) }
            is Long -> { ceEdit.putLong(key, value); deEdit.putLong(key, value) }
        }
        ceEdit.apply()
        deEdit.apply()
    }
}
