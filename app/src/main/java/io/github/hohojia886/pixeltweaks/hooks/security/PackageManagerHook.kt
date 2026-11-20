@file:Suppress("DiscouragedPrivateApi", "PrivateApi", "RedundantStringInterpolation", "AnInterpolationPrefixCanSimplifyTheString")

package io.github.hohojia886.pixeltweaks.hooks.security

import android.content.Context
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import io.github.hohojia886.pixeltweaks.utils.IpcDispatcher
import io.github.hohojia886.pixeltweaks.utils.IpcManager
import io.github.hohojia886.pixeltweaks.utils.Logger
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys
import io.github.hohojia886.pixeltweaks.utils.hookAfter
import io.github.hohojia886.pixeltweaks.utils.hookBefore
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages time-limited security policy bypasses inside system_server (`PackageManagerService`).
 *
 * Provides temporary 3-minute active windows for App Version Downgrade (injecting `installFlags` 0x82)
 * and Package Signature Verification Bypass during active app installations.
 */
object PackageManagerHook {

    private const val TAG = "Security"
    private const val TIMEOUT_MS = 3 * 60 * 1000L
    private const val ACTIVE_INSTALL_WINDOW_MS = 60 * 1000L

    @Volatile private var isDowngradeEnabled = false
    @Volatile private var isSignatureBypassEnabled = false

    @Volatile private var downgradeTimestamp = 0L
    @Volatile private var signatureTimestamp = 0L
    @Volatile private var lastInstallSessionTime = 0L
    private val activeInstallSessions = ConcurrentHashMap<Int, Long>()
    @Volatile private var isHooked = false
    @Volatile private var lastSignatureActiveState = false

    @Volatile private var appliedTabletMode: Boolean? = null

    private val installVerificationDepth = ThreadLocal.withInitial { 0 }
    private val installSuspensionDepth = ThreadLocal.withInitial { 0 }

    /**
     * Initializes PackageManager hooks inside system_server.
     *
     * @param module Active LSPosed [XposedModule] instance.
     * @param classLoader SystemServer ClassLoader.
     */
    fun hook(module: XposedModule, classLoader: ClassLoader) {
        if (isHooked) return
        isHooked = true

        refreshSettings(module, classLoader)
        applyHijacks(module, classLoader)
        syncSettings(module, classLoader)
        Logger.i(TAG, "Init", "Successfully initialized Policy Hijack PackageManager")
    }

    /**
     * Loads settings to memory to start enforcing rules immediately.
     * Queries preferences bundle for downgrade and signature bypass flags.
     */
    private fun refreshSettings(module: XposedModule, classLoader: ClassLoader?) {
        runCatching {
            val bundle = if (classLoader != null) IpcManager.loadPreferences(module, classLoader) else Bundle()
            val prefs = if (bundle.isEmpty) module.getRemotePreferences(IpcManager.PREF_NAME) else null

            isDowngradeEnabled = bundle.getBoolean(PreferenceKeys.ALLOW_DOWNGRADE, prefs?.getBoolean(PreferenceKeys.ALLOW_DOWNGRADE, false) ?: false)
            isSignatureBypassEnabled = bundle.getBoolean(PreferenceKeys.BYPASS_SIGNATURE, prefs?.getBoolean(PreferenceKeys.BYPASS_SIGNATURE, false) ?: false)
            downgradeTimestamp = bundle.getLong(PreferenceKeys.DOWNGRADE_TIMESTAMP, prefs?.getLong(PreferenceKeys.DOWNGRADE_TIMESTAMP, 0L) ?: 0L)
            signatureTimestamp = bundle.getLong(PreferenceKeys.SIGNATURE_TIMESTAMP, prefs?.getLong(PreferenceKeys.SIGNATURE_TIMESTAMP, 0L) ?: 0L)
        }.onFailure { e ->
            Logger.e(TAG, "Error", "Failed to refresh PackageManager settings", e)
        }
    }

