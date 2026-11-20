package io.github.hohojia886.pixeltweaks

import android.os.Process
import io.github.hohojia886.pixeltweaks.hooks.PixelHook
import io.github.hohojia886.pixeltweaks.hooks.battery.EnergyRingHook
import io.github.hohojia886.pixeltweaks.hooks.gestures.DoubleTapToSleepHook
import io.github.hohojia886.pixeltweaks.hooks.`interface`.BatteryPercentHook
import io.github.hohojia886.pixeltweaks.hooks.`interface`.ClearAllButtonHook
import io.github.hohojia886.pixeltweaks.hooks.`interface`.HideVpnIconHook
import io.github.hohojia886.pixeltweaks.hooks.`interface`.NetworkTrafficHook
import io.github.hohojia886.pixeltweaks.hooks.quicksettings.QuickSettingsHook
import io.github.hohojia886.pixeltweaks.hooks.security.EasyUnlockHook
import io.github.hohojia886.pixeltweaks.hooks.security.PackageManagerHook
import io.github.hohojia886.pixeltweaks.hooks.security.ScreenshotHook
import io.github.hohojia886.pixeltweaks.utils.Logger
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam

private const val SYSTEMUI_PKG = "com.android.systemui"
private val LAUNCHER_PKGS = setOf(
    "com.google.android.apps.nexuslauncher",
    "com.google.android.launcher",
    "com.android.launcher3"
)

/**
 * ClearAllButton: "Clear all" button entry in Pixel Launcher Recents.
 * Handles process matching for Launcher packages and applies hook logic.
 */
private object ClearAllButtonEntry : PixelHook {
    override val name = "ClearAllButton"
    /**
     * Checks if the package matches the launcher packages.
     * Evaluates package name against the allowed launcher package set.
     */
    override fun matches(packageName: String, isRootSystemServer: Boolean) = packageName in LAUNCHER_PKGS
    /**
     * Applies the ClearAllButton hook to the target package.
     * Delegates hook initialization to ClearAllButtonHook implementation.
     */
    override fun apply(module: XposedModule, classLoader: ClassLoader, param: PackageLoadedParam) {
        ClearAllButtonHook.hook(module, classLoader)
    }
}

/**
 * DoubleTapToSleep: Gesture handling entry in SystemUI and Launchers.
 * Handles process matching for SystemUI and Launcher packages.
 */
private object DoubleTapToSleepEntry : PixelHook {
    override val name = "DoubleTapToSleep"
    /**
     * Checks if the package matches SystemUI or launcher packages.
     * Evaluates package name against SystemUI and launcher package set.
     */
    override fun matches(packageName: String, isRootSystemServer: Boolean) =
        packageName == SYSTEMUI_PKG || packageName in LAUNCHER_PKGS
    /**
     * Applies the DoubleTapToSleep hook to the target package.
     * Delegates hook initialization to DoubleTapToSleepHook implementation.
     */
    override fun apply(module: XposedModule, classLoader: ClassLoader, param: PackageLoadedParam) {
        DoubleTapToSleepHook.hook(module, classLoader)
    }
}

/**
 * EasyUnlock: Auto PIN confirm and learning feature entry point.
 * Handles process matching for SystemUI and applies keyguard hooks.
 */
private object EasyUnlockEntry : PixelHook {
    override val name = "EasyUnlock"
    /**
     * Checks if the package matches the SystemUI package name.
     * Returns true if the package matches com.android.systemui.
     */
    override fun matches(packageName: String, isRootSystemServer: Boolean) = packageName == SYSTEMUI_PKG
    /**
     * Applies the EasyUnlock hook to the target package.
     * Delegates hook initialization to EasyUnlockHook implementation.
     */
    override fun apply(module: XposedModule, classLoader: ClassLoader, param: PackageLoadedParam) {
        EasyUnlockHook.hook(module, classLoader, param.packageName)
    }
}

/**
 * QuickSettings: WiFi and Mobile Data tile fixes feature entry point.
 * Handles process matching for SystemUI and applies tile hooks.
 */
private object QuickSettingsEntry : PixelHook {
    override val name = "QuickSettings"
    /**
     * Checks if the package matches the SystemUI package name.
     * Returns true if the package matches com.android.systemui.
     */
    override fun matches(packageName: String, isRootSystemServer: Boolean) = packageName == SYSTEMUI_PKG
    /**
     * Applies the QuickSettings hook to the target package.
     * Delegates hook initialization to QuickSettingsHook implementation.
     */
    override fun apply(module: XposedModule, classLoader: ClassLoader, param: PackageLoadedParam) {
        QuickSettingsHook.hook(module, classLoader)
    }
}

/**
 * Screenshot-Server: Bypassing FLAG_SECURE at System Server level.
 * Handles process matching for root system server process.
 */
