package io.github.hohojia886.pixeltweaks.hooks.system

import io.github.hohojia886.pixeltweaks.utils.IpcManager
import io.github.hohojia886.pixeltweaks.utils.Logger
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys
import io.github.hohojia886.pixeltweaks.utils.hookBefore
import io.github.libxposed.api.XposedModule

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
        
        refreshSettings(module)

        if (applyHijacks(module, classLoader)) {
            isHooked = true
            Logger.i(TAG, "Init", "Successfully initialized Policy Hijack PackageManager")
            syncSettings(module, classLoader)
        }
    }

    // Direct read from RemotePreferences to establish initial security state
    private fun refreshSettings(module: XposedModule) {
        runCatching {
            val prefs = module.getRemotePreferences(IpcManager.PREF_NAME)
            isDowngradeEnabled = prefs.getBoolean(PreferenceKeys.ALLOW_DOWNGRADE, false)
            isSignatureBypassEnabled = prefs.getBoolean(PreferenceKeys.BYPASS_SIGNATURE, false)
            downgradeTimestamp = prefs.getLong(PreferenceKeys.DOWNGRADE_TIMESTAMP, 0L)
            signatureTimestamp = prefs.getLong(PreferenceKeys.SIGNATURE_TIMESTAMP, 0L)
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
                    } else {
                        val key = intent.getStringExtra(PreferenceKeys.EXTRA_KEY)
                        val value = intent.getBooleanExtra(PreferenceKeys.EXTRA_VALUE, false)
                        val now = System.currentTimeMillis()
                        when (key) {
                            PreferenceKeys.ALLOW_DOWNGRADE -> {
                                isDowngradeEnabled = value
                                if (value) downgradeTimestamp = now
                            }
                            PreferenceKeys.BYPASS_SIGNATURE -> {
                                isSignatureBypassEnabled = value
                                if (value) signatureTimestamp = now
                            }
                        }
                    }
                    Logger.d(TAG, "Sync", "Settings updated via broadcast: DG=$isDowngradeEnabled, Sig=$isSignatureBypassEnabled")
                }
            }
        }.start()
    }

    // Injects logic into PackageManager components to ignore signature mismatches and version downgrades
    private fun applyHijacks(module: XposedModule, classLoader: ClassLoader): Boolean {
        return runCatching {
            val smClass = classLoader.loadClass("android.os.ServiceManager")
            val getService = smClass.getDeclaredMethod("getService", String::class.java)
            val binder = getService.invoke(null, "package") as android.os.IBinder
            val realClassLoader = binder.javaClass.classLoader

            // A. PackageInstallerService: Injects flags (0x82) to permit version downgrades
            val piClass = realClassLoader.loadClass("com.android.server.pm.PackageInstallerService")
            piClass.declaredMethods.filter { it.name == "createSession" }.forEach { m ->
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
                    val clazz = realClassLoader.loadClass(className)
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
                    val clazz = realClassLoader.loadClass(className)
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
}
