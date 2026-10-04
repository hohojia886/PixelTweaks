package io.github.hohojia886.pixeltweaks.utils

/**
 * Single source of truth for every settings key used across the module:
 * UI (SettingsActivity), storage (RemotePrefProvider), broadcast sync
 * (IpcManager), and every hook that consumes a setting.
 */
object PreferenceKeys {

    /** Generic broadcast extra field names, shared by every single-key update. */
    const val EXTRA_KEY = "key"
    const val EXTRA_VALUE = "value"

    const val EXTRA_BATTERY_CYCLES = "battery_cycles"
    const val EXTRA_BATTERY_RATED = "battery_rated"
    const val EXTRA_BATTERY_ESTIMATED = "battery_estimated"
    const val EXTRA_BATTERY_HEALTH_CAP_INDEX = "battery_health_cap_index"
    const val EXTRA_BATTERY_OVERALL_HEALTH = "battery_overall_health"
    const val EXTRA_BATTERY_TEMP = "battery_temp"
    const val EXTRA_BATTERY_RESISTANCE_AVG = "battery_resistance_avg"
    const val EXTRA_BATTERY_RESISTANCE_NOW = "battery_resistance_now"
    const val EXTRA_BATTERY_HEALTH_IMP_INDEX = "battery_health_imp_index"
    const val EXTRA_BATTERY_SERIAL_NUMBER = "battery_serial_number"
    const val EXTRA_BATTERY_FIRST_USAGE = "battery_first_usage"
    const val EXTRA_BATTERY_AGE = "battery_age"
    const val EXTRA_BATTERY_AAFV_OFFSET = "battery_aafv_offset"

    // ---- Feature toggles -------------------------------------------------
    const val APP_LANGUAGE = "app_language"
    const val ENABLE_BATTERY_INFO = "enable_battery_info"
    const val ENABLE_CLEAR_ALL = "enable_clear_all"
    const val ENABLE_NETWORK_TRAFFIC = "enable_network_traffic"
    const val ENABLE_UNRESTRICTED_SCREENSHOTS = "enable_unrestricted_screenshots"
    const val ENABLE_QS_WIFI_FIX = "enable_qs_wifi_fix"
    const val ENABLE_QS_DATA_FIX = "enable_qs_data_fix"
    const val ENABLE_DT_LAUNCHER = "enable_dt_launcher"
    const val ENABLE_DT_LOCKSCREEN = "enable_dt_lockscreen"
    const val ENABLE_DT_STATUSBAR = "enable_dt_statusbar"
    const val ENABLE_EASY_UNLOCK = "enable_easy_unlock"
    const val ENABLE_EASY_UNLOCK_REBOOT = "enable_easy_unlock_reboot"
    const val ENABLE_TABLET_MODE = "enable_tablet_mode"
    const val IS_FIRST_UNLOCK_DONE = "is_first_unlock_done"

    // ---- Numerical settings ------------------------------------------------
    const val NETWORK_TRAFFIC_INTERVAL = "network_traffic_interval"
    const val NETWORK_TRAFFIC_FONT_SIZE = "network_traffic_font_size"
    const val NETWORK_TRAFFIC_THRESHOLD = "network_traffic_threshold"
    const val EXPECTED_PASS_LEN = "expected_pass_len"

    // ---- Security bypasses -------------------------------------------------
    const val ALLOW_DOWNGRADE = "allow_downgrade"
    const val BYPASS_SIGNATURE = "bypass_signature"
    const val DOWNGRADE_TIMESTAMP = "downgrade_timestamp"
    const val SIGNATURE_TIMESTAMP = "signature_timestamp"

    // ---- Debug logging configuration (Consolidated into 4 categories) ------
    const val ENABLE_MASTER_LOG = "enable_master_log"
    const val LOG_SECURITY = "log_security"
    const val LOG_INTERFACE = "log_interface"
    const val LOG_GESTURES = "log_gestures"
    const val LOG_QUICK_SETTINGS = "log_quick_settings"
}
