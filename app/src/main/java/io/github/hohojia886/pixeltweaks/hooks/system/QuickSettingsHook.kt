package io.github.hohojia886.pixeltweaks.hooks.system

import android.content.Context
import android.net.wifi.WifiManager
import io.github.hohojia886.pixeltweaks.utils.IpcManager
import io.github.hohojia886.pixeltweaks.utils.Logger
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys
import io.github.hohojia886.pixeltweaks.utils.hookBefore
import io.github.libxposed.api.XposedModule

/**
 * QuickSettingsHook: Enhances Quick Settings tile behaviors.
 * Fixes the WiFi "Pause" behavior by forcing a complete power-off,
 * and bypasses the confirmation dialog when enabling Mobile Data.
 */
object QuickSettingsHook {

    private const val TAG = "QuickSettings"
    
    @Volatile private var isWifiFixEnabled = true // Enable WiFi force-off
    @Volatile private var isDataFixEnabled = true // Enable data confirmation bypass
    private var receiverRegistered = false

    // Entry point: Loads settings and hooks the tile interactors
    fun hook(module: XposedModule, classLoader: ClassLoader) {
        Logger.i(TAG, "Started", "Initializing QuickSettingsHook")
        
        runCatching {
            val prefs = module.getRemotePreferences(io.github.hohojia886.pixeltweaks.utils.IpcManager.PREF_NAME)
            isDataFixEnabled = prefs.getBoolean(PreferenceKeys.ENABLE_QS_DATA_FIX, true)
            isWifiFixEnabled = prefs.getBoolean(PreferenceKeys.ENABLE_QS_WIFI_FIX, true)
            Logger.i(TAG, "Success", "Initial load finished: Data=$isDataFixEnabled, WiFi=$isWifiFixEnabled")
        }.onFailure { e ->
            Logger.e(TAG, "Error", "Failed to load settings via RemotePrefProvider", e)
        }

        runCatching {
            val appClass = classLoader.loadClass("android.app.Application")
            module.hookBefore(appClass.getDeclaredMethod("onCreate")) { chain ->
                val app = chain.thisObject as? Context
                if (app != null) {
                    registerReceiver(app, module.getModuleApplicationInfo().uid)
                }
            }
        }

        // WiFi Fix: Intercepts 'pauseWifi' and executes 'setWifiEnabled(false)' instead
        runCatching {
            val wifiRepoClass = classLoader.loadClass("com.android.systemui.statusbar.pipeline.wifi.data.repository.prod.WifiRepositoryImpl")
            val pauseWifiMethod = wifiRepoClass.getDeclaredMethod("pauseWifi")

            module.hook(pauseWifiMethod).intercept { chain ->
                if (!isWifiFixEnabled) {
                    Logger.i(TAG, "Running", "WiFi Fix Disabled -> Proceeding with factory pauseWifi")
                    return@intercept chain.proceed()
                }

                val instance = chain.thisObject ?: return@intercept chain.proceed()
                try {
                    // Prevent state flicker: cancelOptimisticToggleTimeoutJobs()
                    runCatching {
                        val cancelMethod = instance.javaClass.getDeclaredMethod("cancelOptimisticToggleTimeoutJobs")
                        cancelMethod.isAccessible = true
                        cancelMethod.invoke(instance)
                    }

                    val wifiManagerField = instance.javaClass.getDeclaredField("wifiManager").apply { isAccessible = true }
                    val wifiManager = wifiManagerField.get(instance) as? WifiManager

                    if (wifiManager != null) {
                        Logger.i(TAG, "Success", "WiFi Fix Active -> Forcing setWifiEnabled(false)")
                        @Suppress("DEPRECATION")
                        wifiManager.setWifiEnabled(false)
                        return@intercept null // Skip original pause logic
                    }
                } catch (e: Exception) {
                    Logger.e(TAG, "Error", "WiFi force-off logic failed", e)
                }
                chain.proceed()
            }
        }

        // Mobile Data Fix: Intercepts the handleSecondaryClick lambda to enable data without dialog
        runCatching {
            val targetClass = "com.android.systemui.qs.tiles.impl.cell.domain.interactor.MobileDataTileUserActionInteractor\$handleSecondaryClick$2"
            val lambdaClass = classLoader.loadClass(targetClass)
            val invokeSuspendMethod = lambdaClass.getDeclaredMethod("invokeSuspend", Any::class.java)

            module.hook(invokeSuspendMethod).intercept { chain ->
                if (!isDataFixEnabled) {
                    Logger.i(TAG, "Running", "Data Fix Disabled -> Proceeding with factory confirmation dialog")
                    return@intercept chain.proceed()
                }

                val lambdaInstance = chain.thisObject ?: return@intercept chain.proceed()
                try {
                    val interactorField = lambdaInstance.javaClass.getDeclaredField("this$0").apply { isAccessible = true }
                    val interactor = interactorField.get(lambdaInstance) ?: return@intercept chain.proceed()

                    val repoField = interactor.javaClass.getDeclaredField("mobileConnectionsRepository").apply { isAccessible = true }
                    val repo = repoField.get(interactor) ?: return@intercept chain.proceed()

                    val subIdFlow = repo.javaClass.getMethod("getDefaultDataSubId").invoke(repo)
                    val subId = subIdFlow.javaClass.getMethod("getValue").invoke(subIdFlow) as? Int

                    if (subId != null) {
                        val connectionRepo = repo.javaClass.getMethod("getRepoForSubId", Int::class.javaPrimitiveType).invoke(repo, subId)
                        if (connectionRepo != null) {
                            Logger.i(TAG, "Success", "Data Fix Active -> Bypassing dialog for SubID $subId")
                            connectionRepo.javaClass.getMethod("setDataEnabled", Boolean::class.javaPrimitiveType).invoke(connectionRepo, true)
                            return@intercept Unit // Skip original dialog showing
                        }
                    }
                } catch (e: Exception) {
                    Logger.e(TAG, "Error", "Mobile data bypass logic failed", e)
                }
                chain.proceed()
            }
        }
    }

    // Registers a secure IPC receiver to handle dynamic Quick Settings configuration
    private fun registerReceiver(context: Context, moduleUid: Int) {
        if (receiverRegistered) return
        receiverRegistered = true
        IpcManager.registerSecureReceiver(context, moduleUid) { intent ->
            val action = intent.action
            if (action == IpcManager.ACTION_SETTINGS_SYNC) {
                isDataFixEnabled = intent.getBooleanExtra(PreferenceKeys.ENABLE_QS_DATA_FIX, true)
                isWifiFixEnabled = intent.getBooleanExtra(PreferenceKeys.ENABLE_QS_WIFI_FIX, true)
                Logger.i(TAG, "Success", "Full sync received: Data=$isDataFixEnabled, WiFi=$isWifiFixEnabled")
            } else if (action == IpcManager.ACTION_SETTING_CHANGED) {
                val key = intent.getStringExtra(PreferenceKeys.EXTRA_KEY)
                val value = intent.getBooleanExtra(PreferenceKeys.EXTRA_VALUE, true)
                when (key) {
                    PreferenceKeys.ENABLE_QS_DATA_FIX -> {
                        isDataFixEnabled = value
                        Logger.i(TAG, "Success", "Setting updated: [enable_qs_data_fix] = $isDataFixEnabled")
                    }
                    PreferenceKeys.ENABLE_QS_WIFI_FIX -> {
                        isWifiFixEnabled = value
                        Logger.i(TAG, "Success", "Setting updated: [enable_qs_wifi_fix] = $isWifiFixEnabled")
                    }
                }
            }
        }
    }
}
