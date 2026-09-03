package io.github.hohojia886.pixeltweaks.utils

import android.content.Intent
import android.util.Log
import io.github.libxposed.api.XposedModule

/**
 * Logger: Standardized logging utility for the PixelTweaks module.
 * Features centralized toggles for each functional area, auto-prefixing with "PXTK_",
 * and a Master switch that controls all non-critical output across processes.
 */
object Logger {

    interface Logger {
        fun v(tag: String, msg: String)
        fun d(tag: String, msg: String)
        fun i(tag: String, msg: String)
        fun w(tag: String, msg: String)
        fun e(tag: String, msg: String, tr: Throwable?)
    }

    private object AndroidLogger : Logger {
        override fun v(tag: String, msg: String) { Log.v(tag, msg) }
        override fun d(tag: String, msg: String) { Log.d(tag, msg) }
        override fun i(tag: String, msg: String) { Log.i(tag, msg) }
        override fun w(tag: String, msg: String) { Log.w(tag, msg) }
        override fun e(tag: String, msg: String, tr: Throwable?) { Log.e(tag, msg, tr) }
    }

    @Volatile var logger: Logger = AndroidLogger

    @Volatile var isMasterEnabled = false // Master toggle for all logs
    @Volatile var logCallRec = true // Call Recording specific logs
    @Volatile var logCallNotes = true // Call Notes specific logs
    @Volatile var logClearAll = true // Clear All button specific logs
    @Volatile var logTraffic = true // Network Traffic specific logs
    @Volatile var logQS = true // Quick Settings specific logs
    @Volatile var logPM = true // PackageManager security logs
    @Volatile var logScreenshot = true // Screenshot bypass logs
    @Volatile var logDT2S = true // Double Tap to Sleep logs
    @Volatile var logEasyUnlock = true // Easy Unlock logs

    // Initializes logging state from RemotePreferences during process attachment
    fun sync(module: XposedModule) {
        runCatching {
            val prefs = module.getRemotePreferences(io.github.hohojia886.pixeltweaks.utils.IpcManager.PREF_NAME)
            isMasterEnabled = prefs.getBoolean(PreferenceKeys.ENABLE_MASTER_LOG, false)
            logCallRec = prefs.getBoolean(PreferenceKeys.LOG_CALL_RECORDING, true)
            logCallNotes = prefs.getBoolean(PreferenceKeys.LOG_CALL_NOTES, true)
            logClearAll = prefs.getBoolean(PreferenceKeys.LOG_CLEAR_ALL, true)
            logTraffic = prefs.getBoolean(PreferenceKeys.LOG_NETWORK_TRAFFIC, true)
            logQS = prefs.getBoolean(PreferenceKeys.LOG_QUICK_SETTINGS, true)
            logPM = prefs.getBoolean(PreferenceKeys.LOG_SECURITY_BYPASSES, true)
            logScreenshot = prefs.getBoolean(PreferenceKeys.LOG_UNRESTRICTED_SCREENSHOTS, true)
            logDT2S = prefs.getBoolean(PreferenceKeys.LOG_DT2S, true)
            logEasyUnlock = prefs.getBoolean(PreferenceKeys.LOG_EASY_UNLOCK, true)
            
            logger.i("PXTK_Hook", "[Logger] Settings synced. Master=$isMasterEnabled (PID: ${android.os.Process.myPid()})")
        }
    }

