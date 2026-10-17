@file:Suppress("DiscouragedPrivateApi", "PrivateApi", "DiscouragedApi")

package io.github.hohojia886.pixeltweaks.hooks.security

import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import io.github.hohojia886.pixeltweaks.utils.IpcDispatcher
import io.github.hohojia886.pixeltweaks.utils.IpcManager
import io.github.hohojia886.pixeltweaks.utils.Logger
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys
import io.github.hohojia886.pixeltweaks.utils.hookBefore
import io.github.libxposed.api.XposedModule

/**
 * Bypasses `FLAG_SECURE` system-wide to enable unrestricted screenshots and screen recordings in restricted apps.
 *
 * Hooks `SurfaceControl.Builder.setSecure()` at app level, and `WindowManagerService` and `WindowState` at system level.
 */
object ScreenshotHook {

    private const val TAG = "Screenshot"
    @Volatile private var isEnabled = false
    @Volatile private var isSystemHooked = false

    /**
     * Intercepts application-level surface creation to suppress secure flags.
     *
     * @param module Active LSPosed [XposedModule] instance.
     * @param classLoader Application process ClassLoader.
     */
    fun hookApp(module: XposedModule, classLoader: ClassLoader) {
        syncSettings(module, classLoader)
        runCatching {
            val builderClass = classLoader.loadClass("android.view.SurfaceControl" + '$' + "Builder")
            module.hook(builderClass.getDeclaredMethod("setSecure", Boolean::class.java)).intercept { chain ->
                val requestedSecure = chain.args.getOrNull(0) as? Boolean ?: false
                if (isEnabled && requestedSecure) {
                    Logger.d(TAG, "Action", "Forced SurfaceControl.setSecure(false)")
                    chain.proceed(arrayOf(false))
                } else {
                    chain.proceed()
                }
            }
        }.onFailure { e ->
            Logger.e(TAG, "Error", "Failed to hook SurfaceControl.Builder", e)
        }
    }

    /**
     * Injects system UI settings specific to screenshots.
     * Synchronizes module preferences for SystemUI process instance.
     */
    fun hookSystemUI(module: XposedModule, classLoader: ClassLoader) {
        Logger.i(TAG, "Init", "Initializing ScreenshotHook for [SystemUI]")
        syncSettings(module, classLoader)
    }

    /**
     * Removes FLAG_SECURE layer properties within WindowManager.
     * Hooks WindowManagerService and WindowState to strip secure flags.
     */
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
        }.onFailure { e ->
            Logger.e(TAG, "Error", "Failed to apply WindowManagerService hooks", e)
        }
    }

    /**
     * Receives settings adjustments synchronously and via broadcast.
     * Updates unrestricted screenshots enablement state property.
     */
    private fun syncSettings(module: XposedModule, classLoader: ClassLoader?) {
        val bundle = if (classLoader != null) IpcManager.loadPreferences(module, classLoader) else Bundle()
        val prefs = if (bundle.isEmpty) module.getRemotePreferences(IpcManager.PREF_NAME) else null

        isEnabled = bundle.getBoolean(PreferenceKeys.ENABLE_UNRESTRICTED_SCREENSHOTS, prefs?.getBoolean(PreferenceKeys.ENABLE_UNRESTRICTED_SCREENSHOTS, true) ?: true)

        IpcDispatcher.addListener { intent ->
            val key = intent.getStringExtra(PreferenceKeys.EXTRA_KEY)
            if (intent.action == IpcManager.ACTION_SETTINGS_SYNC) {
                isEnabled = intent.getBooleanExtra(PreferenceKeys.ENABLE_UNRESTRICTED_SCREENSHOTS, true)
                Logger.i(TAG, "Sync", "Full sync received: enabled=$isEnabled")
            } else if (key == PreferenceKeys.ENABLE_UNRESTRICTED_SCREENSHOTS) {
                isEnabled = intent.getBooleanExtra(PreferenceKeys.EXTRA_VALUE, true)
                Logger.i(TAG, "Sync", "Setting [enable_unrestricted_screenshots] updated to $isEnabled")
            }
        }
        if (classLoader != null) {
            IpcDispatcher.initializeOnce(module, classLoader)
        }
    }
}