private object ScreenshotServerEntry : PixelHook {
    override val name = "Screenshot-Server"
    /**
     * Checks if the target process is the root system server process.
     * Evaluates isRootSystemServer or UID 1000 process identity.
     */
    override fun matches(packageName: String, isRootSystemServer: Boolean) = 
        isRootSystemServer || (Process.myUid() == 1000)
    /**
     * Applies the system server screenshot hook to the target process.
     * Delegates hook initialization to ScreenshotHook.hookSystemServer.
     */
    override fun apply(module: XposedModule, classLoader: ClassLoader, param: PackageLoadedParam) {
        ScreenshotHook.hookSystemServer(module, classLoader)
    }
}

/**
 * Screenshot-SystemUI: Bypassing FLAG_SECURE at SystemUI level.
 * Handles process matching for SystemUI package.
 */
private object ScreenshotSystemUIEntry : PixelHook {
    override val name = "Screenshot-SystemUI"
    /**
     * Checks if the package matches the SystemUI package name.
     * Returns true if the package matches com.android.systemui.
     */
    override fun matches(packageName: String, isRootSystemServer: Boolean) = packageName == SYSTEMUI_PKG
    /**
     * Applies the SystemUI screenshot hook to the target package.
     * Delegates hook initialization to ScreenshotHook.hookSystemUI.
     */
    override fun apply(module: XposedModule, classLoader: ClassLoader, param: PackageLoadedParam) {
        ScreenshotHook.hookSystemUI(module, classLoader)
    }
}

/**
 * Screenshot-App: Bypassing FLAG_SECURE at application level.
 * Handles process matching for third-party app packages.
 */
private object ScreenshotAppEntry : PixelHook {
    override val name = "Screenshot-App"
    /**
     * Checks if the package is a non-SystemUI application package.
     * Returns true for all packages except com.android.systemui.
     */
    override fun matches(packageName: String, isRootSystemServer: Boolean) = packageName != SYSTEMUI_PKG
    /**
     * Applies the application-level screenshot hook to the target package.
     * Delegates hook initialization to ScreenshotHook.hookApp.
     */
    override fun apply(module: XposedModule, classLoader: ClassLoader, param: PackageLoadedParam) {
        ScreenshotHook.hookApp(module, classLoader)
    }
}

/**
 * PackageManager: Downgrade and Signature bypass security feature entry.
 * Handles process matching for system server process.
 */
private object PackageManagerEntry : PixelHook {
    override val name = "PackageManager"
    /**
     * Checks if the target process is the root system server process.
     * Evaluates isRootSystemServer or UID 1000 process identity.
     */
    override fun matches(packageName: String, isRootSystemServer: Boolean) = 
        isRootSystemServer || (Process.myUid() == 1000)
    /**
     * Applies the PackageManager security bypass hooks to system server.
     * Delegates hook initialization to PackageManagerHook implementation.
     */
    override fun apply(module: XposedModule, classLoader: ClassLoader, param: PackageLoadedParam) {
        PackageManagerHook.hook(module, classLoader)
    }
}

/**
 * NetworkTraffic: Real-time traffic indicator in SystemUI status bar.
 * Handles process matching for SystemUI package.
 */
private object NetworkTrafficEntry : PixelHook {
    override val name = "NetworkTraffic"
    /**
     * Checks if the package matches the SystemUI package name.
     * Returns true if the package matches com.android.systemui.
     */
    override fun matches(packageName: String, isRootSystemServer: Boolean) = packageName == SYSTEMUI_PKG
    /**
     * Applies the NetworkTraffic hook to the target package.
     * Delegates hook initialization to NetworkTrafficHook implementation.
     */
    override fun apply(module: XposedModule, classLoader: ClassLoader, param: PackageLoadedParam) {
        NetworkTrafficHook.hook(module, classLoader)
    }
}

/**
 * EnergyRing: Camera cutout battery energy ring in SystemUI.
 * Handles process matching for SystemUI package.
 */
private object EnergyRingEntry : PixelHook {
    override val name = "EnergyRing"
    /**
     * Checks if the package matches the SystemUI package name.
     * Returns true if the package matches com.android.systemui.
     */
    override fun matches(packageName: String, isRootSystemServer: Boolean) = packageName == SYSTEMUI_PKG
    /**
     * Applies the EnergyRing hook to the target package.
     * Delegates hook initialization to EnergyRingHook implementation.
     */
    override fun apply(module: XposedModule, classLoader: ClassLoader, param: PackageLoadedParam) {
        EnergyRingHook.hook(module, classLoader, param.packageName)
    }
}

/**
 * BatteryPercent: Text battery percentage at far-right of status bar in SystemUI.
 * Handles process matching for SystemUI package.
 */
private object BatteryPercentEntry : PixelHook {
    override val name = "BatteryPercent"
    /**
     * Checks if the package matches the SystemUI package name.
     * Returns true if the package matches com.android.systemui.
     */
    override fun matches(packageName: String, isRootSystemServer: Boolean) = packageName == SYSTEMUI_PKG
    /**
     * Applies the BatteryPercent hook to the target package.
     * Delegates hook initialization to BatteryPercentHook implementation.
     */
    override fun apply(module: XposedModule, classLoader: ClassLoader, param: PackageLoadedParam) {
        BatteryPercentHook.hook(module, classLoader)
    }
}

