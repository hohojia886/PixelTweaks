package io.github.hohojia886.pixeltweaks.ui

import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.hohojia886.pixeltweaks.ui.components.SettingsScreen
import io.github.hohojia886.pixeltweaks.ui.theme.PixelTweaksTheme
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

    // Interface State
    private var clearAll by mutableStateOf(true)
    private var networkTraffic by mutableStateOf(true)
    private var trafficInterval by mutableStateOf(1)
    private var trafficFontSize by mutableStateOf(8f)
    private var trafficThreshold by mutableStateOf(1)

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
        unrestrictedScreenshots = dePrefs.getBoolean(PreferenceKeys.ENABLE_UNRESTRICTED_SCREENSHOTS, true)
        easyUnlock = dePrefs.getBoolean(PreferenceKeys.ENABLE_EASY_UNLOCK, true)
        easyUnlockReboot = dePrefs.getBoolean(PreferenceKeys.ENABLE_EASY_UNLOCK_REBOOT, false)
        allowDowngrade = dePrefs.getBoolean(PreferenceKeys.ALLOW_DOWNGRADE, false)
        bypassSignature = dePrefs.getBoolean(PreferenceKeys.BYPASS_SIGNATURE, false)

        clearAll = dePrefs.getBoolean(PreferenceKeys.ENABLE_CLEAR_ALL, true)
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

    override fun onPause() {
        super.onPause()
        val deContext = createDeviceProtectedStorageContext()
        val dePrefs = deContext.getSharedPreferences(IpcManager.PREF_NAME, MODE_PRIVATE)
        IpcManager.syncAllSettings(this, dePrefs)
    }

    override fun onDestroy() {
        super.onDestroy()
        timerRunnable?.let { handler.removeCallbacks(it) }
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
