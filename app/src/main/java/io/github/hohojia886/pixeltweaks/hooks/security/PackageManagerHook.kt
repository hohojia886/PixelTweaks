@file:Suppress("DiscouragedPrivateApi", "PrivateApi")

package io.github.hohojia886.pixeltweaks.hooks.security

import android.content.Context
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import io.github.hohojia886.pixeltweaks.utils.IpcManager
import io.github.hohojia886.pixeltweaks.utils.Logger
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys
import io.github.hohojia886.pixeltweaks.utils.hookBefore
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

/**
 * PackageManagerHook: Manages security policy bypasses in the system process.
 * Features time-limited "App Downgrade" and "Signature Verification Bypass".
 * Automatically disables sensitive bypasses after 3 minutes for security,
 * and restricts signature bypass to active package installation operations.
 */
object PackageManagerHook {

    private const val TAG = "Security"
    private const val TIMEOUT_MS = 3 * 60 * 1000L // Auto-disable timeout duration (3 minutes)
    private const val ACTIVE_INSTALL_WINDOW_MS = 60 * 1000L // Active install operation window (1 minute)

    @Volatile private var isDowngradeEnabled = false // Toggle for downgrade bypass
    @Volatile private var isSignatureBypassEnabled = false // Toggle for signature bypass

    @Volatile private var downgradeTimestamp = 0L // Monotonic start time of downgrade bypass
    @Volatile private var signatureTimestamp = 0L // Monotonic start time of signature bypass
    @Volatile private var lastInstallSessionTime = 0L // Monotonic timestamp of last active install operation
    @Volatile private var isHooked = false // Prevent duplicate hooking
    @Volatile private var lastSignatureActiveState = false

    // Entry point: Initializes settings and applies core system hijacks.
    // Guaranteed to execute idempotently exactly ONCE per process.
    fun hook(module: XposedModule, classLoader: ClassLoader) {
        if (isHooked) return
        isHooked = true

        refreshSettings(module, classLoader)
        applyHijacks(module, classLoader)
        syncSettings(module, classLoader)
        Logger.i(TAG, "Init", "Successfully initialized Policy Hijack PackageManager")
    }

    private fun refreshSettings(module: XposedModule, classLoader: ClassLoader?) {
        runCatching {
            val bundle = if (classLoader != null) IpcManager.loadPreferences(module, classLoader) else Bundle()
            val prefs = if (bundle.isEmpty) module.getRemotePreferences(IpcManager.PREF_NAME) else null

            isDowngradeEnabled = bundle.getBoolean(PreferenceKeys.ALLOW_DOWNGRADE, prefs?.getBoolean(PreferenceKeys.ALLOW_DOWNGRADE, false) ?: false)
            isSignatureBypassEnabled = bundle.getBoolean(PreferenceKeys.BYPASS_SIGNATURE, prefs?.getBoolean(PreferenceKeys.BYPASS_SIGNATURE, false) ?: false)
            val dgTs = bundle.getLong(PreferenceKeys.DOWNGRADE_TIMESTAMP, prefs?.getLong(PreferenceKeys.DOWNGRADE_TIMESTAMP, 0L) ?: 0L)
            val sigTs = bundle.getLong(PreferenceKeys.SIGNATURE_TIMESTAMP, prefs?.getLong(PreferenceKeys.SIGNATURE_TIMESTAMP, 0L) ?: 0L)

            if (isDowngradeEnabled && dgTs > 0) downgradeTimestamp = SystemClock.elapsedRealtime()
            if (isSignatureBypassEnabled && sigTs > 0) signatureTimestamp = SystemClock.elapsedRealtime()
        }
    }

    // Evaluates if a security bypass feature is currently active and within monotonic timeout
    private fun isFeatureActive(enabled: Boolean, timestamp: Long): Boolean {
        if (!enabled || timestamp <= 0L) return false
        val elapsed = SystemClock.elapsedRealtime() - timestamp
        return elapsed in 0L..<TIMEOUT_MS
    }

    // Evaluates if signature bypass is active AND restricted to an active package installation operation
    private fun isSignatureBypassActiveForInstall(): Boolean {
        if (!isFeatureActive(isSignatureBypassEnabled, signatureTimestamp)) return false
        val elapsedSinceInstall = SystemClock.elapsedRealtime() - lastInstallSessionTime
        return elapsedSinceInstall in 0L..<ACTIVE_INSTALL_WINDOW_MS
    }

