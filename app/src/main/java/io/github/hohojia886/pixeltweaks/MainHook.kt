package io.github.hohojia886.pixeltweaks

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam
import io.github.hohojia886.pixeltweaks.hooks.PixelHook
import io.github.hohojia886.pixeltweaks.hooks.apps.CallRecordingHook
import io.github.hohojia886.pixeltweaks.hooks.apps.CallNotesHook
import io.github.hohojia886.pixeltweaks.hooks.ui.ClearAllButtonHook
import io.github.hohojia886.pixeltweaks.hooks.interaction.DoubleTapToSleepHook
import io.github.hohojia886.pixeltweaks.hooks.interaction.EasyUnlockHook
import io.github.hohojia886.pixeltweaks.hooks.ui.NetworkTrafficHook
import io.github.hohojia886.pixeltweaks.hooks.system.PackageManagerHook
import io.github.hohojia886.pixeltweaks.hooks.system.QuickSettingsHook
import io.github.hohojia886.pixeltweaks.hooks.ui.ScreenshotHook
import io.github.hohojia886.pixeltweaks.utils.Logger

private const val SYSTEMUI_PKG = "com.android.systemui"
private val LAUNCHER_PKGS = setOf(
    "com.google.android.apps.nexuslauncher",
    "com.google.android.launcher",
    "com.android.launcher3"
)
private val DIALER_PKGS = setOf("com.google.android.dialer", "com.android.dialer")

/**
 * MainHook: The primary entry point for the LSPosed module.
 * Responsible for process identification, global log synchronization, 
 * and dispatching specific hook modules to their target packages.
 */

// 1. CallNotes: AI silence logic in Dialer and system_server
private object CallNotesEntry : PixelHook {
    override val name = "CallNotes"
    override fun matches(packageName: String, isRootSystemServer: Boolean) =
        isRootSystemServer || packageName in DIALER_PKGS
    override fun apply(module: XposedModule, classLoader: ClassLoader, param: PackageLoadedParam) {
        CallNotesHook.hook(module, classLoader, param.packageName)
    }
}

// 2. CallRecording: Enablement in Google Dialer
private object CallRecordingEntry : PixelHook {
    override val name = "CallRecording"
    override fun matches(packageName: String, isRootSystemServer: Boolean) =
        BuildConfig.ENABLE_CALL_RECORDING && packageName in DIALER_PKGS
    override fun apply(module: XposedModule, classLoader: ClassLoader, param: PackageLoadedParam) {
        CallRecordingHook.hookFull(module, classLoader, param.packageName, param.applicationInfo.sourceDir)
    }
}

// 3. ClearAllButton: "Clear all" button in Pixel Launcher Recents
private object ClearAllButtonEntry : PixelHook {
    override val name = "ClearAllButton"
    override fun matches(packageName: String, isRootSystemServer: Boolean) = packageName in LAUNCHER_PKGS
    override fun apply(module: XposedModule, classLoader: ClassLoader, param: PackageLoadedParam) {
        ClearAllButtonHook.hook(module, classLoader)
    }
}

// 4. DoubleTapToSleep: Gesture handling in SystemUI and Launchers
private object DoubleTapToSleepEntry : PixelHook {
    override val name = "DoubleTapToSleep"
    override fun matches(packageName: String, isRootSystemServer: Boolean) =
        packageName == SYSTEMUI_PKG || packageName in LAUNCHER_PKGS
    override fun apply(module: XposedModule, classLoader: ClassLoader, param: PackageLoadedParam) {
        DoubleTapToSleepHook.hook(module, classLoader)
    }
}

// 5. EasyUnlock: Auto PIN confirm and learning
private object EasyUnlockEntry : PixelHook {
    override val name = "EasyUnlock"
    override fun matches(packageName: String, isRootSystemServer: Boolean) = packageName == SYSTEMUI_PKG
    override fun apply(module: XposedModule, classLoader: ClassLoader, param: PackageLoadedParam) {
        EasyUnlockHook.hook(module, classLoader, param.packageName)
    }
}

