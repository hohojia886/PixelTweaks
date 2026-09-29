package io.github.hohojia886.pixeltweaks.data.repository

import android.content.Context
import android.content.SharedPreferences
import io.github.hohojia886.pixeltweaks.presentation.SettingsUiState
import io.github.hohojia886.pixeltweaks.utils.IpcManager
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys

/**
 * SettingsRepository: Data access layer handling dual SharedPreferences persistence (CE/DE)
 * and cross-process IPC broadcast dispatches.
 */
class SettingsRepository(private val context: Context) {

    private val cePrefs: SharedPreferences by lazy {
        context.getSharedPreferences(IpcManager.PREF_NAME, Context.MODE_PRIVATE)
    }

    private val dePrefs: SharedPreferences by lazy {
        val deContext = context.createDeviceProtectedStorageContext()
        deContext.getSharedPreferences(IpcManager.PREF_NAME, Context.MODE_PRIVATE)
    }

    init {
        initDefaultsIfNeeded()
    }

    private fun initDefaultsIfNeeded() {
        if (!cePrefs.contains(PreferenceKeys.ENABLE_NETWORK_TRAFFIC) && !dePrefs.contains(PreferenceKeys.ENABLE_NETWORK_TRAFFIC)) {
            savePreference(PreferenceKeys.ENABLE_NETWORK_TRAFFIC, true)
            savePreference(PreferenceKeys.NETWORK_TRAFFIC_INTERVAL, 1)
            savePreference(PreferenceKeys.NETWORK_TRAFFIC_FONT_SIZE, 8f)
            savePreference(PreferenceKeys.NETWORK_TRAFFIC_THRESHOLD, 1)
            savePreference(PreferenceKeys.ENABLE_EASY_UNLOCK, true)
            savePreference(PreferenceKeys.ENABLE_EASY_UNLOCK_REBOOT, false)
            savePreference(PreferenceKeys.ENABLE_QS_WIFI_FIX, true)
            savePreference(PreferenceKeys.ENABLE_QS_DATA_FIX, true)
            savePreference(PreferenceKeys.ENABLE_CLEAR_ALL, true)
            savePreference(PreferenceKeys.ENABLE_TABLET_MODE, false)
            savePreference(PreferenceKeys.ENABLE_BATTERY_INFO, false)
            savePreference(PreferenceKeys.ENABLE_DT_LAUNCHER, true)
            savePreference(PreferenceKeys.ENABLE_DT_LOCKSCREEN, true)
            savePreference(PreferenceKeys.ENABLE_DT_STATUSBAR, true)
            savePreference(PreferenceKeys.ENABLE_UNRESTRICTED_SCREENSHOTS, true)
            savePreference(PreferenceKeys.ALLOW_DOWNGRADE, false)
            savePreference(PreferenceKeys.BYPASS_SIGNATURE, false)
            savePreference(PreferenceKeys.LOG_SECURITY, true)
            savePreference(PreferenceKeys.LOG_INTERFACE, true)
            savePreference(PreferenceKeys.LOG_GESTURES, true)
            savePreference(PreferenceKeys.LOG_QUICK_SETTINGS, true)
            syncAll()
        } else {
            if (cePrefs.all.isNotEmpty() && dePrefs.all.isEmpty()) {
                cePrefs.all.forEach { (k, v) -> if (v != null) savePreference(k, v) }
                syncAll()
            } else if (dePrefs.all.isNotEmpty() && cePrefs.all.isEmpty()) {
                dePrefs.all.forEach { (k, v) -> if (v != null) savePreference(k, v) }
                syncAll()
            }
        }
    }

    fun loadInitialState(): SettingsUiState {
        return SettingsUiState(
            enableBatteryInfo = dePrefs.getBoolean(PreferenceKeys.ENABLE_BATTERY_INFO, false),
            unrestrictedScreenshots = dePrefs.getBoolean(PreferenceKeys.ENABLE_UNRESTRICTED_SCREENSHOTS, true),
            easyUnlock = dePrefs.getBoolean(PreferenceKeys.ENABLE_EASY_UNLOCK, true),
            easyUnlockReboot = dePrefs.getBoolean(PreferenceKeys.ENABLE_EASY_UNLOCK_REBOOT, false),
            allowDowngrade = dePrefs.getBoolean(PreferenceKeys.ALLOW_DOWNGRADE, false),
            bypassSignature = dePrefs.getBoolean(PreferenceKeys.BYPASS_SIGNATURE, false),
            clearAll = dePrefs.getBoolean(PreferenceKeys.ENABLE_CLEAR_ALL, true),
            tabletMode = dePrefs.getBoolean(PreferenceKeys.ENABLE_TABLET_MODE, false),
            networkTraffic = dePrefs.getBoolean(PreferenceKeys.ENABLE_NETWORK_TRAFFIC, true),
            trafficInterval = dePrefs.getInt(PreferenceKeys.NETWORK_TRAFFIC_INTERVAL, 1),
            trafficFontSize = dePrefs.getFloat(PreferenceKeys.NETWORK_TRAFFIC_FONT_SIZE, 8f),
            trafficThreshold = dePrefs.getInt(PreferenceKeys.NETWORK_TRAFFIC_THRESHOLD, 1),
            dtLauncher = dePrefs.getBoolean(PreferenceKeys.ENABLE_DT_LAUNCHER, true),
            dtLockscreen = dePrefs.getBoolean(PreferenceKeys.ENABLE_DT_LOCKSCREEN, true),
            dtStatusbar = dePrefs.getBoolean(PreferenceKeys.ENABLE_DT_STATUSBAR, true),
            qsWifiFix = dePrefs.getBoolean(PreferenceKeys.ENABLE_QS_WIFI_FIX, true),
            qsDataFix = dePrefs.getBoolean(PreferenceKeys.ENABLE_QS_DATA_FIX, true),
            masterLog = dePrefs.getBoolean(PreferenceKeys.ENABLE_MASTER_LOG, false),
            logSecurity = dePrefs.getBoolean(PreferenceKeys.LOG_SECURITY, true),
            logInterface = dePrefs.getBoolean(PreferenceKeys.LOG_INTERFACE, true),
            logGestures = dePrefs.getBoolean(PreferenceKeys.LOG_GESTURES, true),
            logQuickSettings = dePrefs.getBoolean(PreferenceKeys.LOG_QUICK_SETTINGS, true)
        )
    }

    fun savePreference(key: String, value: Any) {
        val ceEdit = cePrefs.edit()
        val deEdit = dePrefs.edit()
        when (value) {
            is Boolean -> { ceEdit.putBoolean(key, value); deEdit.putBoolean(key, value) }
            is Int -> { ceEdit.putInt(key, value); deEdit.putInt(key, value) }
            is Float -> { ceEdit.putFloat(key, value); deEdit.putFloat(key, value) }
            is Long -> { ceEdit.putLong(key, value); deEdit.putLong(key, value) }
            is String -> { ceEdit.putString(key, value); deEdit.putString(key, value) }
        }
        ceEdit.apply()
        deEdit.apply()
    }

    fun dispatchUpdateBroadcast(key: String, value: Any) {
        IpcManager.sendUpdateBroadcast(context, key, value)
    }

    fun syncAll() {
        IpcManager.syncAllSettings(context, dePrefs)
    }

    fun getDowngradeTimestamp(): Long = dePrefs.getLong(PreferenceKeys.DOWNGRADE_TIMESTAMP, 0L)

    fun getSignatureTimestamp(): Long = dePrefs.getLong(PreferenceKeys.SIGNATURE_TIMESTAMP, 0L)
}
