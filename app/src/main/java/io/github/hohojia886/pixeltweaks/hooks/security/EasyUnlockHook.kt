@file:Suppress("DiscouragedPrivateApi", "PrivateApi", "DiscouragedApi")

package io.github.hohojia886.pixeltweaks.hooks.security

import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import io.github.hohojia886.pixeltweaks.utils.IpcDispatcher
import io.github.hohojia886.pixeltweaks.utils.IpcManager
import io.github.hohojia886.pixeltweaks.utils.Logger
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys
import io.github.hohojia886.pixeltweaks.utils.hookBefore
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Enables Auto PIN Confirm for any PIN length on Keyguard without requiring the user to press Enter.
 *
 * Intercepts LockPatternUtils and automatically learns the user's PIN length
 * upon the first successful unlock attempt in a session.
 */
object EasyUnlockHook {

    private const val TAG = "EasyUnlock"
    @Volatile private var isEnabled = true
    @Volatile private var isBypassActive = false
    @Volatile private var isFirstUnlockDone = false
    @Volatile private var learnedPinLength = -1
    @Volatile private var processPackageName: String? = null

    /**
     * Initializes EasyUnlock hooks inside SystemUI keyguard.
     *
     * @param module Active LSPosed [XposedModule] instance.
     * @param classLoader SystemUI ClassLoader.
     * @param packageName Target process package name.
     */
    fun hook(module: XposedModule, classLoader: ClassLoader, packageName: String) {
        processPackageName = packageName
        syncSettings(module, classLoader)

        IpcDispatcher.addListener { intent -> handleBroadcast(intent) }
        IpcDispatcher.initializeOnce(module, classLoader)

        applyNativeHijack(module, classLoader)
    }

    /**
     * Loads DE/CE settings synchronously prioritizing Direct Boot.
     * Queries ContentProvider or RemotePreferences for auto confirm settings.
     */
    private fun syncSettings(module: XposedModule, classLoader: ClassLoader) {
        val loadedFromDe = runCatching {
            val ctx = IpcManager.getSafeContext(classLoader, processPackageName) ?: IpcManager.getSystemContext(classLoader) ?: return@runCatching false
            val uri = "content://io.github.hohojia886.pixeltweaks".toUri()
            val bundle = ctx.contentResolver.call(uri, "get", null, null) ?: return@runCatching false
            isEnabled = bundle.getBoolean(PreferenceKeys.ENABLE_EASY_UNLOCK, true)
            isBypassActive = bundle.getBoolean(PreferenceKeys.ENABLE_EASY_UNLOCK_REBOOT, false)
            val len = bundle.getInt(PreferenceKeys.EXPECTED_PASS_LEN, -1)
            if (len > 0) learnedPinLength = len
            Logger.i(TAG, "Sync", "Settings loaded from DE (ContentProvider): enabled=$isEnabled, bypass=$isBypassActive, len=$learnedPinLength")
            true
        }.getOrDefault(false)

        if (!loadedFromDe) {
            runCatching {
                val prefs = module.getRemotePreferences(IpcManager.PREF_NAME)
                isEnabled = prefs.getBoolean(PreferenceKeys.ENABLE_EASY_UNLOCK, true)
                isBypassActive = prefs.getBoolean(PreferenceKeys.ENABLE_EASY_UNLOCK_REBOOT, false)
                val len = prefs.getInt(PreferenceKeys.EXPECTED_PASS_LEN, -1)
                if (len > 0) learnedPinLength = len
                Logger.i(TAG, "Sync", "Settings loaded from CE (fallback): enabled=$isEnabled, bypass=$isBypassActive, len=$learnedPinLength")
            }.onFailure { e ->
                Logger.e(TAG, "Error", "Both DE and CE settings load failed", e)
            }
        }
    }

    /**
     * Re-evaluates settings upon receiving IPC broadcast intents.
     * Updates enabled, bypass active, and learned PIN length properties.
     */
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