    // Handles real-time log toggle updates via IPC broadcasts
    fun handleBroadcast(intent: Intent) {
        val action = intent.action ?: return
        
        var isChanged = false
        var targetKey = ""
        var targetValue = false

        if (action == "io.github.hohojia886.pixeltweaks.SETTINGS_SYNC") {
            isMasterEnabled = intent.getBooleanExtra(PreferenceKeys.ENABLE_MASTER_LOG, false)
            logCallRec = intent.getBooleanExtra(PreferenceKeys.LOG_CALL_RECORDING, true)
            logCallNotes = intent.getBooleanExtra(PreferenceKeys.LOG_CALL_NOTES, true)
            logClearAll = intent.getBooleanExtra(PreferenceKeys.LOG_CLEAR_ALL, true)
            logTraffic = intent.getBooleanExtra(PreferenceKeys.LOG_NETWORK_TRAFFIC, true)
            logQS = intent.getBooleanExtra(PreferenceKeys.LOG_QUICK_SETTINGS, true)
            logPM = intent.getBooleanExtra(PreferenceKeys.LOG_SECURITY_BYPASSES, true)
            logScreenshot = intent.getBooleanExtra(PreferenceKeys.LOG_UNRESTRICTED_SCREENSHOTS, true)
            logDT2S = intent.getBooleanExtra(PreferenceKeys.LOG_DT2S, true)
            logEasyUnlock = intent.getBooleanExtra(PreferenceKeys.LOG_EASY_UNLOCK, true)
            isChanged = true
            targetKey = "ALL_SETTINGS"
            targetValue = isMasterEnabled
        } else if (action == "io.github.hohojia886.pixeltweaks.SETTING_CHANGED") {
            val key = intent.getStringExtra(PreferenceKeys.EXTRA_KEY) ?: return
            val value = intent.getBooleanExtra(PreferenceKeys.EXTRA_VALUE, true)
            when (key) {
                PreferenceKeys.ENABLE_MASTER_LOG -> if (isMasterEnabled != value) { isMasterEnabled = value; isChanged = true }
                PreferenceKeys.LOG_CALL_RECORDING -> if (logCallRec != value) { logCallRec = value; isChanged = true }
                PreferenceKeys.LOG_CALL_NOTES -> if (logCallNotes != value) { logCallNotes = value; isChanged = true }
                PreferenceKeys.LOG_CLEAR_ALL -> if (logClearAll != value) { logClearAll = value; isChanged = true }
                PreferenceKeys.LOG_NETWORK_TRAFFIC -> if (logTraffic != value) { logTraffic = value; isChanged = true }
                PreferenceKeys.LOG_QUICK_SETTINGS -> if (logQS != value) { logQS = value; isChanged = true }
                PreferenceKeys.LOG_SECURITY_BYPASSES -> if (logPM != value) { logPM = value; isChanged = true }
                PreferenceKeys.LOG_UNRESTRICTED_SCREENSHOTS -> if (logScreenshot != value) { logScreenshot = value; isChanged = true }
                PreferenceKeys.LOG_DT2S -> if (logDT2S != value) { logDT2S = value; isChanged = true }
                PreferenceKeys.LOG_EASY_UNLOCK -> if (logEasyUnlock != value) { logEasyUnlock = value; isChanged = true }
            }
            targetKey = key
            targetValue = value
        }

        if (isChanged && (isMasterEnabled || targetKey == PreferenceKeys.ENABLE_MASTER_LOG)) {
            logger.i("PXTK_Hook", "[Success] Log setting [$targetKey] updated to $targetValue")
        }
    }

    // Verbose: Only logs if Master and Sub-toggle are both enabled
    @Suppress("NOTHING_TO_INLINE")
    inline fun v(tag: String, status: String, msg: String) {
        if (isMasterEnabled && isSubEnabled(tag)) {
            runCatching { logger.v("PXTK_$tag", "[$status] $msg") }
        }
    }

    // Debug: Only logs if Master and Sub-toggle are both enabled
    @Suppress("NOTHING_TO_INLINE")
    inline fun d(tag: String, status: String, msg: String) {
        if (isMasterEnabled && isSubEnabled(tag)) {
            runCatching { logger.d("PXTK_$tag", "[$status] $msg") }
        }
    }

    // Info: Only logs if Master and Sub-toggle are both enabled
    @Suppress("NOTHING_TO_INLINE")
    inline fun i(tag: String, status: String, msg: String) {
        if (isMasterEnabled && isSubEnabled(tag)) {
            runCatching { logger.i("PXTK_$tag", "[$status] $msg") }
        }
    }

    // Error: Critical hook failures are always logged, others respect the master switch
    @Suppress("NOTHING_TO_INLINE")
    inline fun e(tag: String, status: String, msg: String, tr: Throwable? = null) {
        if (isMasterEnabled || tag == "Hook") {
            runCatching { logger.e("PXTK_$tag", "[$status] $msg", tr) }
        }
    }

    // Warning: Only logs if Master and Sub-toggle are both enabled
    @Suppress("NOTHING_TO_INLINE")
    inline fun w(tag: String, status: String, msg: String) {
        if (isMasterEnabled && isSubEnabled(tag)) {
            runCatching { logger.w("PXTK_$tag", "[$status] $msg") }
        }
    }

    // Helper: Maps functional tags to their respective toggle states
    fun isSubEnabled(tag: String): Boolean {
        return when (tag) {
            "CallRec" -> logCallRec
            "CallNotes" -> logCallNotes
            "ClearAll" -> logClearAll
            "Traffic" -> logTraffic
            "QuickSettings" -> logQS
            "Security" -> logPM
            "Screenshot" -> logScreenshot
            "DT2S" -> logDT2S
            "EasyUnlock" -> logEasyUnlock
            else -> true
        }
    }
}
