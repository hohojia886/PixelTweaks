package io.github.hohojia886.pixeltweaks.hooks.interaction

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import io.github.hohojia886.pixeltweaks.utils.IpcManager
import io.github.hohojia886.pixeltweaks.utils.Logger
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys
import io.github.hohojia886.pixeltweaks.utils.hookBefore
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * EasyUnlockHook: Enables "Auto PIN Confirm" for any PIN length on Google Pixel keyguard.
 * Hijacks LockPatternUtils to spoof auto-confirm status and expected PIN length.
 * Automatically learns the correct PIN length on password check and persists it to DE storage.
 */
object EasyUnlockHook {

    private const val TAG = "EasyUnlock"
    @Volatile private var isEnabled = true // Enable the overall feature
    @Volatile private var isBypassActive = false // Bypass the reboot restriction
    @Volatile private var isFirstUnlockDone = false // Local session state
    @Volatile private var learnedPinLength = -1 // The PIN length learned from previous successful unlock
    @Volatile private var processPackageName: String? = null

    // Entry point: Syncs settings and hooks the Application lifecycle
    fun hook(module: XposedModule, classLoader: ClassLoader, packageName: String) {
        processPackageName = packageName
        syncSettings(module, classLoader)

        runCatching {
            val appClass = classLoader.loadClass("android.app.Application")
            module.hookBefore(appClass.getDeclaredMethod("onCreate")) { chain ->
                val app = chain.thisObject as? Context
                if (app != null) {
                    IpcManager.registerSecureReceiver(app, module.getModuleApplicationInfo().uid) { intent ->
                        handleBroadcast(intent)
                    }
                }
            }
        }

        applyNativeHijack(module, classLoader)
    }

    // Synchronously loads settings from RemotePreferences to ensure early availability
    private fun syncSettings(module: XposedModule, classLoader: ClassLoader) {
        runCatching {
            val prefs = module.getRemotePreferences(IpcManager.PREF_NAME)
            isEnabled = prefs.getBoolean(PreferenceKeys.ENABLE_EASY_UNLOCK, true)
            isBypassActive = prefs.getBoolean(PreferenceKeys.ENABLE_EASY_UNLOCK_REBOOT, false)
            learnedPinLength = prefs.getInt(PreferenceKeys.EXPECTED_PASS_LEN, -1)
            Logger.i(TAG, "Sync", "Settings loaded from DE: enabled=$isEnabled, bypass=$isBypassActive, len=$learnedPinLength")
        }
        isFirstUnlockDone = false
    }

    // Processes incoming IPC broadcasts to update feature states in real-time
    private fun handleBroadcast(intent: Intent) {
        when (intent.action) {
            IpcManager.ACTION_SETTINGS_SYNC -> {
                isEnabled = intent.getBooleanExtra(PreferenceKeys.ENABLE_EASY_UNLOCK, true)
                isBypassActive = intent.getBooleanExtra(PreferenceKeys.ENABLE_EASY_UNLOCK_REBOOT, false)
                val syncedLen = intent.getIntExtra(PreferenceKeys.EXPECTED_PASS_LEN, -1)
                if (syncedLen > 0) learnedPinLength = syncedLen
                Logger.i(TAG, "Sync", "Full sync received: enabled=$isEnabled, bypass=$isBypassActive, len=$learnedPinLength")
            }
            IpcManager.ACTION_SETTING_CHANGED -> {
                val key = intent.getStringExtra(PreferenceKeys.EXTRA_KEY)
                val value = intent.getBooleanExtra(PreferenceKeys.EXTRA_VALUE, false)
                when (key) {
                    PreferenceKeys.ENABLE_EASY_UNLOCK -> {
                        isEnabled = value
                        Logger.i(TAG, "Sync", "Setting [enable_easy_unlock] updated to $isEnabled")
                    }
                    PreferenceKeys.ENABLE_EASY_UNLOCK_REBOOT -> {
                        isBypassActive = value
                        Logger.i(TAG, "Sync", "Setting [enable_easy_unlock_reboot] updated to $isBypassActive")
                    }
                    PreferenceKeys.EXPECTED_PASS_LEN -> {
                        learnedPinLength = intent.getIntExtra(PreferenceKeys.EXTRA_VALUE, -1)
                        Logger.i(TAG, "Sync", "Setting [expected_pass_len] updated to $learnedPinLength")
                    }
                }
            }
        }
    }

