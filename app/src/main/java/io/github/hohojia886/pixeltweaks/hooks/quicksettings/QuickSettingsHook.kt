@file:Suppress("DiscouragedPrivateApi", "PrivateApi", "DiscouragedApi", "DEPRECATION", "UsePropertyAccessSyntax")

package io.github.hohojia886.pixeltweaks.hooks.quicksettings

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.net.wifi.WifiManager
import androidx.core.net.toUri
import io.github.hohojia886.pixeltweaks.utils.IpcDispatcher
import io.github.hohojia886.pixeltweaks.utils.IpcManager
import io.github.hohojia886.pixeltweaks.utils.Logger
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys
import io.github.hohojia886.pixeltweaks.utils.isQsDataFixSupported
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field

/**
 * QuickSettingsHook: Enhances Quick Settings tile behaviors.
 * Fixes the WiFi "Pause" behavior by forcing a complete power-off,
 * and bypasses the confirmation dialog when enabling Mobile Data via Pre-Show Zero-Flicker Interception.
 */
object QuickSettingsHook {

    private const val TAG = "QuickSettings"
    
    @Volatile private var isWifiFixEnabled = true // Enable WiFi force-off
    @Volatile private var isDataFixEnabled = true // Enable data confirmation bypass

    // Entry point: Loads settings and hooks tile interactors & dialogs
    fun hook(module: XposedModule, classLoader: ClassLoader) {
        Logger.i(TAG, "Started", "Initializing QuickSettingsHook")
        
        val loadedFromDe = runCatching {
            val ctx = IpcManager.getSafeContext(classLoader, "com.android.systemui") ?: IpcManager.getSystemContext(classLoader) ?: return@runCatching false
            val uri = "content://io.github.hohojia886.pixeltweaks".toUri()
            val bundle = ctx.contentResolver.call(uri, "get", null, null) ?: return@runCatching false
            isDataFixEnabled = bundle.getBoolean(PreferenceKeys.ENABLE_QS_DATA_FIX, true)
            isWifiFixEnabled = bundle.getBoolean(PreferenceKeys.ENABLE_QS_WIFI_FIX, true)
            Logger.i(TAG, "Success", "Initial load from DE (ContentProvider): Data=$isDataFixEnabled, WiFi=$isWifiFixEnabled")
            true
        }.getOrDefault(false)

        if (!loadedFromDe) {
            runCatching {
                val prefs = module.getRemotePreferences(IpcManager.PREF_NAME)
                isDataFixEnabled = prefs.getBoolean(PreferenceKeys.ENABLE_QS_DATA_FIX, true)
                isWifiFixEnabled = prefs.getBoolean(PreferenceKeys.ENABLE_QS_WIFI_FIX, true)
                Logger.i(TAG, "Success", "Initial load from CE (fallback): Data=$isDataFixEnabled, WiFi=$isWifiFixEnabled")
            }.onFailure { e ->
                Logger.e(TAG, "Error", "Failed to load settings via RemotePrefProvider", e)
            }
        }

        if (!isQsDataFixSupported()) {
            isDataFixEnabled = false
        }

        // Subscribe to process-level IPC broadcasts via IpcDispatcher for real-time sync
        IpcDispatcher.addListener { intent ->
            when (intent.action) {
                IpcManager.ACTION_SETTINGS_SYNC -> {
                    isDataFixEnabled = intent.getBooleanExtra(PreferenceKeys.ENABLE_QS_DATA_FIX, true)
                    isWifiFixEnabled = intent.getBooleanExtra(PreferenceKeys.ENABLE_QS_WIFI_FIX, true)
                    if (!isQsDataFixSupported()) isDataFixEnabled = false
                    Logger.i(TAG, "Sync", "Full sync received: Data=$isDataFixEnabled, WiFi=$isWifiFixEnabled")
                }
                IpcManager.ACTION_SETTING_CHANGED -> {
                    when (intent.getStringExtra(PreferenceKeys.EXTRA_KEY)) {
                        PreferenceKeys.ENABLE_QS_DATA_FIX -> {
                            isDataFixEnabled = intent.getBooleanExtra(PreferenceKeys.EXTRA_VALUE, true)
                            if (!isQsDataFixSupported()) isDataFixEnabled = false
                            Logger.i(TAG, "Sync", "Setting [enable_qs_data_fix] updated to $isDataFixEnabled")
                        }
                        PreferenceKeys.ENABLE_QS_WIFI_FIX -> {
                            isWifiFixEnabled = intent.getBooleanExtra(PreferenceKeys.ENABLE_QS_WIFI_FIX, true)
                            Logger.i(TAG, "Sync", "Setting [enable_qs_wifi_fix] updated to $isWifiFixEnabled")
                        }
                    }
                }
            }
        }
        IpcDispatcher.initializeOnce(module, classLoader)

        // WiFi Fix: Intercepts 'pauseWifi' and executes 'setWifiEnabled(false)' instead
        runCatching {
            val wifiRepoClass = classLoader.loadClass("com.android.systemui.statusbar.pipeline.wifi.data.repository.prod.WifiRepositoryImpl")
            wifiRepoClass.declaredMethods.filter { it.name == "pauseWifi" }.forEach { method ->
                module.hook(method).intercept { chain ->
                    if (!isWifiFixEnabled) {
                        Logger.i(TAG, "Running", "WiFi Fix Disabled -> Proceeding with factory pauseWifi")
                        return@intercept chain.proceed()
                    }

                    try {
                        val instance = chain.thisObject
                        val wifiManagerField = wifiManagerFieldCached ?: run {
                            val field = findField(instance.javaClass, "mWifiManager") ?: findField(instance.javaClass, "wifiManager")
                            wifiManagerFieldCached = field
                            field
                        }

                        val wifiManager = wifiManagerField?.get(instance) as? WifiManager

                        if (wifiManager != null) {
                            Logger.i(TAG, "Success", "WiFi Fix Active -> Forcing setWifiEnabled(false)")
                            wifiManager.setWifiEnabled(false)
                            return@intercept null // Skip original pause logic
                        }
                    } catch (e: Exception) {
                        Logger.e(TAG, "Error", "WiFi force-off logic failed", e)
                    }
                    chain.proceed()
                }
            }
        }.onFailure { e ->
            Logger.e(TAG, "Error", "Failed to hook WiFiRepositoryImpl#pauseWifi", e)
        }

        // Mobile Data Fix: Pre-show Zero-Flicker Interception for Mobile Data dialogs
        runCatching {
            val dialogClass = classLoader.loadClass("android.app.Dialog")
            val showMethod = dialogClass.getDeclaredMethod("show")
            module.hook(showMethod).intercept { chain ->
                val dialog = chain.thisObject as? Dialog

                if (isDataFixEnabled && dialog != null) {
                    // Check StackTrace BEFORE chain.proceed() to suppress window attachment entirely
                    val isMobileDataStack = Throwable().stackTrace.any { element ->
                        val cls = element.className
                        cls.contains("MobileData") || cls.contains("Cellular") || cls.contains("MobileConnections") || cls.contains("cell.domain")
                    }

                    if (isMobileDataStack) {
                        Logger.i(TAG, "Success", "Bypassed Mobile Data confirmation dialog PRE-SHOW (Zero-Flicker)")
                        
                        // 1. Force Absolute Window Transparency, Zero Alpha, & Invisible Attributes
                        runCatching {
                            dialog.window?.let { win ->
                                win.setDimAmount(0f)
                                win.setWindowAnimations(0)
                                win.attributes = win.attributes.apply {
                                    alpha = 0f
                                    dimAmount = 0f
                                }
                                win.decorView.visibility = android.view.View.INVISIBLE
                            }
                        }

                        // 2. Try triggering positive listener directly without rendering window
                        var triggered = false
                        runCatching {
                            if (dialog is AlertDialog) {
                                dialog.create() // Ensure AlertController is initialized
                                val alertField = findField(AlertDialog::class.java, "mAlert")
                                val alertController = alertField?.get(dialog)
                                if (alertController != null) {
                                    val listenerField = findField(alertController.javaClass, "mButtonPositiveListener")
                                    val listener = listenerField?.get(alertController) as? DialogInterface.OnClickListener
                                    if (listener != null) {
                                        listener.onClick(dialog, DialogInterface.BUTTON_POSITIVE)
                                        triggered = true
                                    }
                                }
                            }
                        }

                        if (triggered) {
                            return@intercept null // Skip chain.proceed() completely
                        }

                        // 3. Fallback: Proceed with 100% transparent window, then perform instant click & dismiss
                        val res = chain.proceed()
                        runCatching {
                            (dialog as? AlertDialog)?.getButton(DialogInterface.BUTTON_POSITIVE)?.performClick() ?: dialog.dismiss()
                        }
                        return@intercept res
                    }
                }
                
                chain.proceed()
            }
        }.onFailure { e ->
            Logger.e(TAG, "Error", "Failed to hook Dialog#show for Mobile Data fix", e)
        }
    }

    private var wifiManagerFieldCached: Field? = null

    // Helper: Finds a field in class hierarchy
    private fun findField(clazz: Class<*>, name: String): Field? {
        var curr: Class<*>? = clazz
        while (curr != null) {
            try { return curr.getDeclaredField(name).apply { isAccessible = true } }
            catch (_: NoSuchFieldException) { curr = curr.superclass }
        }
        return null
    }
}
