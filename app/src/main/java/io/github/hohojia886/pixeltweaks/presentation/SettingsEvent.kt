package io.github.hohojia886.pixeltweaks.presentation

/**
 * Sealed interface representing all user intents and system events.
 * Defines unidirectional data flow action events for SettingsViewModel.
 */
sealed interface SettingsEvent {
    /**
     * Changes the application display language.
     * Passes target languageTag string to repository.
     */
    data class ChangeLanguage(val languageTag: String) : SettingsEvent

    /**
     * Toggles the battery info display feature.
     * Updates enableBatteryInfo preference state.
     */
    data class ToggleBatteryInfo(val enabled: Boolean) : SettingsEvent
    /**
     * Toggles the camera energy ring feature.
     * Updates cameraEnergyRing preference state.
     */
    data class ToggleCameraEnergyRing(val enabled: Boolean) : SettingsEvent
    /**
     * Toggles energy ring only while charging.
     * Updates ringOnlyCharging preference state.
     */
    data class ToggleRingOnlyCharging(val enabled: Boolean) : SettingsEvent
    /**
     * Toggles energy ring rotation animation.
     * Updates ringRotation preference state.
     */
    data class ToggleRingRotation(val enabled: Boolean) : SettingsEvent
    /**
     * Changes energy ring radius offset value.
     * Updates ringRadiusOffset preference float.
     */
    data class ChangeRingRadiusOffset(val offset: Float) : SettingsEvent
    /**
     * Changes energy ring stroke width value.
     * Updates ringStrokeWidth preference float.
     */
    data class ChangeRingStrokeWidth(val width: Float) : SettingsEvent
    /**
     * Requests a manual refresh of battery info.
     * Triggers root sysfs battery fetch execution.
     */
    data object RefreshBatteryInfo : SettingsEvent

    /**
     * Toggles unrestricted screenshots feature.
     * Suppresses FLAG_SECURE system-wide when enabled.
     */
    data class ToggleUnrestrictedScreenshots(val enabled: Boolean) : SettingsEvent
    /**
     * Toggles easy unlock keyguard feature.
     * Auto confirms PIN when learned length is matched.
     */
    data class ToggleEasyUnlock(val enabled: Boolean) : SettingsEvent
    /**
     * Toggles easy unlock reboot bypass feature.
     * Permits PIN auto confirm after device reboot.
     */
    data class ToggleEasyUnlockReboot(val enabled: Boolean) : SettingsEvent
    /**
     * Toggles allow package downgrade feature.
     * Bypasses version downgrade restriction for 3m.
     */
    data class ToggleAllowDowngrade(val enabled: Boolean) : SettingsEvent
    /**
     * Toggles bypass signature verification.
     * Bypasses signature mismatch checks for 3m.
     */
    data class ToggleBypassSignature(val enabled: Boolean) : SettingsEvent

    /**
     * Toggles the clear all recent tasks button.
     * Injects clear all button into Recents screen.
     */
    data class ToggleClearAll(val enabled: Boolean) : SettingsEvent
    /**
     * Toggles tablet mode layout override.
     * Forces smallest width sw >= 600dp density.
     */
    data class ToggleTabletMode(val enabled: Boolean) : SettingsEvent
    /**
     * Toggles network traffic monitor feature.
     * Displays speed indicator in status bar.
     */
    data class ToggleNetworkTraffic(val enabled: Boolean) : SettingsEvent
    /**
     * Toggles statusbar battery percentage text.
     * Injects text view at far right of status bar.
     */
    data class ToggleStatusbarBatteryPercent(val enabled: Boolean) : SettingsEvent
    /**
     * Toggles hiding system VPN status bar icon.
     * Blacklists vpn slot and intercepts Compose.
     */
    data class ToggleHideVpnIcon(val enabled: Boolean) : SettingsEvent
    /**
     * Changes network traffic update interval.
     * Sets polling delay in seconds for speed updates.
     */
    data class ChangeTrafficInterval(val interval: Int) : SettingsEvent
    /**
     * Changes network traffic text font size.
     * Sets font sp size for speed indicator view.
     */
    data class ChangeTrafficFontSize(val size: Float) : SettingsEvent
    /**
     * Changes network traffic display threshold.
     * Auto hides speed text below KB/s threshold.
     */
    data class ChangeTrafficThreshold(val threshold: Int) : SettingsEvent

    /**
     * Toggles double-tap to sleep on launcher.
     * Enables DT2S gesture on home screen workspace.
     */
    data class ToggleDtLauncher(val enabled: Boolean) : SettingsEvent
    /**
     * Toggles double-tap to sleep on lockscreen.
     * Enables DT2S gesture on keyguard shade view.
     */
    data class ToggleDtLockscreen(val enabled: Boolean) : SettingsEvent
    /**
     * Toggles double-tap to sleep on statusbar.
     * Enables DT2S gesture on top status bar view.
     */
    data class ToggleDtStatusbar(val enabled: Boolean) : SettingsEvent

    /**
     * Toggles Quick Settings WiFi fix tile logic.
     * Forces setWifiEnabled(false) on pauseWifi call.
     */
    data class ToggleQsWifiFix(val enabled: Boolean) : SettingsEvent
    /**
     * Toggles Quick Settings Data fix tile logic.
     * Bypasses cellular data confirmation dialog.
     */
    data class ToggleQsDataFix(val enabled: Boolean) : SettingsEvent

    /**
     * Toggles master log switch state.
     * Enables or disables all category loggers.
     */
    data class ToggleMasterLog(val enabled: Boolean) : SettingsEvent
    /**
     * Toggles security logging category.
     * Filters logcat outputs for Security hooks.
     */
    data class ToggleLogSecurity(val enabled: Boolean) : SettingsEvent
    /**
     * Toggles interface logging category.
     * Filters logcat outputs for Interface hooks.
     */
    data class ToggleLogInterface(val enabled: Boolean) : SettingsEvent
    /**
     * Toggles gestures logging category.
     * Filters logcat outputs for Gestures hooks.
     */
    data class ToggleLogGestures(val enabled: Boolean) : SettingsEvent
    /**
     * Toggles quick settings logging category.
     * Filters logcat outputs for QS tile hooks.
     */
    data class ToggleLogQuickSettings(val enabled: Boolean) : SettingsEvent

    /**
     * Triggered when the activity resumes.
     * Starts battery refresh and timer loops.
     */
    data object OnResume : SettingsEvent
    /**
     * Triggered when the activity pauses.
     * Stops battery refresh and background timers.
     */
    data object OnPause : SettingsEvent
    /**
     * Triggered when device screen turns off.
     * Pauses real-time metric updates.
     */
    data object OnScreenOff : SettingsEvent
    /**
     * Triggered when device screen turns on.
     * Resumes real-time metric updates.
     */
    data object OnScreenOn : SettingsEvent
}
