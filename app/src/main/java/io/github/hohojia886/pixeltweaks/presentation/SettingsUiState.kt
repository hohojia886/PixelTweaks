package io.github.hohojia886.pixeltweaks.presentation

/**
 * SettingsUiState: Represents the single source of truth for the entire Settings UI.
 * Consolidates all feature toggles, internal app states, and real-time battery metrics
 * into an immutable data class for Unidirectional Data Flow (UDF) rendering.
 */
data class SettingsUiState(
    // App Display Language ("" = System Default)
    val currentLanguageTag: String = "",

    // System & Security State
    val unrestrictedScreenshots: Boolean = true,
    val easyUnlock: Boolean = true,
    val easyUnlockReboot: Boolean = false,
    val allowDowngrade: Boolean = false,
    val allowDowngradeTimer: String? = null,
    val bypassSignature: Boolean = false,
    val bypassSignatureTimer: String? = null,

    // Battery Info State (Toggle + 18 Metrics + Energy Ring Sliders)
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

    // Interface State
    val clearAll: Boolean = true,
    val tabletMode: Boolean = false,
    val networkTraffic: Boolean = true,
    val statusbarBatteryPercent: Boolean = false,
    val trafficInterval: Int = 1,
    val trafficFontSize: Float = 8f,
    val trafficThreshold: Int = 1,

    // Gestures State
    val dtLauncher: Boolean = true,
    val dtLockscreen: Boolean = true,
    val dtStatusbar: Boolean = true,

    // Quick Settings State
    val qsWifiFix: Boolean = true,
    val qsDataFix: Boolean = true,

    // Debug Logging State
    val masterLog: Boolean = false,
    val logSecurity: Boolean = true,
    val logInterface: Boolean = true,
    val logGestures: Boolean = true,
    val logQuickSettings: Boolean = true
)