    /**
     * Verifies whether requested bypass is within allowed active window.
     * Evaluates toggle state and elapsed time against 3-minute timeout.
     */
    private fun isFeatureActive(enabled: Boolean, wallTimestampMs: Long): Boolean {
        if (!enabled || wallTimestampMs <= 0L) return false
        val elapsed = System.currentTimeMillis() - wallTimestampMs
        return elapsed in 0L..<TIMEOUT_MS
    }

    /**
     * Verifies signature bypass is unexpired and tracking active installs.
     * Evaluates active install sessions and recent session timestamp window.
     */
    private fun isSignatureBypassActiveForInstall(): Boolean {
        if (!isFeatureActive(isSignatureBypassEnabled, signatureTimestamp)) {
            if (activeInstallSessions.isNotEmpty()) {
                activeInstallSessions.clear()
            }
            return false
        }

        val nowRealtime = SystemClock.elapsedRealtime()
        val iterator = activeInstallSessions.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (nowRealtime - entry.value > 5 * 60 * 1000L) {
                iterator.remove()
            }
        }

        val hasActiveSession = activeInstallSessions.isNotEmpty()
        val elapsedSinceInstall = nowRealtime - lastInstallSessionTime
        val recentInstall = elapsedSinceInstall in 0L..<ACTIVE_INSTALL_WINDOW_MS
        return hasActiveSession || recentInstall
    }

    /**
     * Determines the appropriate action for forced display density configuration.
     * Prevents race conditions during full settings synchronization where an unchanged tablet mode
     * setting might unintentionally overwrite manual display density changes or reset display scale.
     *
     * @param isFullSync True if triggered by a full settings sync broadcast, false if triggered by an individual toggle change.
     * @param requested The desired state of the tablet mode toggle from preferences.
     * @param applied The last successfully applied tablet mode state in the current process, or null if uninitialized.
     * @return `true` to apply tablet density, `false` to clear forced density back to default, or `null` if no action is needed.
     */
    internal fun decideDensityAction(isFullSync: Boolean, requested: Boolean, applied: Boolean?): Boolean? {
        if (!isFullSync) return requested
        if (requested && applied != true) return true
        if (!requested && applied == true) return false
        return null
    }

    /**
     * Evaluates whether the current thread is actively executing within an install verification boundary.
     * Thread-local depth counters ensure that signature checks are bypassed ONLY during package installation
     * and reconciliation, and NEVER during runtime IPC permission checks or post-commit package setup.
     *
     * @return True if the calling thread is inside install verification without being suspended.
     */
    internal fun isInInstallVerification(): Boolean =
        (installVerificationDepth.get() ?: 0) > 0 && (installSuspensionDepth.get() ?: 0) == 0

    /**
     * Determines whether signature verification should be spoofed for a given check.
     * Requires both the user toggle (with unexpired active install window) and active execution
     * inside the package installer verification call stack.
     *
     * @param isBypassActiveForInstall True if the signature bypass toggle is active within its 3-minute timeout window.
     * @param inInstallFlow True if the current thread is verified to be within the install verification pipeline.
     * @return True if signature comparison or capability checks should return success.
     */
    internal fun shouldBypassSignature(isBypassActiveForInstall: Boolean, inInstallFlow: Boolean): Boolean {
        return isBypassActiveForInstall && inInstallFlow
    }

    /**
     * Scopes an execution block within the install verification boundary using thread-local depth tracking.
     * Increments the verification depth before execution and guarantees restoration upon completion.
     *
     * @param block The lambda to execute within the install verification context.
     * @return The result produced by [block].
     */
    internal inline fun <T> withInstallVerification(block: () -> T): T {
        val prevDepth = installVerificationDepth.get() ?: 0
        installVerificationDepth.set(prevDepth + 1)
        return try {
            block()
        } finally {
            if (prevDepth <= 0) {
                installVerificationDepth.remove()
            } else {
                installVerificationDepth.set(prevDepth)
            }
        }
    }

    /**
     * Suspends install verification for nested execution blocks (e.g., post-commit permission setup).
     * Prevents signature bypass from leaking into permission granting or system service initialization.
     *
     * @param block The lambda to execute with install verification suspended.
     * @return The result produced by [block].
     */
    internal inline fun <T> withoutInstallVerification(block: () -> T): T {
        val prevDepth = installSuspensionDepth.get() ?: 0
        installSuspensionDepth.set(prevDepth + 1)
        return try {
            block()
        } finally {
            if (prevDepth <= 0) {
                installSuspensionDepth.remove()
            } else {
                installSuspensionDepth.set(prevDepth)
            }
        }
    }

    /**
     * Listens to IPC for dynamic enablement of downgrade and signature features.
     * Updates timestamps and density settings upon broadcast intent arrival.
     */
    private fun syncSettings(module: XposedModule, classLoader: ClassLoader) {
        IpcDispatcher.addListener { intent ->
            when (intent.action) {
                IpcManager.ACTION_SETTINGS_SYNC -> {
                    isDowngradeEnabled = intent.getBooleanExtra(PreferenceKeys.ALLOW_DOWNGRADE, false)
                    isSignatureBypassEnabled = intent.getBooleanExtra(PreferenceKeys.BYPASS_SIGNATURE, false)
                    downgradeTimestamp = intent.getLongExtra(PreferenceKeys.DOWNGRADE_TIMESTAMP, 0L)
                    signatureTimestamp = intent.getLongExtra(PreferenceKeys.SIGNATURE_TIMESTAMP, 0L)
                    val isTabletMode = intent.getBooleanExtra(PreferenceKeys.ENABLE_TABLET_MODE, false)
                    
                    val sysContext = IpcManager.getSystemContext(classLoader) ?: IpcManager.getSafeContext(classLoader, "android")
                    val action = decideDensityAction(true, isTabletMode, appliedTabletMode)
                    if (sysContext != null && action != null) {
                        updateSystemServerDensity(sysContext, action)
                    }
                    Logger.d(TAG, "Sync", "Settings updated via broadcast: DG=$isDowngradeEnabled, Sig=$isSignatureBypassEnabled, Tablet=$isTabletMode")
                }
                IpcManager.ACTION_SETTING_CHANGED -> {
                    val key = intent.getStringExtra(PreferenceKeys.EXTRA_KEY)
                    val value = intent.getBooleanExtra(PreferenceKeys.EXTRA_VALUE, false)
                    val nowMs = System.currentTimeMillis()
                    when (key) {
                        PreferenceKeys.ALLOW_DOWNGRADE -> {
                            isDowngradeEnabled = value
                            downgradeTimestamp = if (value) nowMs else 0L
                            Logger.d(TAG, "Sync", "Setting [allow_downgrade] updated to $isDowngradeEnabled")
                        }
                        PreferenceKeys.BYPASS_SIGNATURE -> {
                            isSignatureBypassEnabled = value
                            signatureTimestamp = if (value) nowMs else 0L
                            if (!value) activeInstallSessions.clear()
                            Logger.d(TAG, "Sync", "Setting [bypass_signature] updated to $isSignatureBypassEnabled")
                        }
                        PreferenceKeys.ENABLE_TABLET_MODE -> {
                            val sysContext = IpcManager.getSystemContext(classLoader) ?: IpcManager.getSafeContext(classLoader, "android")
                            val action = decideDensityAction(false, value, appliedTabletMode)
                            if (sysContext != null && action != null) {
                                updateSystemServerDensity(sysContext, action)
                            }
                            Logger.d(TAG, "Sync", "Setting [enable_tablet_mode] updated to $value")
                        }
                    }
                }
            }
        }
        IpcDispatcher.initializeOnce(module, classLoader) { ctx ->
            val bundle = IpcManager.loadPreferences(module, classLoader)
            if (!bundle.isEmpty) {
                isDowngradeEnabled = bundle.getBoolean(PreferenceKeys.ALLOW_DOWNGRADE, isDowngradeEnabled)
                isSignatureBypassEnabled = bundle.getBoolean(PreferenceKeys.BYPASS_SIGNATURE, isSignatureBypassEnabled)
                downgradeTimestamp = bundle.getLong(PreferenceKeys.DOWNGRADE_TIMESTAMP, downgradeTimestamp)
                signatureTimestamp = bundle.getLong(PreferenceKeys.SIGNATURE_TIMESTAMP, signatureTimestamp)
                if (bundle.containsKey(PreferenceKeys.ENABLE_TABLET_MODE)) {
                    val isTabletMode = bundle.getBoolean(PreferenceKeys.ENABLE_TABLET_MODE, false)
                    val action = decideDensityAction(true, isTabletMode, appliedTabletMode)
                    if (action != null) {
                        updateSystemServerDensity(ctx, action)
                    }
                }
            }
        }
    }

    /**
     * Injects logic into package managers enforcing downgrade/signature overrides.
     * Hooks PackageInstallerService, InstallPackageHelper, and ComputerEngine.
     */
    private fun applyHijacks(module: XposedModule, classLoader: ClassLoader) {
        val realClassLoader = runCatching {
            val smClass = classLoader.loadClass("android.os.ServiceManager")
            val getService = smClass.getDeclaredMethod("getService", String::class.java)
            val binder = getService.invoke(null, "package") as IBinder
            binder.javaClass.classLoader
        }.getOrNull() ?: classLoader

        runCatching {
            val piClass = realClassLoader.loadClass("com.android.server.pm.PackageInstallerService")
            
            piClass.declaredMethods.filter { it.name == "createSession" }.forEach { m ->
                module.hookBefore(m) { chain ->
                    if (isFeatureActive(isDowngradeEnabled, downgradeTimestamp)) {
                        val params = chain.args.getOrNull(0)
                        if (params != null) {
                            runCatching {
                                val f = params.javaClass.getDeclaredField("installFlags").apply { isAccessible = true }
                                f.setInt(params, f.getInt(params) or 0x00000082)
                                Logger.i(TAG, "Active", "Injected Downgrade flags (0x82)")
                            }.onFailure { e ->
                                Logger.e(TAG, "Error", "Failed to inject installFlags", e)
                            }
                        }
                    }
                }
            }

            piClass.declaredMethods.filter { it.name == "createSession" }.forEach { m ->
                module.hookAfter(m) { _, result ->
                    lastInstallSessionTime = SystemClock.elapsedRealtime()
                    val sessionId = result as? Int ?: -1
                    if (sessionId > 0 && isFeatureActive(isSignatureBypassEnabled, signatureTimestamp)) {
                        activeInstallSessions[sessionId] = SystemClock.elapsedRealtime()
                        Logger.i(TAG, "Session", "Tracked active install session ID: $sessionId")
                    }
                }
            }

            piClass.declaredMethods.filter { it.name == "abandonSession" || it.name == "cleanupSession" }.forEach { m ->
                module.hookBefore(m) { chain ->
                    val sessionId = chain.args.getOrNull(0) as? Int ?: -1
                    if (sessionId > 0) {
                        activeInstallSessions.remove(sessionId)
                        Logger.i(TAG, "Session", "Removed install session ID: $sessionId")
                    }
                }
            }
        }.onFailure { e ->
            Logger.e(TAG, "SectionA", "PackageInstallerService hook skipped/failed", e)
        }

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
                }.onFailure { e ->
                    Logger.e(TAG, "SectionB", "Failed to hook Installer Helpers for $className", e)
                }
            }
        }.onFailure { e ->
            Logger.e(TAG, "SectionB", "Installer Helpers hook skipped/failed", e)
        }

        var installEntryHookCount = 0
        val installEntryTargets = listOf(
            "com.android.server.pm.InstallPackageHelper" to setOf(
                "installPackagesTraced",
                "installPackagesLI",
                "preparePackageLI",
                "preparePackage",
                "scanPackageTracedLI"
            ),
            "com.android.server.pm.ReconcilePackageUtils" to setOf("reconcilePackages"),
            "com.android.server.pm.PackageManagerServiceUtils" to setOf(
                "verifySignatures",
                "verifySignaturesCompat",
                "verifySignaturesRecover"
            ),
            "com.android.server.pm.PackageInstallerSession" to setOf(
                "streamValidateAndInstall",
                "handleStreamValidateAndInstall",
                "verifyNonStaged",
                "verifyAPKs",
                "verify"
            )
        )
        installEntryTargets.forEach { (className, methodNames) ->
            runCatching {
                val clazz = realClassLoader.loadClass(className) ?: return@runCatching
                clazz.declaredMethods.filter { it.name in methodNames }.forEach { m ->
                    module.hook(m).intercept { chain ->
                        withInstallVerification {
                            chain.proceed()
                        }
                    }
                    installEntryHookCount++
                }
            }.onFailure { e ->
                Logger.e(TAG, "Error", "Failed to hook install entry $className", e)
            }
        }

        val installSuspendTargets = listOf(
            "com.android.server.pm.InstallPackageHelper" to setOf("executePostCommitSteps"),
            "com.android.server.pm.permission.PermissionManagerServiceImpl" to setOf("onPackageInstalled")
        )
        installSuspendTargets.forEach { (className, methodNames) ->
            runCatching {
                val clazz = realClassLoader.loadClass(className) ?: return@runCatching
                clazz.declaredMethods.filter { it.name in methodNames }.forEach { m ->
                    module.hook(m).intercept { chain ->
                        withoutInstallVerification {
                            chain.proceed()
                        }
                    }
                }
            }.onFailure { e ->
                Logger.e(TAG, "Error", "Failed to hook suspend boundary $className", e)
            }
        }

        Logger.i(TAG, "Applied", "Hooked $installEntryHookCount install verification entry methods")

        if (installEntryHookCount == 0) {
            Logger.w(TAG, "Blocked", "No install verification entry point found; skipping signature bypass hooks to prevent global bypass")
            return
        }

        runCatching {
            listOf(
                "com.android.server.pm.ComputerEngine",
                "com.android.server.pm.Computer"
            ).forEach { className ->
                runCatching {
                    val clazz = realClassLoader.loadClass(className) ?: return@runCatching
                    clazz.declaredMethods.filter { it.name == "checkSignatures" }.forEach { m ->
                        module.hook(m).intercept { chain ->
                            val isActive = shouldBypassSignature(isSignatureBypassActiveForInstall(), isInInstallVerification())

                            if (isActive != lastSignatureActiveState) {
                                lastSignatureActiveState = isActive
                                Logger.i(TAG, "Active", "Signature Bypass state changed to: ${if (isActive) "ACTIVE" else "INACTIVE"}")
                            }

                            if (isActive) {
                                return@intercept 0
                            }
                            chain.proceed()
                        }
                    }
                }.onFailure { e ->
                    Logger.e(TAG, "SectionC", "Failed to hook ComputerEngine for $className", e)
                }
            }
        }.onFailure { e ->
            Logger.e(TAG, "SectionC", "ComputerEngine hook skipped/failed", e)
        }

        runCatching {
            val utilsClass = realClassLoader.loadClass("com.android.server.pm.PackageManagerServiceUtils")
            utilsClass.declaredMethods.filter {
                it.name == "compareSignatures" && it.returnType == Int::class.javaPrimitiveType
            }.forEach { m ->
                module.hook(m).intercept { chain ->
                    if (shouldBypassSignature(isSignatureBypassActiveForInstall(), isInInstallVerification())) {
                        Logger.i(TAG, "Active", "Bypassed PackageManagerServiceUtils#compareSignatures")
                        0
                    } else {
                        chain.proceed()
                    }
                }
            }
        }.onFailure { e ->
            Logger.e(TAG, "Error", "PackageManagerServiceUtils#compareSignatures hook skipped/failed", e)
        }

        runCatching {
            val detailsClass = classLoader.loadClass("android.content.pm.SigningDetails")
            val bypassMethods = setOf(
                "checkCapability",
                "checkCapabilityRecover",
                "hasAncestorOrSelf",
                "hasAncestor",
                "signaturesMatchExactly",
                "hasCommonSignerWithCapability"
            )
            detailsClass.declaredMethods.filter {
                it.name in bypassMethods && (it.returnType == Boolean::class.javaPrimitiveType || it.returnType == Boolean::class.java)
            }.forEach { m ->
                module.hook(m).intercept { chain ->
                    if (shouldBypassSignature(isSignatureBypassActiveForInstall(), isInInstallVerification())) {
                        Logger.d(TAG, "Active", "Bypassed SigningDetails#${m.name}")
                        true
                    } else {
                        chain.proceed()
                    }
                }
            }
        }.onFailure { e ->
            Logger.e(TAG, "Error", "SigningDetails hook skipped/failed", e)
        }
    }

    @Volatile private var cachedWms: Any? = null
    @Volatile private var cachedSetDpiMethod: Method? = null
    @Volatile private var cachedClearDpiMethod: Method? = null

    /**
     * Obtains core WindowManager binder instance using ServiceManager.
     * Reflectively calls getService and converts IBinder to IWindowManager.
     */
    private fun getIWindowManager(): Any? {
        if (cachedWms != null) return cachedWms
        return runCatching {
            val smClass = Class.forName("android.os.ServiceManager")
            val getService = smClass.getDeclaredMethod("getService", String::class.java)
            val binder = getService.invoke(null, "window") as IBinder
            val iwmClass = Class.forName("android.view.IWindowManager" + '$' + "Stub")
            val asInterface = iwmClass.getDeclaredMethod("asInterface", IBinder::class.java)
            val wms = asInterface.invoke(null, binder)
            cachedWms = wms
            wms
        }.getOrNull()
    }

    /**
     * Configures system density settings for tablet-like interfaces.
     * Calls setForcedDisplayDensityForUser or updates Settings.Secure fallback.
     */
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
                        cachedSetDpiMethod!!.invoke(wms, android.view.Display.DEFAULT_DISPLAY, targetDpi, -2)
                        appliedTabletMode = true
                        Logger.i("Density", "Success", "Successfully called setForcedDisplayDensityForUser(DEFAULT_DISPLAY, $targetDpi, USER_CURRENT)")
                        return
                    }
                } else {
                    if (cachedClearDpiMethod == null) {
                        cachedClearDpiMethod = wms.javaClass.methods.firstOrNull { it.name == "clearForcedDisplayDensityForUser" }
                    }
                    if (cachedClearDpiMethod != null) {
                        cachedClearDpiMethod!!.invoke(wms, android.view.Display.DEFAULT_DISPLAY, -2)
                        appliedTabletMode = false
                        Logger.i("Density", "Success", "Successfully called clearForcedDisplayDensityForUser(DEFAULT_DISPLAY, USER_CURRENT)")
                        return
                    }
                }
            }

            if (enabled) {
                val dm = context.resources.displayMetrics
                val minPx = minOf(dm.widthPixels, dm.heightPixels)
                val targetDpi = (minPx * 160) / 600
                Settings.Secure.putInt(context.contentResolver, "display_density_forced", targetDpi)
                appliedTabletMode = true
                Logger.i("Density", "Active", "Fallback: Set display_density_forced = $targetDpi")
            } else {
                Settings.Secure.putString(context.contentResolver, "display_density_forced", null)
                appliedTabletMode = false
                Logger.i("Density", "Active", "Fallback: Cleared display_density_forced")
            }
        }.onFailure { e ->
            Logger.e("Density", "Error", "Failed to update display density in SystemServer", e)
        }
    }
}
