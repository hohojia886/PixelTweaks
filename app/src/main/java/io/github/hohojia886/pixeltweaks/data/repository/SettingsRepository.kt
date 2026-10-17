package io.github.hohojia886.pixeltweaks.data.repository

import android.content.Context
import android.content.SharedPreferences
import io.github.hohojia886.pixeltweaks.presentation.SettingsUiState
import io.github.hohojia886.pixeltweaks.utils.IpcManager
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys
import io.github.hohojia886.pixeltweaks.utils.isQsDataFixSupported

/**
 * SettingsRepository: Data access layer handling dual SharedPreferences persistence.
 *
 * Coordinates credential-encrypted (CE) and device-protected (DE) preferences storage.
 * Direct Boot requires DE storage so settings are accessible before the user unlocks the screen.
 *
 * @param context Application context used to create device-protected storage contexts.
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

    /**
     * Initializes default preferences if they don't exist and syncs CE/DE storage.
     * Seeds missing default keys and performs bidirectional synchronization between CE and DE storage.
     */
    private fun initDefaultsIfNeeded() {
        if (!cePrefs.contains(PreferenceKeys.ENABLE_NETWORK_TRAFFIC) && !dePrefs.contains(PreferenceKeys.ENABLE_NETWORK_TRAFFIC)) {
            savePreference(PreferenceKeys.ENABLE_NETWORK_TRAFFIC, true)
            savePreference(PreferenceKeys.ENABLE_STATUSBAR_BATTERY_PERCENT, false)
            savePreference(PreferenceKeys.ENABLE_HIDE_VPN_ICON, false)
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
            savePreference(PreferenceKeys.ENABLE_CAMERA_ENERGY_RING, false)
            savePreference(PreferenceKeys.RING_RADIUS_OFFSET, 0f)
            savePreference(PreferenceKeys.RING_STROKE_WIDTH, 2.0f)
            savePreference(PreferenceKeys.RING_ONLY_CHARGING, false)
            savePreference(PreferenceKeys.ENABLE_RING_ROTATION, false)
            savePreference(PreferenceKeys.APP_LANGUAGE, "")
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
            cePrefs.all.forEach { (k, v) -> if (v != null && !dePrefs.contains(k)) savePreference(k, v) }
            dePrefs.all.forEach { (k, v) -> if (v != null && !cePrefs.contains(k)) savePreference(k, v) }
            syncAll()
        }
    }

    /**
     * Loads the initial settings state from DE preferences into state holder.
     *
     * @return A populated [SettingsUiState] data class containing current preference values.
     */
    fun loadInitialState(): SettingsUiState {
        return SettingsUiState(
            currentLanguageTag = dePrefs.getString(PreferenceKeys.APP_LANGUAGE, "") ?: "",
            enableBatteryInfo = dePrefs.getBoolean(PreferenceKeys.ENABLE_BATTERY_INFO, false),
            cameraEnergyRing = dePrefs.getBoolean(PreferenceKeys.ENABLE_CAMERA_ENERGY_RING, false),
            ringRadiusOffset = dePrefs.getFloat(PreferenceKeys.RING_RADIUS_OFFSET, 0f),
            ringStrokeWidth = dePrefs.getFloat(PreferenceKeys.RING_STROKE_WIDTH, 2.0f),
            ringOnlyCharging = dePrefs.getBoolean(PreferenceKeys.RING_ONLY_CHARGING, false),
            ringRotation = dePrefs.getBoolean(PreferenceKeys.ENABLE_RING_ROTATION, false),
            unrestrictedScreenshots = dePrefs.getBoolean(PreferenceKeys.ENABLE_UNRESTRICTED_SCREENSHOTS, true),
            easyUnlock = dePrefs.getBoolean(PreferenceKeys.ENABLE_EASY_UNLOCK, true),
            easyUnlockReboot = dePrefs.getBoolean(PreferenceKeys.ENABLE_EASY_UNLOCK_REBOOT, false),
            allowDowngrade = dePrefs.getBoolean(PreferenceKeys.ALLOW_DOWNGRADE, false),
            bypassSignature = dePrefs.getBoolean(PreferenceKeys.BYPASS_SIGNATURE, false),
            clearAll = dePrefs.getBoolean(PreferenceKeys.ENABLE_CLEAR_ALL, true),
            tabletMode = dePrefs.getBoolean(PreferenceKeys.ENABLE_TABLET_MODE, false),
            networkTraffic = dePrefs.getBoolean(PreferenceKeys.ENABLE_NETWORK_TRAFFIC, true),
            statusbarBatteryPercent = dePrefs.getBoolean(PreferenceKeys.ENABLE_STATUSBAR_BATTERY_PERCENT, false),
            hideVpnIcon = dePrefs.getBoolean(PreferenceKeys.ENABLE_HIDE_VPN_ICON, false),
            trafficInterval = dePrefs.getInt(PreferenceKeys.NETWORK_TRAFFIC_INTERVAL, 1),
            trafficFontSize = dePrefs.getFloat(PreferenceKeys.NETWORK_TRAFFIC_FONT_SIZE, 8f),
            trafficThreshold = dePrefs.getInt(PreferenceKeys.NETWORK_TRAFFIC_THRESHOLD, 1),
            dtLauncher = dePrefs.getBoolean(PreferenceKeys.ENABLE_DT_LAUNCHER, true),
            dtLockscreen = dePrefs.getBoolean(PreferenceKeys.ENABLE_DT_LOCKSCREEN, true),
            dtStatusbar = dePrefs.getBoolean(PreferenceKeys.ENABLE_DT_STATUSBAR, true),
            qsWifiFix = dePrefs.getBoolean(PreferenceKeys.ENABLE_QS_WIFI_FIX, true),
            qsDataFix = if (isQsDataFixSupported()) dePrefs.getBoolean(PreferenceKeys.ENABLE_QS_DATA_FIX, true) else false,
            masterLog = dePrefs.getBoolean(PreferenceKeys.ENABLE_MASTER_LOG, false),
            logSecurity = dePrefs.getBoolean(PreferenceKeys.LOG_SECURITY, true),
            logInterface = dePrefs.getBoolean(PreferenceKeys.LOG_INTERFACE, true),
            logGestures = dePrefs.getBoolean(PreferenceKeys.LOG_GESTURES, true),
            logQuickSettings = dePrefs.getBoolean(PreferenceKeys.LOG_QUICK_SETTINGS, true)
        )
    }

    /**
     * Saves a preference value to both CE and DE storage.
     * Commits values synchronously to prevent cross-process state desync across Direct Boot transitions.
     *
     * @param key The setting key identifier defined in [PreferenceKeys].
     * @param value The primitive value (Boolean, Int, Float, Long, String) to persist.
     */
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
        ceEdit.commit()
        deEdit.commit()
    }

    /**
     * Dispatches an update broadcast to notify other processes of a setting change.
     *
     * @param key The setting key identifier modified.
     * @param value The updated value dispatched via IPC.
     */
    fun dispatchUpdateBroadcast(key: String, value: Any) {
        IpcManager.sendUpdateBroadcast(context, key, value)
    }

    /**
     * Synchronizes all settings to other processes via a bulk IPC broadcast.
     * Sends a full state intent containing all DE preferences to active module hooks.
     */
    fun syncAll() {
        IpcManager.syncAllSettings(context, dePrefs)
    }

    /**
     * Gets the downgrade timestamp from DE preferences.
     *
     * @return The wall-clock timestamp in milliseconds when downgrade bypass was activated.
     */
    fun getDowngradeTimestamp(): Long = dePrefs.getLong(PreferenceKeys.DOWNGRADE_TIMESTAMP, 0L)

    /**
     * Gets the signature timestamp from DE preferences.
     *
     * @return The wall-clock timestamp in milliseconds when signature bypass was activated.
     */
    fun getSignatureTimestamp(): Long = dePrefs.getLong(PreferenceKeys.SIGNATURE_TIMESTAMP, 0L)
}