    // Registers a secure IPC receiver to track real-time security state changes
    private fun syncSettings(module: XposedModule, classLoader: ClassLoader) {
        Thread {
            val sysContext = IpcManager.getSystemContext(classLoader) ?: IpcManager.getSafeContext(classLoader, "android")
            if (sysContext != null) {
                IpcManager.registerSecureReceiver(sysContext, module.getModuleApplicationInfo().uid) { intent ->
                    if (intent.action == IpcManager.ACTION_SETTINGS_SYNC) {
                        val now = SystemClock.elapsedRealtime()
                        isDowngradeEnabled = intent.getBooleanExtra(PreferenceKeys.ALLOW_DOWNGRADE, false)
                        isSignatureBypassEnabled = intent.getBooleanExtra(PreferenceKeys.BYPASS_SIGNATURE, false)
                        if (isDowngradeEnabled && downgradeTimestamp <= 0L) downgradeTimestamp = now
                        if (isSignatureBypassEnabled && signatureTimestamp <= 0L) signatureTimestamp = now
                        val isTabletMode = intent.getBooleanExtra(PreferenceKeys.ENABLE_TABLET_MODE, false)
                        updateSystemServerDensity(sysContext, isTabletMode)
                        Logger.d(TAG, "Sync", "Settings updated via broadcast: DG=$isDowngradeEnabled, Sig=$isSignatureBypassEnabled, Tablet=$isTabletMode")
                    } else if (intent.action == IpcManager.ACTION_SETTING_CHANGED) {
                        val key = intent.getStringExtra(PreferenceKeys.EXTRA_KEY)
                        val value = intent.getBooleanExtra(PreferenceKeys.EXTRA_VALUE, false)
                        val now = SystemClock.elapsedRealtime()
                        when (key) {
                            PreferenceKeys.ALLOW_DOWNGRADE -> {
                                isDowngradeEnabled = value
                                downgradeTimestamp = if (value) now else 0L
                                Logger.d(TAG, "Sync", "Setting [allow_downgrade] updated to $isDowngradeEnabled")
                            }
                            PreferenceKeys.BYPASS_SIGNATURE -> {
                                isSignatureBypassEnabled = value
                                signatureTimestamp = if (value) now else 0L
                                Logger.d(TAG, "Sync", "Setting [bypass_signature] updated to $isSignatureBypassEnabled")
                            }
                            PreferenceKeys.ENABLE_TABLET_MODE -> {
                                updateSystemServerDensity(sysContext, value)
                                Logger.d(TAG, "Sync", "Setting [enable_tablet_mode] updated to $value")
                            }
                        }
                    }
                }
            }
        }.start()
    }

    // Injects logic into PackageManager components to ignore signature mismatches and version downgrades.
    // Each hijack section (A, B, C, D) is isolated in its own independent runCatching block.
    private fun applyHijacks(module: XposedModule, classLoader: ClassLoader) {
        val realClassLoader = runCatching {
            val smClass = classLoader.loadClass("android.os.ServiceManager")
            val getService = smClass.getDeclaredMethod("getService", String::class.java)
            val binder = getService.invoke(null, "package") as IBinder
            binder.javaClass.classLoader
        }.getOrNull() ?: classLoader

        // A. PackageInstallerService: Injects flags (0x82) to permit version downgrades & records active install session
        runCatching {
            val piClass = realClassLoader.loadClass("com.android.server.pm.PackageInstallerService")
            piClass.declaredMethods.filter { it.name == "createSession" }.forEach { m ->
                module.hookBefore(m) { chain ->
                    lastInstallSessionTime = SystemClock.elapsedRealtime()
                    if (isFeatureActive(isDowngradeEnabled, downgradeTimestamp)) {
                        val params = chain.args[0]
                        runCatching {
                            val f = params.javaClass.getDeclaredField("installFlags").apply { isAccessible = true }
                            f.setInt(params, f.getInt(params) or 0x00000082)
                            Logger.i(TAG, "Active", "Injected Downgrade flags (0x82)")
                        }
                    }
                }
            }
        }.onFailure { e ->
            Logger.e(TAG, "SectionA", "PackageInstallerService hook skipped/failed", e)
        }

        // B. Installer Helpers: Forcefully returns true for internal downgrade permission checks & records active install session
        runCatching {
            listOf(
                "com.android.server.pm.InstallPackageHelper",
                "com.android.server.pm.PackageManagerServiceUtils",
                "com.android.server.pm.PackageManagerService"
            ).forEach { className ->
                runCatching {
                    val clazz = realClassLoader.loadClass(className) ?: return@runCatching
                    clazz.declaredMethods.filter {
                        it.name == "checkDowngrade" || it.name == "isDowngradePermitted"
                    }.forEach { m ->
                        module.hook(m).intercept { chain ->
                            lastInstallSessionTime = SystemClock.elapsedRealtime()
                            if (isFeatureActive(isDowngradeEnabled, downgradeTimestamp)) {
                                Logger.i(TAG, "Active", "Bypassing $className#${m.name}")
                                if (m.returnType == Boolean::class.javaPrimitiveType || m.returnType == Boolean::class.java) {
                                    return@intercept true
                                }
                                return@intercept null
                            }
                            chain.proceed()
                        }
                    }
                }
            }
        }.onFailure { e ->
            Logger.e(TAG, "SectionB", "Installer Helpers hook skipped/failed", e)
        }

        // C. ComputerEngine: Hijacks signature matching to return SIGNATURE_MATCH (0) ONLY during active install operations
        runCatching {
            listOf(
                "com.android.server.pm.ComputerEngine",
                "com.android.server.pm.Computer"
            ).forEach { className ->
                runCatching {
                    val clazz = realClassLoader.loadClass(className) ?: return@runCatching
                    clazz.declaredMethods.filter { it.name == "checkSignatures" }.forEach { m ->
                        module.hook(m).intercept { chain ->
                            val isActive = isSignatureBypassActiveForInstall()

                            if (isActive != lastSignatureActiveState) {
                                lastSignatureActiveState = isActive
                                Logger.i(TAG, "Status", "Signature Bypass state changed to: ${if (isActive) "ACTIVE" else "INACTIVE"}")
                            }

                            if (isActive) {
                                return@intercept 0 // SIGNATURE_MATCH
                            }
                            chain.proceed()
                        }
                    }
                }
            }
        }.onFailure { e ->
            Logger.e(TAG, "SectionC", "ComputerEngine hook skipped/failed", e)
        }

        // D. SigningDetails: Low-level safety net to ignore signature verification failures ONLY during active install operations
        runCatching {
            val detailsClass = classLoader.loadClass("android.content.pm.SigningDetails")
            detailsClass.declaredMethods.filter {
                it.name == "checkCapability" || it.name == "hasAncestorOrSelf"
            }.forEach { m ->
                module.hook(m).intercept { chain ->
                    if (isSignatureBypassActiveForInstall()) {
                        true
                    } else {
                        chain.proceed()
                    }
                }
            }
        }.onFailure { e ->
            Logger.e(TAG, "SectionD", "SigningDetails hook skipped/failed", e)
        }
    }

