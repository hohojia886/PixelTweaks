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

    // ---- Feature toggles -------------------------------------------------
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

    // ---- Debug logging configuration ---------------------------------------
    const val ENABLE_MASTER_LOG = "enable_master_log"
    const val LOG_CLEAR_ALL = "log_clear_all"
    const val LOG_NETWORK_TRAFFIC = "log_network_traffic"
    const val LOG_QUICK_SETTINGS = "log_quick_settings"
    const val LOG_SECURITY_BYPASSES = "log_security_bypasses"
    const val LOG_UNRESTRICTED_SCREENSHOTS = "log_unrestricted_screenshots"
    const val LOG_DT2S = "log_dt2s"
    const val LOG_EASY_UNLOCK = "log_easy_unlock"
}
