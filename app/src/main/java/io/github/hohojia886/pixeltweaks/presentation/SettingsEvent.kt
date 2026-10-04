package io.github.hohojia886.pixeltweaks.presentation

/**
 * SettingsEvent: Sealed interface representing all user intents and system events
 * in Unidirectional Data Flow (UDF) architecture.
 */
sealed interface SettingsEvent {
    // Language Event
    data class ChangeLanguage(val languageTag: String) : SettingsEvent

    // Battery Info Events
    data class ToggleBatteryInfo(val enabled: Boolean) : SettingsEvent
    data object RefreshBatteryInfo : SettingsEvent

    // System & Security Events
    data class ToggleUnrestrictedScreenshots(val enabled: Boolean) : SettingsEvent
    data class ToggleEasyUnlock(val enabled: Boolean) : SettingsEvent
    data class ToggleEasyUnlockReboot(val enabled: Boolean) : SettingsEvent
    data class ToggleAllowDowngrade(val enabled: Boolean) : SettingsEvent
    data class ToggleBypassSignature(val enabled: Boolean) : SettingsEvent

    // Interface Events
    data class ToggleClearAll(val enabled: Boolean) : SettingsEvent
    data class ToggleTabletMode(val enabled: Boolean) : SettingsEvent
    data class ToggleNetworkTraffic(val enabled: Boolean) : SettingsEvent
    data class ChangeTrafficInterval(val interval: Int) : SettingsEvent
    data class ChangeTrafficFontSize(val size: Float) : SettingsEvent
    data class ChangeTrafficThreshold(val threshold: Int) : SettingsEvent

    // Gestures Events
    data class ToggleDtLauncher(val enabled: Boolean) : SettingsEvent
    data class ToggleDtLockscreen(val enabled: Boolean) : SettingsEvent
    data class ToggleDtStatusbar(val enabled: Boolean) : SettingsEvent

    // Quick Settings Events
    data class ToggleQsWifiFix(val enabled: Boolean) : SettingsEvent
    data class ToggleQsDataFix(val enabled: Boolean) : SettingsEvent

    // Debug Logging Events
    data class ToggleMasterLog(val enabled: Boolean) : SettingsEvent
    data class ToggleLogSecurity(val enabled: Boolean) : SettingsEvent
    data class ToggleLogInterface(val enabled: Boolean) : SettingsEvent
    data class ToggleLogGestures(val enabled: Boolean) : SettingsEvent
    data class ToggleLogQuickSettings(val enabled: Boolean) : SettingsEvent

    // Lifecycle & Screen Events
    data object OnResume : SettingsEvent
    data object OnPause : SettingsEvent
    data object OnScreenOff : SettingsEvent
    data object OnScreenOn : SettingsEvent
}
