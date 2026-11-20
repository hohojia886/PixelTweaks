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
import java.util.concurrent.ConcurrentHashMap

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
    private val hookedCallbackClasses = ConcurrentHashMap.newKeySet<Class<*>>()

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

    @Volatile private var pendingCredentialLength = -1
    @Volatile private var lastEnteredPinLength = -1

    /**
     * Implements native hijacks bypassing PIN length and confirm checks.
     * Spoofs isAutoPinConfirmEnabled and getPinLength in LockPatternUtils.
     */
    private fun applyNativeHijack(module: XposedModule, classLoader: ClassLoader) {
        runCatching {
            val lpuClass = classLoader.loadClass("com.android.internal.widget.LockPatternUtils")

            lpuClass.declaredMethods.filter { 
                it.name == "isAutoPinConfirmEnabled" || it.name == "isAutoPinConfirmSetInSettings" 
            }.forEach { m ->
                module.hook(m).intercept { chain ->
                    if (isEnabled && learnedPinLength > 0) {
                        if (isBypassActive || isFirstUnlockDone) return@intercept true
                    }
                    chain.proceed()
                }
            }

            lpuClass.declaredMethods.filter { it.name == "getPinLength" }.forEach { m ->
                module.hook(m).intercept { chain ->
                    if (isEnabled && learnedPinLength > 0) {
                        if (isBypassActive || isFirstUnlockDone) {
                            Logger.i(TAG, "Active", "Spoofed getPinLength -> $learnedPinLength")
                            return@intercept learnedPinLength
                        }
                    }
                    chain.proceed()
                }
            }

            lpuClass.declaredMethods.filter { it.name == "userHas6DigitPin" }.forEach { m ->
                module.hook(m).intercept { chain ->
                    if (isEnabled && learnedPinLength == 6) true else chain.proceed()
                }
            }

            // Global credential length capture and success verification on LockPatternUtils
            lpuClass.declaredMethods.filter { 
                it.name == "checkCredential" || it.name == "verifyCredential" || it.name == "checkPassword" 
            }.forEach { m ->
                module.hook(m).intercept { chain ->
                    val cred = chain.args.find { arg ->
                        arg != null && (arg is CharSequence || arg is ByteArray || arg.javaClass.simpleName.contains("Credential"))
                    }
                    val size = getCredentialSize(cred)
                    if (size > 0) {
                        pendingCredentialLength = size
                        Logger.d(TAG, "Active", "Intercepted credential attempt, size=$size")
                    }

                    val callbackArg = chain.args.find { arg ->
                        arg != null && (arg.javaClass.simpleName.contains("Callback") || arg.javaClass.interfaces.any { it.simpleName.contains("Callback") })
                    }
                    
                    if (callbackArg != null && hookedCallbackClasses.add(callbackArg.javaClass)) {
                        runCatching {
                            callbackArg.javaClass.declaredMethods.filter { 
                                it.name == "onChecked" || it.name == "onEarlyMatched" || it.name == "onCredentialVerified" 
                            }.forEach { cbMethod ->
                                val boolIndex = cbMethod.parameterTypes.indexOfFirst { it == Boolean::class.javaPrimitiveType }
                                module.hookBefore(cbMethod) { cbChain ->
                                    val matched = if (cbMethod.name == "onEarlyMatched") true 
                                                  else if (boolIndex >= 0) cbChain.args[boolIndex] as? Boolean == true 
                                                  else false
                                    if (matched && pendingCredentialLength > 0) {
                                        commitLearnedLength(classLoader, pendingCredentialLength, "LockPatternUtils Callback")
                                    }
                                }
                            }
                        }.onFailure { e ->
                            Logger.e(TAG, "Error", "Failed to hook callback on ${callbackArg.javaClass.name}", e)
                        }
                    }

                    val result = chain.proceed()

                    if (result != null && pendingCredentialLength > 0) {
                        val isSuccess = runCatching {
                            val resCode = findMethod(result.javaClass, "getResponseCode")?.invoke(result) as? Int
                            val matched = findMethod(result.javaClass, "isMatched")?.invoke(result) as? Boolean
                            isCredentialCheckSuccess(result, resCode, matched)
                        }.getOrDefault(false)

                        if (isSuccess) {
                            commitLearnedLength(classLoader, pendingCredentialLength, "LockPatternUtils Return")
                        }
                    }

                    result
                }
            }

            // Keyguard Controller Candidates
            val candidateClassNames = listOf(
                "com.android.keyguard.KeyguardAbsKeyInputViewController",
                "com.android.keyguard.KeyguardPinBasedInputViewController",
                "com.android.keyguard.KeyguardPinViewController",
                "com.android.keyguard.KeyguardPasswordViewController",
                "com.android.keyguard.KeyguardAbsKeyInputView",
                "com.android.keyguard.KeyguardPinBasedInputView"
            )

            val candidateFieldNames = listOf(
                "mPasswordEntry", "mPinEntry", "mEntryView", "mPasswordTextView", "passwordEntry", "mText"
            )

            candidateClassNames.forEach { className ->
                runCatching {
                    val clazz = classLoader.loadClass(className)
                    
                    // Capture entered PIN length BEFORE mPasswordEntry is cleared by verifyPasswordAndUnlock
                    clazz.declaredMethods.filter { 
                        it.name == "verifyPasswordAndUnlock" || it.name == "onPasswordChecked" || it.name == "resetPasswordText"
                    }.forEach { m ->
                        module.hookBefore(m) { chain ->
                            val instance = chain.thisObject ?: return@hookBefore
                            for (fieldName in candidateFieldNames) {
                                val entryObj = findField(instance.javaClass, fieldName)?.get(instance) ?: continue
                                val text = findMethod(entryObj.javaClass, "getText")?.invoke(entryObj)
                                    ?: findMethod(entryObj.javaClass, "getTransformedText")?.invoke(entryObj)
                                    ?: findField(entryObj.javaClass, "mText")?.get(entryObj)
                                
                                val l = (text as? CharSequence)?.length ?: 0
                                if (l > 0) {
                                    lastEnteredPinLength = l
                                    Logger.d(TAG, "Active", "Captured entered PIN length before clear: $l (from $className)")
                                    break
                                }
                            }
                        }
                    }

                    // Handle password check success and commit learned length
                    clazz.declaredMethods.filter { 
                        it.name == "onPasswordChecked"
                    }.forEach { m ->
                        val boolIndex = m.parameterTypes.indexOfFirst { it == Boolean::class.javaPrimitiveType }
                        if (boolIndex >= 0) {
                            module.hookBefore(m) { chain ->
                                val successful = chain.args[boolIndex] as? Boolean == true
                                if (isEnabled && successful) {
                                    val lengthToSave = if (lastEnteredPinLength > 0) lastEnteredPinLength else pendingCredentialLength
                                    if (lengthToSave > 0) {
                                        commitLearnedLength(classLoader, lengthToSave, className)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }.onFailure { e ->
            Logger.e(TAG, "Error", "Failed to apply native hijacks", e)
        }
    }

    /**
     * Commits auto-learned PIN length securely and syncs across processes.
     */
    private fun commitLearnedLength(classLoader: ClassLoader, length: Int, source: String) {
        if (length in 4..16 && length != learnedPinLength) {
            learnedPinLength = length
            isFirstUnlockDone = true
            Logger.i(TAG, "Success", "Learned PIN length: $learnedPinLength (from $source)")
            saveLearnedLength(classLoader, learnedPinLength)
        } else if (length in 4..16) {
            isFirstUnlockDone = true
        }
    }

    /**
     * Extracts length from credential objects or payloads safely.
     */
    private fun getCredentialSize(cred: Any?): Int {
        if (cred == null) return 0
        if (cred is CharSequence) return cred.length
        if (cred is ByteArray) return cred.size
        return runCatching {
            val sizeMethod = cred.javaClass.getDeclaredMethod("size").apply { isAccessible = true }
            sizeMethod.invoke(cred) as? Int ?: 0
        }.getOrElse {
            runCatching {
                val sizeField = cred.javaClass.getDeclaredField("mSize").apply { isAccessible = true }
                sizeField.get(cred) as? Int ?: 0
            }.getOrDefault(0)
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
     * Traverses superclasses searching for declared method by name.
     */
    private fun findMethod(clazz: Class<*>, name: String = "getText"): Method? {
        var curr: Class<*>? = clazz
        while (curr != null) {
            try { return curr.getDeclaredMethod(name).apply { isAccessible = true } } 
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

    /**
     * Evaluates whether a credential check returned a successful verification result.
     * Explicitly recognizes Boolean `true`, response code `0` (RESPONSE_OK), or `isMatched() == true`.
     * Does NOT treat `timeout == 0` as success, preventing false-positive PIN learning when an incorrect PIN is entered.
     *
     * @param result The raw result object returned by `LockPatternUtils.checkCredential`.
     * @param responseCode Optional integer response code retrieved via reflection (`getResponseCode`).
     * @param isMatched Optional boolean match indicator retrieved via reflection (`isMatched`).
     * @return True if the credential verification was definitively successful, false otherwise.
     */
    internal fun isCredentialCheckSuccess(result: Any?, responseCode: Int?, isMatched: Boolean?): Boolean {
        if (result is Boolean) return result
        if (responseCode == 0) return true
        if (isMatched == true) return true
        return false
    }
}
