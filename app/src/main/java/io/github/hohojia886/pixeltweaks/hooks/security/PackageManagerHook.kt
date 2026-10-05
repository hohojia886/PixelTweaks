@file:Suppress("DiscouragedPrivateApi", "PrivateApi")

package io.github.hohojia886.pixeltweaks.hooks.security

import android.content.Context
import android.os.Bundle
import android.os.IBinder
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
 * Automatically disables sensitive bypasses after 3 minutes for security.
 */
object PackageManagerHook {

    private const val TAG = "Security"
    private const val TIMEOUT_MS = 3 * 60 * 1000L // Auto-disable timeout duration

    @Volatile private var isDowngradeEnabled = false // Toggle for downgrade bypass
    @Volatile private var isSignatureBypassEnabled = false // Toggle for signature bypass

    @Volatile private var downgradeTimestamp = 0L // Start time of downgrade bypass
    @Volatile private var signatureTimestamp = 0L // Start time of signature bypass
    @Volatile private var isHooked = false // Prevent duplicate hooking
    @Volatile private var lastSignatureActiveState = false

    // Entry point: Initializes settings and applies core system hijacks
    fun hook(module: XposedModule, classLoader: ClassLoader) {
        if (isHooked) return

        refreshSettings(module, classLoader)

        if (applyHijacks(module, classLoader)) {
            isHooked = true
            Logger.i(TAG, "Init", "Successfully initialized Policy Hijack PackageManager")
            syncSettings(module, classLoader)
        }
    }

    private fun refreshSettings(module: XposedModule, classLoader: ClassLoader?) {
        runCatching {
            val bundle = if (classLoader != null) IpcManager.loadPreferences(module, classLoader) else Bundle()
            val prefs = if (bundle.isEmpty) module.getRemotePreferences(IpcManager.PREF_NAME) else null

            isDowngradeEnabled = bundle.getBoolean(PreferenceKeys.ALLOW_DOWNGRADE, prefs?.getBoolean(PreferenceKeys.ALLOW_DOWNGRADE, false) ?: false)
            isSignatureBypassEnabled = bundle.getBoolean(PreferenceKeys.BYPASS_SIGNATURE, prefs?.getBoolean(PreferenceKeys.BYPASS_SIGNATURE, false) ?: false)
            downgradeTimestamp = bundle.getLong(PreferenceKeys.DOWNGRADE_TIMESTAMP, prefs?.getLong(PreferenceKeys.DOWNGRADE_TIMESTAMP, 0L) ?: 0L)
            signatureTimestamp = bundle.getLong(PreferenceKeys.SIGNATURE_TIMESTAMP, prefs?.getLong(PreferenceKeys.SIGNATURE_TIMESTAMP, 0L) ?: 0L)
        }
    }

    // Evaluates if a security bypass feature is currently active and within timeout
    private fun isFeatureActive(enabled: Boolean, timestamp: Long): Boolean {
        if (!enabled) return false
        return (System.currentTimeMillis() - timestamp) < TIMEOUT_MS
    }

    // Registers a secure IPC receiver to track real-time security state changes
    private fun syncSettings(module: XposedModule, classLoader: ClassLoader) {
        Thread {
            val sysContext = IpcManager.getSystemContext(classLoader)
            if (sysContext != null) {
                IpcManager.registerSecureReceiver(sysContext, module.getModuleApplicationInfo().uid) { intent ->
                    if (intent.action == IpcManager.ACTION_SETTINGS_SYNC) {
                        isDowngradeEnabled = intent.getBooleanExtra(PreferenceKeys.ALLOW_DOWNGRADE, false)
                        isSignatureBypassEnabled = intent.getBooleanExtra(PreferenceKeys.BYPASS_SIGNATURE, false)
                        downgradeTimestamp = intent.getLongExtra(PreferenceKeys.DOWNGRADE_TIMESTAMP, 0L)
                        signatureTimestamp = intent.getLongExtra(PreferenceKeys.SIGNATURE_TIMESTAMP, 0L)
                        val isTabletMode = intent.getBooleanExtra(PreferenceKeys.ENABLE_TABLET_MODE, false)
                        updateSystemServerDensity(sysContext, isTabletMode)
                        Logger.d(TAG, "Sync", "Settings updated via broadcast: DG=$isDowngradeEnabled, Sig=$isSignatureBypassEnabled, Tablet=$isTabletMode")
                    } else if (intent.action == IpcManager.ACTION_SETTING_CHANGED) {
                        val key = intent.getStringExtra(PreferenceKeys.EXTRA_KEY)
                        val value = intent.getBooleanExtra(PreferenceKeys.EXTRA_VALUE, false)
                        val now = System.currentTimeMillis()
                        when (key) {
                            PreferenceKeys.ALLOW_DOWNGRADE -> {
                                isDowngradeEnabled = value
                                if (value) downgradeTimestamp = now
                                Logger.d(TAG, "Sync", "Setting [allow_downgrade] updated to $isDowngradeEnabled")
                            }
                            PreferenceKeys.BYPASS_SIGNATURE -> {
                                isSignatureBypassEnabled = value
                                if (value) signatureTimestamp = now
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

    // Injects logic into PackageManager components to ignore signature mismatches and version downgrades
    private fun applyHijacks(module: XposedModule, classLoader: ClassLoader): Boolean {
        return runCatching {
            val smClass = classLoader.loadClass("android.os.ServiceManager")
            val getService = smClass.getDeclaredMethod("getService", String::class.java)
            val binder = getService.invoke(null, "package") as IBinder
            val realClassLoader = binder.javaClass.classLoader

            // A. PackageInstallerService: Injects flags (0x82) to permit version downgrades
            val piClass = realClassLoader?.loadClass("com.android.server.pm.PackageInstallerService")
            piClass?.declaredMethods?.filter { it.name == "createSession" }?.forEach { m ->
                module.hookBefore(m) { chain ->
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

            // B. Installer Helpers: Forcefully returns true for internal downgrade permission checks
            listOf(
                "com.android.server.pm.InstallPackageHelper",
                "com.android.server.pm.PackageManagerServiceUtils",
                "com.android.server.pm.PackageManagerService"
            ).forEach { className ->
                runCatching {
                    val clazz = realClassLoader?.loadClass(className) ?: return@runCatching
                    clazz.declaredMethods.filter {
                        it.name == "checkDowngrade" || it.name == "isDowngradePermitted"
                    }.forEach { m ->
                        module.hook(m).intercept { chain ->
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

            // C. ComputerEngine: Hijacks signature matching to return SIGNATURE_MATCH (0)
            listOf(
                "com.android.server.pm.ComputerEngine",
                "com.android.server.pm.Computer"
            ).forEach { className ->
                runCatching {
                    val clazz = realClassLoader?.loadClass(className) ?: return@runCatching
                    clazz.declaredMethods.filter { it.name == "checkSignatures" }.forEach { m ->
                        module.hook(m).intercept { chain ->
                            val isActive = isFeatureActive(isSignatureBypassEnabled, signatureTimestamp)

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

            // D. SigningDetails: Low-level safety net to ignore signature verification failures
            val detailsClass = classLoader.loadClass("android.content.pm.SigningDetails")
            detailsClass.declaredMethods.filter {
                it.name == "checkCapability" || it.name == "hasAncestorOrSelf"
            }.forEach { m ->
                module.hook(m).intercept { chain ->
                    if (isFeatureActive(isSignatureBypassEnabled, signatureTimestamp)) {
                        true
                    } else {
                        chain.proceed()
                    }
                }
            }
            true
        }.getOrDefault(false)
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
