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
    @Volatile private var isEnabled = true // Master toggle for Easy Unlock
    @Volatile private var isBypassActive = false // Bypass reboot restriction toggle
    @Volatile private var isFirstUnlockDone = false // Session flag tracking if first unlock completed
    @Volatile private var learnedPinLength = -1 // Learned PIN length (-1 if not yet learned)
    @Volatile private var processPackageName: String? = null

    // Entry point: Syncs settings, registers IPC receiver, and applies LockPatternUtils hooks
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

    // Synchronously loads initial settings from RemotePreferences
    private fun syncSettings(module: XposedModule, classLoader: ClassLoader) {
        runCatching {
            val prefs = module.getRemotePreferences(IpcManager.PREF_NAME)
            isEnabled = prefs.getBoolean(PreferenceKeys.ENABLE_EASY_UNLOCK, true)
            isBypassActive = prefs.getBoolean(PreferenceKeys.ENABLE_EASY_UNLOCK_REBOOT, false)
            val len = prefs.getInt(PreferenceKeys.EXPECTED_PASS_LEN, -1)
            if (len > 0) learnedPinLength = len
        }
    }

    // Processes real-time IPC broadcasts for setting changes
    private fun handleBroadcast(intent: Intent) {
        when (intent.action) {
            IpcManager.ACTION_SETTINGS_SYNC -> {
                isEnabled = intent.getBooleanExtra(PreferenceKeys.ENABLE_EASY_UNLOCK, true)
                isBypassActive = intent.getBooleanExtra(PreferenceKeys.ENABLE_EASY_UNLOCK_REBOOT, false)
                val syncedLen = intent.getIntExtra(PreferenceKeys.EXPECTED_PASS_LEN, -1)
                if (syncedLen > 0) learnedPinLength = syncedLen
            }
            IpcManager.ACTION_SETTING_CHANGED -> {
                val key = intent.getStringExtra(PreferenceKeys.EXTRA_KEY)
                when (key) {
                    PreferenceKeys.ENABLE_EASY_UNLOCK -> isEnabled = intent.getBooleanExtra(PreferenceKeys.EXTRA_VALUE, false)
                    PreferenceKeys.ENABLE_EASY_UNLOCK_REBOOT -> isBypassActive = intent.getBooleanExtra(PreferenceKeys.EXTRA_VALUE, false)
                    PreferenceKeys.EXPECTED_PASS_LEN -> {
                        val len = intent.getIntExtra(PreferenceKeys.EXTRA_VALUE, -1)
                        if (len > 0) learnedPinLength = len
                    }
                }
            }
        }
    }

    // Applies core hijacks to LockPatternUtils and Keyguard password verification
    private fun applyNativeHijack(module: XposedModule, classLoader: ClassLoader) {
        runCatching {
            val lpuClass = classLoader.loadClass("com.android.internal.widget.LockPatternUtils")

            // Forces system to treat Auto PIN Confirm as enabled when PIN length is known
            module.hook(lpuClass.getDeclaredMethod("isAutoPinConfirmEnabled", Int::class.javaPrimitiveType)).intercept { chain ->
                if (isEnabled && learnedPinLength > 0) {
                    if (isBypassActive || isFirstUnlockDone) return@intercept true
                }
                chain.proceed()
            }

            // Spoofs expected PIN length to trigger auto-unlock at the exact learned length
            module.hook(lpuClass.getDeclaredMethod("getPinLength", Int::class.javaPrimitiveType)).intercept { chain ->
                if (isEnabled && learnedPinLength > 0) {
                    if (isBypassActive || isFirstUnlockDone) {
                        Logger.i(TAG, "Active", "Spoofed getPinLength -> $learnedPinLength")
                        return@intercept learnedPinLength
                    }
                }
                chain.proceed()
            }

            // Always returns true for 6-digit PIN checks to enable advanced unlock UI
            lpuClass.declaredMethods.find { it.name == "userHas6DigitPin" }?.let { m ->
                module.hook(m).intercept { if (isEnabled) true else it.proceed() }
            }

            // Learner: Intercepts successful password checks to capture and persist PIN length
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

    // Persists learned PIN length to DE storage via ContentProvider and broadcasts update
    private fun saveLearnedLength(classLoader: ClassLoader, len: Int) {
        runCatching {
            val ctx = IpcManager.getSafeContext(classLoader, processPackageName) ?: return@runCatching
            val uri = Uri.parse("content://io.github.hohojia886.pixeltweaks")
            val bundle = Bundle().apply { putInt(PreferenceKeys.EXPECTED_PASS_LEN, len) }
            ctx.contentResolver.call(uri, "put", null, bundle)
            IpcManager.sendUpdateBroadcast(ctx, PreferenceKeys.EXPECTED_PASS_LEN, len)
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
