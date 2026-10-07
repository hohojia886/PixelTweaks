@file:Suppress("DiscouragedPrivateApi", "PrivateApi", "DiscouragedApi", "DEPRECATION", "UsePropertyAccessSyntax")

package io.github.hohojia886.pixeltweaks.hooks.quicksettings

import android.net.wifi.WifiManager
import androidx.core.net.toUri
import io.github.hohojia886.pixeltweaks.utils.IpcManager
import io.github.hohojia886.pixeltweaks.utils.Logger
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * QuickSettingsHook: Enhances Quick Settings tile behaviors.
 * Fixes the WiFi "Pause" behavior by forcing a complete power-off,
 * and bypasses the confirmation dialog when enabling Mobile Data.
 */
object QuickSettingsHook {

    private const val TAG = "QuickSettings"
    
    @Volatile private var isWifiFixEnabled = true // Enable WiFi force-off
    @Volatile private var isDataFixEnabled = true // Enable data confirmation bypass

    // Entry point: Loads settings and hooks the tile interactors
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
        }

        // Mobile Data Fix: Intercepts the handleSecondaryClick lambda to enable data without dialog
        runCatching {
            val targetClass = "com.android.systemui.qs.tiles.impl.cell.domain.interactor.MobileDataTileUserActionInteractor" + '$' + "handleSecondaryClick" + '$' + "2"
            val lambdaClass = classLoader.loadClass(targetClass)
            val invokeSuspendMethod = lambdaClass.getDeclaredMethod("invokeSuspend", Any::class.java)

            module.hook(invokeSuspendMethod).intercept { chain ->
                if (!isDataFixEnabled) {
                    Logger.i(TAG, "Running", "Data Fix Disabled -> Proceeding with factory confirmation dialog")
                    return@intercept chain.proceed()
                }

                try {
                    val lambdaInstance = chain.thisObject
                    val interactorField = interactorFieldCached ?: run {
                        val field = findField(lambdaInstance.javaClass, "this" + '$' + "0") ?: findField(lambdaInstance.javaClass, "val" + '$' + "interactor")
                        interactorFieldCached = field
                        field
                    }

                    val interactor = interactorField?.get(lambdaInstance) ?: return@intercept chain.proceed()
                    
                    // Invoke confirm(false) or setDataEnabled(true) directly on interactor
                    val confirmMethod = findMethod(interactor.javaClass, "confirm", Boolean::class.java) 
                        ?: findMethod(interactor.javaClass, "setDataEnabled", Boolean::class.java)

                    if (confirmMethod != null) {
                        Logger.i(TAG, "Success", "Data Fix Active -> Bypassing dialog via method: ${confirmMethod.name}")
                        confirmMethod.invoke(interactor, false)
                        return@intercept Unit // Skip original dialog showing
                    }

                    // Fallback: Query SubID and set data enabled directly via connection repository
                    val subIdField = findField(interactor.javaClass, "subId") ?: findField(interactor.javaClass, '$' + "subId")
                    val subId = subIdField?.get(interactor) as? Int
                    val repoField = findField(interactor.javaClass, "mobileDataRepository") ?: findField(interactor.javaClass, "repository")
                    val repo = repoField?.get(interactor)

                    if (subId != null && repo != null) {
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

    private var wifiManagerFieldCached: Field? = null
    private var interactorFieldCached: Field? = null

    // Helper: Finds a field in class hierarchy
    private fun findField(clazz: Class<*>, name: String): Field? {
        var curr: Class<*>? = clazz
        while (curr != null) {
            try { return curr.getDeclaredField(name).apply { isAccessible = true } }
            catch (_: NoSuchFieldException) { curr = curr.superclass }
        }
        return null
    }

    // Helper: Finds a method in class hierarchy
    private fun findMethod(clazz: Class<*>, name: String, vararg parameterTypes: Class<*>): Method? {
        var curr: Class<*>? = clazz
        while (curr != null) {
            try { return curr.getDeclaredMethod(name, *parameterTypes).apply { isAccessible = true } }
            catch (_: NoSuchMethodException) { curr = curr.superclass }
        }
        return null
    }
}