/**
 * HideVpnIcon: Hides the VPN icon from the status bar in SystemUI.
 * Handles process matching for SystemUI package.
 */
private object HideVpnIconEntry : PixelHook {
    override val name = "HideVpnIcon"
    /**
     * Checks if the package matches the SystemUI package name.
     * Returns true if the package matches com.android.systemui.
     */
    override fun matches(packageName: String, isRootSystemServer: Boolean) = packageName == SYSTEMUI_PKG
    /**
     * Applies the HideVpnIcon hook to the target package.
     * Delegates hook initialization to HideVpnIconHook implementation.
     */
    override fun apply(module: XposedModule, classLoader: ClassLoader, param: PackageLoadedParam) {
        HideVpnIconHook.hook(module, classLoader)
    }
}

/**
 * Main entry point class for the LSPosed module handling dispatching.
 * Listens for module loading and package attachment events.
 */
class MainHook : XposedModule() {

    private var isSystemServerProcess = false

    private val allHooks: List<PixelHook> = listOf(
        ClearAllButtonEntry,
        DoubleTapToSleepEntry,
        EasyUnlockEntry,
        QuickSettingsEntry,
        ScreenshotSystemUIEntry,
        ScreenshotAppEntry,
        NetworkTrafficEntry,
        EnergyRingEntry,
        BatteryPercentEntry,
        HideVpnIconEntry
    )

    /**
     * Initializes logging and identifies process type upon module load.
     * Evaluates whether current process is system_server or UID 1000.
     */
    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        super.onModuleLoaded(param)
        isSystemServerProcess = param.isSystemServer || Process.myUid() == 1000
        Logger.sync(this)
        Logger.i(
            "Hook", "Started",
            "Module loaded (PID: ${Process.myPid()}, UID: ${Process.myUid()}, isSys: $isSystemServerProcess)"
        )
    }

    /**
     * Invoked when system_server is starting up.
     * Hooks core system services (PackageManager, Screenshot) directly using the system ClassLoader,
     * and defers IPC receiver initialization until ActivityManagerService.systemReady is reached.
     *
     * @param param System server startup parameters containing system ClassLoader.
     */
    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        super.onSystemServerStarting(param)
        isSystemServerProcess = true
        val cl = param.classLoader
        
        runCatching { 
            io.github.hohojia886.pixeltweaks.hooks.security.PackageManagerHook.hook(this, cl) 
        }.onFailure { Logger.e("Hook", "Error", "PackageManager hook initialization failed", it) }
        
        runCatching { 
            io.github.hohojia886.pixeltweaks.hooks.security.ScreenshotHook.hookSystemServer(this, cl) 
        }.onFailure { Logger.e("Hook", "Error", "Screenshot system server hook failed", it) }
        
        runCatching { 
            io.github.hohojia886.pixeltweaks.hooks.security.ScreenshotHook.hookApp(this, cl) 
        }.onFailure { Logger.e("Hook", "Error", "Screenshot app hook in system_server failed", it) }

        // Delay receiver registration until AMS is ready to avoid early-boot Binder/context deadlocks
        runCatching {
            val amsClass = cl.loadClass("com.android.server.am.ActivityManagerService")
            val systemReadyMethods = amsClass.declaredMethods.filter { it.name == "systemReady" }
            if (systemReadyMethods.isEmpty()) {
                Logger.e("Hook", "Error", "No ActivityManagerService.systemReady method found")
            } else {
                val ipcInitialized = java.util.concurrent.atomic.AtomicBoolean(false)
                systemReadyMethods.forEach { systemReady ->
                    this.hook(systemReady).intercept { chain ->
                        val result = chain.proceed()
                        if (ipcInitialized.compareAndSet(false, true)) {
                            Logger.i("Hook", "Started", "AMS systemReady reached, initializing IPC in system_server")
                            Logger.sync(this, cl)
                            io.github.hohojia886.pixeltweaks.utils.IpcDispatcher.initializeOnce(this, cl, isSystemReady = true)
                        }
                        result
                    }
                }
            }
        }.onFailure { Logger.e("Hook", "Error", "Failed to hook AMS systemReady", it) }
    }

    /**
     * Iterates through defined hooks and applies those matching current package.
     * Invokes apply method on matching hook entries with target ClassLoader.
     */
    override fun onPackageLoaded(param: PackageLoadedParam) {
        super.onPackageLoaded(param)
        if (isSystemServerProcess) return
        
        @Suppress("USELESS_ELVIS")
        val pkgName = param.packageName ?: return
        @Suppress("USELESS_ELVIS")
        val classLoader = param.defaultClassLoader ?: return
        
        val isRootSystemServer = false // Handled in onSystemServerStarting

        allHooks.forEach { hook ->
            if (!hook.matches(pkgName, isRootSystemServer)) return@forEach
            try {
                hook.apply(this, classLoader, param)
                Logger.i("Hook", "Applied", "[${hook.name}] successfully set up for $pkgName")
            } catch (t: Throwable) {
                Logger.e("Hook", "Error", "[${hook.name}] failed for $pkgName", t)
            }
        }
    }
}