    @Volatile private var cachedWms: Any? = null
    @Volatile private var cachedSetDpiMethod: Method? = null
    @Volatile private var cachedClearDpiMethod: Method? = null

    private fun getIWindowManager(): Any? {
        if (cachedWms != null) return cachedWms
        return runCatching {
            val smClass = Class.forName("android.os.ServiceManager")
            val getService = smClass.getDeclaredMethod("getService", String::class.java)
            val binder = getService.invoke(null, "window") as IBinder
            val iwmClass = Class.forName("android.view.IWindowManager\$Stub")
            val asInterface = iwmClass.getDeclaredMethod("asInterface", IBinder::class.java)
            val wms = asInterface.invoke(null, binder)
            cachedWms = wms
            wms
        }.getOrNull()
    }

    private fun updateSystemServerDensity(context: Context, enabled: Boolean) {
        runCatching {
            val wms = getIWindowManager()
            if (wms != null) {
                if (enabled) {
                    val dm = context.resources.displayMetrics
                    val minPx = minOf(dm.widthPixels, dm.heightPixels)
                    val targetDpi = (minPx * 160) / 600
                    if (cachedSetDpiMethod == null) {
                        cachedSetDpiMethod = wms.javaClass.methods.firstOrNull { it.name == "setForcedDisplayDensityForUser" }
                    }
                    if (cachedSetDpiMethod != null) {
                        cachedSetDpiMethod!!.invoke(wms, 0, targetDpi, -2) // 0 = Display.DEFAULT_DISPLAY, -2 = USER_CURRENT
                        Logger.i("Density", "SystemServer", "Successfully called setForcedDisplayDensityForUser(0, $targetDpi, USER_CURRENT)")
                        return
                    }
                } else {
                    if (cachedClearDpiMethod == null) {
                        cachedClearDpiMethod = wms.javaClass.methods.firstOrNull { it.name == "clearForcedDisplayDensityForUser" }
                    }
                    if (cachedClearDpiMethod != null) {
                        cachedClearDpiMethod!!.invoke(wms, 0, -2) // 0 = Display.DEFAULT_DISPLAY, -2 = USER_CURRENT
                        Logger.i("Density", "SystemServer", "Successfully called clearForcedDisplayDensityForUser(0, USER_CURRENT)")
                        return
                    }
                }
            }

            if (enabled) {
                val dm = context.resources.displayMetrics
                val minPx = minOf(dm.widthPixels, dm.heightPixels)
                val targetDpi = (minPx * 160) / 600
                Settings.Secure.putInt(context.contentResolver, "display_density_forced", targetDpi)
                Logger.i("Density", "SystemServer", "Fallback: Set display_density_forced = $targetDpi")
            } else {
                Settings.Secure.putString(context.contentResolver, "display_density_forced", null)
                Logger.i("Density", "SystemServer", "Fallback: Cleared display_density_forced")
            }
        }.onFailure { e ->
            Logger.e("Density", "SystemServer", "Failed to update display density in SystemServer", e)
        }
    }
}
