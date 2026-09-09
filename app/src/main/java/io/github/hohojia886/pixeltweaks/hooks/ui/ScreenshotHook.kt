package io.github.hohojia886.pixeltweaks.hooks.ui

import android.app.Application
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import io.github.hohojia886.pixeltweaks.utils.IpcManager
import io.github.hohojia886.pixeltweaks.utils.Logger
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys
import io.github.hohojia886.pixeltweaks.utils.hookBefore
import io.github.libxposed.api.XposedModule

/**
 * ScreenshotHook: Bypasses screenshot restrictions (FLAG_SECURE) system-wide.
 * Hijacks SurfaceControl to prevent secure surface creation in apps, and WMS 
 * to strip the FLAG_SECURE from windows during registration and rendering.
 */
object ScreenshotHook {

    private const val TAG = "Screenshot"
    @Volatile private var isEnabled = true // Feature master toggle
    @Volatile private var isSystemHooked = false // Prevent duplicate hooking in system_server

    // Intercepts app-level surface creation to force non-secure surfaces
    fun hookApp(module: XposedModule, classLoader: ClassLoader) {
        runCatching {
            val builderClass = classLoader.loadClass("android.view.SurfaceControl\$Builder")
            module.hookBefore(builderClass.getDeclaredMethod("setSecure", Boolean::class.java)) { chain ->
                if (isEnabled) {
                    chain.args[0] = false
                    Logger.d(TAG, "Action", "Forced SurfaceControl.setSecure(false)")
                }
            }
        }
    }

    // Secondary entry for SystemUI to ensure its own settings are synchronized
    fun hookSystemUI(module: XposedModule, classLoader: ClassLoader) {
        Logger.i(TAG, "Init", "Initializing ScreenshotHook for [SystemUI]")
        syncSettings(module, classLoader)
    }

    // Injects logic into WindowManagerService to strip FLAG_SECURE from all incoming windows
    fun hookSystemServer(module: XposedModule, classLoader: ClassLoader) {
        if (isSystemHooked) return
        
        val realClassLoader = runCatching {
            val smClass = classLoader.loadClass("android.os.ServiceManager")
            val getService = smClass.getDeclaredMethod("getService", String::class.java)
            val binder = getService.invoke(null, "window") as IBinder
            binder.javaClass.classLoader
        }.getOrNull() ?: classLoader

        runCatching {
            val wmsClass = realClassLoader.loadClass("com.android.server.wm.WindowManagerService")
            
            // Strips the FLAG_SECURE bit from WindowManager.LayoutParams during add/relayout
            val windowMethods = listOf("addWindow", "relayoutWindow")
            wmsClass.declaredMethods.filter { it.name in windowMethods }.forEach { method ->
                module.hookBefore(method) { chain ->
                    if (isEnabled) {
                        val attrs = chain.args.firstOrNull { it is WindowManager.LayoutParams } as? WindowManager.LayoutParams
                        attrs?.let {
                            if ((it.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0) {
                                it.flags = it.flags and WindowManager.LayoutParams.FLAG_SECURE.inv()
                                Logger.i(TAG, "Active", "Stripped FLAG_SECURE in WMS.${method.name}")
                            }
                        }
                    }
                }
            }

            // Spoofs isSecureLocked to always return false, enabling screenshots of restricted windows
            val wsClass = realClassLoader.loadClass("com.android.server.wm.WindowState")
            wsClass.declaredMethods.filter { it.name == "isSecureLocked" }.forEach { method ->
                module.hook(method).intercept { chain ->
                    val result = chain.proceed()
                    if (isEnabled && result == true) {
                        Logger.d(TAG, "Active", "WindowState.isSecureLocked() forced to false")
                        false
                    } else {
                        result
                    }
                }
            }
            
            isSystemHooked = true
            Logger.i(TAG, "Init", "Successfully initialized ScreenshotHook for [SystemServer]")
            syncSettings(module, realClassLoader)
        }
    }

    // Loads settings from DE storage/RemotePreferences and registers a receiver for real-time updates
    private fun syncSettings(module: XposedModule) {
        syncSettings(module, null)
    }

    private fun syncSettings(module: XposedModule, classLoader: ClassLoader?) {
        val bundle = if (classLoader != null) IpcManager.loadPreferences(module, classLoader) else Bundle()
        val prefs = if (bundle.isEmpty) module.getRemotePreferences(IpcManager.PREF_NAME) else null

        isEnabled = bundle.getBoolean(PreferenceKeys.ENABLE_UNRESTRICTED_SCREENSHOTS, prefs?.getBoolean(PreferenceKeys.ENABLE_UNRESTRICTED_SCREENSHOTS, true) ?: true)
        
        runCatching {
            val ctxClass = Class.forName("android.app.ActivityThread")
            val app = ctxClass.getDeclaredMethod("currentApplication").invoke(null) as? Application
            app?.let {
                IpcManager.registerSecureReceiver(it, module.getModuleApplicationInfo().uid) { intent ->
                    val key = intent.getStringExtra(PreferenceKeys.EXTRA_KEY)
                    if (intent.action == IpcManager.ACTION_SETTINGS_SYNC) {
                        isEnabled = intent.getBooleanExtra(PreferenceKeys.ENABLE_UNRESTRICTED_SCREENSHOTS, true)
                        Logger.i(TAG, "Sync", "Full sync received: enabled=$isEnabled")
                    } else if (key == PreferenceKeys.ENABLE_UNRESTRICTED_SCREENSHOTS) {
                        isEnabled = intent.getBooleanExtra(PreferenceKeys.EXTRA_VALUE, true)
                        Logger.i(TAG, "Sync", "Setting [enable_unrestricted_screenshots] updated to $isEnabled")
                    }
                }
            }
        }
    }
}