// 6. QuickSettings: WiFi and Mobile Data tile fixes
private object QuickSettingsEntry : PixelHook {
    override val name = "QuickSettings"
    override fun matches(packageName: String, isRootSystemServer: Boolean) = packageName == SYSTEMUI_PKG
    override fun apply(module: XposedModule, classLoader: ClassLoader, param: PackageLoadedParam) {
        QuickSettingsHook.hook(module, classLoader)
    }
}

// 7. Screenshot: Bypassing FLAG_SECURE (Server level)
private object ScreenshotServerEntry : PixelHook {
    override val name = "Screenshot-Server"
    override fun matches(packageName: String, isRootSystemServer: Boolean) = 
        isRootSystemServer || (android.os.Process.myUid() == 1000)
    override fun apply(module: XposedModule, classLoader: ClassLoader, param: PackageLoadedParam) {
        ScreenshotHook.hookSystemServer(module, classLoader)
    }
}

// 7. Screenshot: Bypassing FLAG_SECURE (SystemUI level)
private object ScreenshotSystemUIEntry : PixelHook {
    override val name = "Screenshot-SystemUI"
    override fun matches(packageName: String, isRootSystemServer: Boolean) = packageName == SYSTEMUI_PKG
    override fun apply(module: XposedModule, classLoader: ClassLoader, param: PackageLoadedParam) {
        ScreenshotHook.hookSystemUI(module, classLoader)
    }
}

// 7. Screenshot: Bypassing FLAG_SECURE (App level)
private object ScreenshotAppEntry : PixelHook {
    override val name = "Screenshot-App"
    override fun matches(packageName: String, isRootSystemServer: Boolean) =
        packageName != SYSTEMUI_PKG && packageName !in DIALER_PKGS
    override fun apply(module: XposedModule, classLoader: ClassLoader, param: PackageLoadedParam) {
        ScreenshotHook.hookApp(module, classLoader)
    }
}

// 8. PackageManager: Downgrade and Signature bypass (Security)
private object PackageManagerEntry : PixelHook {
    override val name = "PackageManager"
    override fun matches(packageName: String, isRootSystemServer: Boolean) = 
        isRootSystemServer || (android.os.Process.myUid() == 1000)
    override fun apply(module: XposedModule, classLoader: ClassLoader, param: PackageLoadedParam) {
        PackageManagerHook.hook(module, classLoader)
    }
}

// 9. NetworkTraffic: Traffic indicator in SystemUI
private object NetworkTrafficEntry : PixelHook {
    override val name = "NetworkTraffic"
    override fun matches(packageName: String, isRootSystemServer: Boolean) = packageName == SYSTEMUI_PKG
    override fun apply(module: XposedModule, classLoader: ClassLoader, param: PackageLoadedParam) {
        NetworkTrafficHook.hook(module, classLoader)
    }
}

class MainHook : XposedModule() {

    private var isSystemServerProcess = false // Tracks if current process is the system_server

    private val allHooks: List<PixelHook> = listOf(
        CallNotesEntry,
        CallRecordingEntry,
        ClearAllButtonEntry,
        DoubleTapToSleepEntry,
        EasyUnlockEntry,
        QuickSettingsEntry,
        ScreenshotServerEntry,
        ScreenshotSystemUIEntry,
        ScreenshotAppEntry,
        PackageManagerEntry,
        NetworkTrafficEntry
    )

    // Lifecycle: Initializes logging and identifies process type upon module load
    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        super.onModuleLoaded(param)
        isSystemServerProcess = param.isSystemServer || android.os.Process.myUid() == 1000
        Logger.sync(this)
        Logger.i(
            "Hook", "Started",
            "Module loaded (PID: ${android.os.Process.myPid()}, UID: ${android.os.Process.myUid()}, isSys: $isSystemServerProcess)"
        )
    }

    // Lifecycle: Iterates through defined hooks and applies those matching the current package
    override fun onPackageLoaded(param: PackageLoadedParam) {
        super.onPackageLoaded(param)
        val pkgName = param.packageName ?: return
        val classLoader = param.defaultClassLoader ?: return
        
        val isRootSystemServer = pkgName == "android" && isSystemServerProcess

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
