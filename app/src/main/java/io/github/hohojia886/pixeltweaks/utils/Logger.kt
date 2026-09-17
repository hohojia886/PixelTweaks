package io.github.hohojia886.pixeltweaks.utils

import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.Log
import io.github.libxposed.api.XposedModule

/**
 * Logger: Standardized logging utility for the PixelTweaks module.
 * Features centralized toggles for each functional category, auto-prefixing with "PXTK_",
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
    @Volatile var logSecurity = true // Security & System logs
    @Volatile var logInterface = true // Interface & Status bar logs
    @Volatile var logGestures = true // Gestures logs
    @Volatile var logQuickSettings = true // Quick Settings logs

    private var lastSyncTime = 0L // Debounce caching for syncSettings

    // Initializes logging state from DE storage / RemotePreferences during process attachment
    fun sync(module: XposedModule, classLoader: ClassLoader? = null) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastSyncTime < 2000L) {
            // Already synced recently within this process, skip to avoid IPC spam
            return
        }
        lastSyncTime = now

        runCatching {
            val bundle = if (classLoader != null) IpcManager.loadPreferences(module, classLoader) else Bundle()
            val prefs = if (bundle.isEmpty) module.getRemotePreferences(IpcManager.PREF_NAME) else null

            isMasterEnabled = bundle.getBoolean(PreferenceKeys.ENABLE_MASTER_LOG, prefs?.getBoolean(PreferenceKeys.ENABLE_MASTER_LOG, false) ?: false)
            logSecurity = bundle.getBoolean(PreferenceKeys.LOG_SECURITY, prefs?.getBoolean(PreferenceKeys.LOG_SECURITY, true) ?: true)
            logInterface = bundle.getBoolean(PreferenceKeys.LOG_INTERFACE, prefs?.getBoolean(PreferenceKeys.LOG_INTERFACE, true) ?: true)
            logGestures = bundle.getBoolean(PreferenceKeys.LOG_GESTURES, prefs?.getBoolean(PreferenceKeys.LOG_GESTURES, true) ?: true)
            logQuickSettings = bundle.getBoolean(PreferenceKeys.LOG_QUICK_SETTINGS, prefs?.getBoolean(PreferenceKeys.LOG_QUICK_SETTINGS, true) ?: true)
            
            logger.i("PXTK_Hook", "[Logger] Settings synced. Master=$isMasterEnabled (PID: ${Process.myPid()})")
        }
    }

    private var lastBroadcastKey = ""
    private var lastBroadcastTime = 0L

    // Handles real-time log toggle updates via IPC broadcasts
    fun handleBroadcast(intent: Intent) {
        val action = intent.action ?: return
        
        var isChanged = false
        var targetKey = ""
        var targetValue = false

        if (action == "io.github.hohojia886.pixeltweaks.SETTINGS_SYNC") {
            isMasterEnabled = intent.getBooleanExtra(PreferenceKeys.ENABLE_MASTER_LOG, false)
            logSecurity = intent.getBooleanExtra(PreferenceKeys.LOG_SECURITY, true)
            logInterface = intent.getBooleanExtra(PreferenceKeys.LOG_INTERFACE, true)
            logGestures = intent.getBooleanExtra(PreferenceKeys.LOG_GESTURES, true)
            logQuickSettings = intent.getBooleanExtra(PreferenceKeys.LOG_QUICK_SETTINGS, true)
            isChanged = true
            targetKey = "ALL_SETTINGS"
            targetValue = isMasterEnabled
        } else if (action == "io.github.hohojia886.pixeltweaks.SETTING_CHANGED") {
            val key = intent.getStringExtra(PreferenceKeys.EXTRA_KEY) ?: return
            val value = intent.getBooleanExtra(PreferenceKeys.EXTRA_VALUE, true)
            when (key) {
                PreferenceKeys.ENABLE_MASTER_LOG -> if (isMasterEnabled != value) { isMasterEnabled = value; isChanged = true }
                PreferenceKeys.LOG_SECURITY -> if (logSecurity != value) { logSecurity = value; isChanged = true }
                PreferenceKeys.LOG_INTERFACE -> if (logInterface != value) { logInterface = value; isChanged = true }
                PreferenceKeys.LOG_GESTURES -> if (logGestures != value) { logGestures = value; isChanged = true }
                PreferenceKeys.LOG_QUICK_SETTINGS -> if (logQuickSettings != value) { logQuickSettings = value; isChanged = true }
            }
            targetKey = key
            targetValue = value
        }

        val now = SystemClock.elapsedRealtime()
        if (isChanged && (isMasterEnabled || targetKey == PreferenceKeys.ENABLE_MASTER_LOG)) {
            // Debounce identical logs from multiple hooks receiving the same broadcast
            if (targetKey != lastBroadcastKey || now - lastBroadcastTime > 2000L) {
                lastBroadcastKey = targetKey
                lastBroadcastTime = now
                logger.i("PXTK_Hook", "[Success] Log setting [$targetKey] updated to $targetValue")
            }
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
            "Security", "EasyUnlock", "Screenshot" -> logSecurity
            "ClearAll", "Traffic" -> logInterface
            "DT2S" -> logGestures
            "QuickSettings" -> logQuickSettings
            else -> true
        }
    }
}