    /**
     * Implements native hijacks bypassing PIN length and confirm checks.
     * Spoofs isAutoPinConfirmEnabled and getPinLength in LockPatternUtils.
     */
    private fun applyNativeHijack(module: XposedModule, classLoader: ClassLoader) {
        runCatching {
            val lpuClass = classLoader.loadClass("com.android.internal.widget.LockPatternUtils")

            module.hook(lpuClass.getDeclaredMethod("isAutoPinConfirmEnabled", Int::class.javaPrimitiveType)).intercept { chain ->
                if (isEnabled && learnedPinLength > 0) {
                    if (isBypassActive || isFirstUnlockDone) return@intercept true
                }
                chain.proceed()
            }

            module.hook(lpuClass.getDeclaredMethod("getPinLength", Int::class.javaPrimitiveType)).intercept { chain ->
                if (isEnabled && learnedPinLength > 0) {
                    if (isBypassActive || isFirstUnlockDone) {
                        Logger.i(TAG, "Active", "Spoofed getPinLength -> $learnedPinLength")
                        return@intercept learnedPinLength
                    }
                }
                chain.proceed()
            }

            lpuClass.declaredMethods.find { it.name == "userHas6DigitPin" }?.let { m ->
                module.hook(m).intercept { if (isEnabled) true else it.proceed() }
            }

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
                            val text = findMethod(passwordEntry.javaClass)?.invoke(passwordEntry) ?: 
                                       findField(passwordEntry.javaClass, "mText")?.get(passwordEntry)
                            
                            val length = (text as? CharSequence)?.length ?: 0
                            if (length > 0 && length != learnedPinLength) {
                                learnedPinLength = length
                                Logger.i(TAG, "Success", "Learned PIN length: $learnedPinLength")
                                saveLearnedLength(classLoader, learnedPinLength)
                            }
                        }.onFailure { e ->
                            Logger.e(TAG, "Learner", "Failed to learn PIN length during onPasswordChecked", e)
                        }
                    }
                }
            }
        }.onFailure { e ->
            Logger.e(TAG, "Error", "Failed to apply native hijacks", e)
        }
    }

    /**
     * Saves auto-learned PIN length securely to DE storage via ContentProvider.
     * Dispatches update broadcast to sync learned length across processes.
     */
    private fun saveLearnedLength(classLoader: ClassLoader, len: Int) {
        runCatching {
            val ctx = IpcManager.getSafeContext(classLoader, processPackageName) ?: return@runCatching
            val uri = "content://io.github.hohojia886.pixeltweaks".toUri()
            val bundle = Bundle().apply { putInt(PreferenceKeys.EXPECTED_PASS_LEN, len) }
            ctx.contentResolver.call(uri, "put", null, bundle)
            IpcManager.sendUpdateBroadcast(ctx, PreferenceKeys.EXPECTED_PASS_LEN, len)
        }.onFailure { e ->
            Logger.e(TAG, "Save", "Failed to persist learned PIN length", e)
        }
    }

    /**
     * Helper to find methods safely within class hierarchy via reflection.
     * Traverses superclasses searching for getText declared method.
     */
    private fun findMethod(clazz: Class<*>): Method? {
        var curr: Class<*>? = clazz
        while (curr != null) {
            try { return curr.getDeclaredMethod("getText").apply { isAccessible = true } } 
            catch (_: NoSuchMethodException) { curr = curr.superclass }
        }
        return null
    }

    /**
     * Helper to find fields safely within class hierarchy via reflection.
     * Traverses superclasses returning Accessible field instance.
     */
    private fun findField(clazz: Class<*>, name: String): Field? {
        var curr: Class<*>? = clazz
        while (curr != null) {
            try { return curr.getDeclaredField(name).apply { isAccessible = true } } 
            catch (_: NoSuchFieldException) { curr = curr.superclass }
        }
        return null
    }
}
