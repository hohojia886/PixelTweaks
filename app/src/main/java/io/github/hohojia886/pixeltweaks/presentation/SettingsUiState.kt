package io.github.hohojia886.pixeltweaks.presentation

/**
 * Single source of truth data class for the Settings UI state.
 * Holds all feature toggles, real-time metrics, and configuration values.
 */
data class SettingsUiState(
    /**
     * App display language tag configuration state.
     * Empty string defaults to system language setting.
     */
    val currentLanguageTag: String = "",

    /**
     * System and security bypass state properties.
     * Controls unrestricted screenshots, easy unlock, and security timeouts.
     */
    val unrestrictedScreenshots: Boolean = true,
    val easyUnlock: Boolean = true,
    val easyUnlockReboot: Boolean = false,
    val allowDowngrade: Boolean = false,
    val allowDowngradeTimer: String? = null,
    val bypassSignature: Boolean = false,
    val bypassSignatureTimer: String? = null,

    /**
     * Battery information and energy ring configuration state.
     * Holds real-time sysfs battery metrics and cutout ring settings.
     */
    val enableBatteryInfo: Boolean = false,
    val cameraEnergyRing: Boolean = false,
    val ringOnlyCharging: Boolean = false,
    val ringRotation: Boolean = false,
    val ringRadiusOffset: Float = 0f,
    val ringStrokeWidth: Float = 2.0f,
    val batteryStatus: String = "N/A",
    val batteryVoltageMv: Int = -1,
    val batteryCurrentMa: Int = 0,
    val batteryPowerWatts: Float = 0f,
    val batteryCurrentChargeMah: Int = -1,
    val batteryMaxChargeVoltageMv: Int = -1,
    val batteryMaxChargeCurrentMa: Int = -1,
    val batteryCycles: Int = -1,
    val batteryRated: Int = -1,
    val batteryEstimated: Int = -1,
    val batteryCalculatedHealth: Int = -1,
    val batteryHealthCapIndex: Int = -1,
    val batteryOverallHealth: String = "N/A",
    val batteryTemp: Float = -1f,
    val batteryResistanceAvg: Float = -1f,
    val batteryResistanceNow: Float = -1f,
    val batteryHealthImpIndex: Int = -1,
    val batterySerialNumber: String = "N/A",
    val batteryFirstUsage: String = "N/A",
    val batteryAge: String = "N/A",
    val batteryAafvOffset: Int = -1,

    /**
     * Interface customization and indicator state properties.
     * Controls clear all button, tablet mode, traffic monitor, and VPN icon.
     */
    val clearAll: Boolean = true,
    val tabletMode: Boolean = false,
    val networkTraffic: Boolean = true,
    val statusbarBatteryPercent: Boolean = false,
    val hideVpnIcon: Boolean = false,
    val trafficInterval: Int = 1,
    val trafficFontSize: Float = 8f,
    val trafficThreshold: Int = 1,

    /**
     * Gesture handling configuration state properties.
     * Controls double-tap to sleep on launcher, lockscreen, and statusbar.
     */
    val dtLauncher: Boolean = true,
    val dtLockscreen: Boolean = true,
    val dtStatusbar: Boolean = true,

    /**
     * Quick Settings tile fix state properties.
     * Controls WiFi power-off fix and cellular data confirmation bypass.
     */
    val qsWifiFix: Boolean = true,
    val qsDataFix: Boolean = true,

    /**
     * Debug logging category configuration state properties.
     * Controls master logging toggle and category sub-switches.
     */
    val masterLog: Boolean = false,
    val logSecurity: Boolean = true,
    val logInterface: Boolean = true,
    val logGestures: Boolean = true,
    val logQuickSettings: Boolean = true
)