    // Applies core hijacks to the system's lockscreen policy and password checking logic
    private fun applyNativeHijack(module: XposedModule, classLoader: ClassLoader) {
        runCatching {
            val lpuClass = classLoader.loadClass("com.android.internal.widget.LockPatternUtils")

            // Forces the system to believe "Auto PIN Confirm" is enabled if we have a learned length
            module.hook(lpuClass.getDeclaredMethod("isAutoPinConfirmEnabled", Int::class.javaPrimitiveType)).intercept { chain ->
                if (isEnabled && learnedPinLength > 0) {
                    if (isBypassActive || isFirstUnlockDone) return@intercept true
                }
                chain.proceed()
            }

            // Spoofs the expected PIN length to the system to trigger auto-unlock at the right moment
            module.hook(lpuClass.getDeclaredMethod("getPinLength", Int::class.javaPrimitiveType)).intercept { chain ->
                if (isEnabled && learnedPinLength > 0) {
                    if (isBypassActive || isFirstUnlockDone) {
                        Logger.i(TAG, "Active", "Spoofed getPinLength -> $learnedPinLength")
                        return@intercept learnedPinLength
                    }
                }
                chain.proceed()
            }

            // Always returns true for 6-digit PIN checks to enable advanced unlock UI features
            lpuClass.declaredMethods.find { it.name == "userHas6DigitPin" }?.let { m ->
                module.hook(m).intercept { if (isEnabled) true else it.proceed() }
            }

            // Learner: Intercepts successful password checks to capture and save the current PIN length
            val securityCtrlClass = classLoader.loadClass("com.android.keyguard.KeyguardAbsKeyInputViewController")
            securityCtrlClass.declaredMethods.find { it.name == "onPasswordChecked" }?.let { m ->
                module.hookBefore(m) { chain ->
                    val successful = chain.args.getOrNull(1) as? Boolean ?: false
                    if (isEnabled && successful) {
                        if (!isFirstUnlockDone) {
                            isFirstUnlockDone = true
                            Logger.i(TAG, "Success", "Session first unlock done")
                        }
                        
                        runCatching {
                            val instance = chain.thisObject
                            val passwordEntry = findField(instance.javaClass, "mPasswordEntry")?.get(instance) ?: return@runCatching
                            val text = findMethod(passwordEntry.javaClass, "getText")?.invoke(passwordEntry) ?: 
                                       findField(passwordEntry.javaClass, "mText")?.get(passwordEntry)
                            
                            val length = if (text is CharSequence) text.length else 0
                            if (length > 0 && length != learnedPinLength) {
                                learnedPinLength = length
                                Logger.i(TAG, "Success", "Learned PIN length: $learnedPinLength")
                                saveLearnedLength(classLoader, learnedPinLength)
                            }
                        }
                    }
                }
            }
        }.onFailure { e ->
            Logger.e(TAG, "Error", "Failed to apply native hijacks", e)
        }
    }

    // Persists the learned PIN length to the device-protected storage via ContentProvider
    private fun saveLearnedLength(classLoader: ClassLoader, len: Int) {
        runCatching {
            val ctx = IpcManager.getSafeContext(classLoader, processPackageName) ?: return@runCatching
            val uri = Uri.parse("content://io.github.hohojia886.pixeltweaks")
            val bundle = Bundle().apply { putInt(PreferenceKeys.EXPECTED_PASS_LEN, len) }
            ctx.contentResolver.call(uri, "put", null, bundle)
        }
    }

    // Reflection Helper: Finds a method in the class hierarchy
    private fun findMethod(clazz: Class<*>, name: String): Method? {
        var curr: Class<*>? = clazz
        while (curr != null) {
            try { return curr.getDeclaredMethod(name).apply { isAccessible = true } } 
            catch (e: NoSuchMethodException) { curr = curr.superclass }
        }
        return null
    }

    // Reflection Helper: Finds a field in the class hierarchy
    private fun findField(clazz: Class<*>, name: String): Field? {
        var curr: Class<*>? = clazz
        while (curr != null) {
            try { return curr.getDeclaredField(name).apply { isAccessible = true } } 
            catch (e: NoSuchFieldException) { curr = curr.superclass }
        }
        return null
    }
}
