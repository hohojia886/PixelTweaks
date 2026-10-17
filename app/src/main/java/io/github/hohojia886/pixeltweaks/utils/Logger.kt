package io.github.hohojia886.pixeltweaks.utils

import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.Log
import io.github.libxposed.api.XposedModule

/**
 * Standardized logging utility for the PixelTweaks module.
 * Features centralized toggles for each functional log category (Security, Interface, Gestures, QuickSettings).
 */
object Logger {

    /**
     * Logger interface to abstract logging backends (Logcat or file-based).
     */
    interface Logger {
        /**
         * Logs a verbose level message.
         *
         * @param tag Logcat tag string.
         * @param msg Log message text.
         */
        fun v(tag: String, msg: String)

        /**
         * Logs a debug level message.
         *
         * @param tag Logcat tag string.
         * @param msg Log message text.
         */
        fun d(tag: String, msg: String)

        /**
         * Logs an info level message.
         *
         * @param tag Logcat tag string.
         * @param msg Log message text.
         */
        fun i(tag: String, msg: String)

        /**
         * Logs a warning level message.
         *
         * @param tag Logcat tag string.
         * @param msg Log message text.
         */
        fun w(tag: String, msg: String)

        /**
         * Logs an error level message with optional stacktrace exception.
         *
         * @param tag Logcat tag string.
         * @param msg Log message text.
         * @param tr Optional throwable exception instance.
         */
        fun e(tag: String, msg: String, tr: Throwable?)
    }

    /**
     * Default logger using android.util.Log.
     * Routes internal logging calls to standard logcat.
     */
    private object AndroidLogger : Logger {
        override fun v(tag: String, msg: String) { Log.v(tag, msg) }
        override fun d(tag: String, msg: String) { Log.d(tag, msg) }
        override fun i(tag: String, msg: String) { Log.i(tag, msg) }
        override fun w(tag: String, msg: String) { Log.w(tag, msg) }
        override fun e(tag: String, msg: String, tr: Throwable?) { Log.e(tag, msg, tr) }
    }

    @Volatile var logger: Logger = AndroidLogger

    @Volatile var isMasterEnabled = false
    @Volatile var logSecurity = true
    @Volatile var logInterface = true
    @Volatile var logGestures = true
    @Volatile var logQuickSettings = true

    private var lastSyncTime = 0L

    /**
     * Initializes logging state from storage during process attachment.
     * Synchronizes logging toggles from preferences bundle.
     */
    fun sync(module: XposedModule, classLoader: ClassLoader? = null) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastSyncTime < 2000L) {
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
            
            if (isMasterEnabled) {
                logger.i("PXTK_Hook", "[Logger] Settings synced. Master=$isMasterEnabled (PID: ${Process.myPid()})")
            }
        }
    }

    private var lastBroadcastKey = ""
    private var lastBroadcastTime = 0L

    /**
     * Handles real-time log toggle updates via IPC broadcasts.
     * Updates master and category logging flags dynamically.
     */
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
            if (targetKey != lastBroadcastKey || now - lastBroadcastTime > 2000L) {
                lastBroadcastKey = targetKey
                lastBroadcastTime = now
                logger.i("PXTK_Hook", "[Success] Log setting [$targetKey] updated to $targetValue")
            }
        }
    }

    /**
     * Logs debug message.
     * Checks master and category toggles before logging.
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun d(tag: String, msg: String) {
        if (isMasterEnabled && isSubEnabled(tag)) {
            runCatching { logger.d("PXTK_$tag", msg) }
        }
    }

    /**
     * Logs debug message with status if master and sub-toggle are enabled.
     * Appends status prefix to message payload.
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun d(tag: String, status: String, msg: String) {
        if (isMasterEnabled && isSubEnabled(tag)) {
            runCatching { logger.d("PXTK_$tag", "[$status] $msg") }
        }
    }

    /**
     * Logs info message.
     * Checks master and category toggles before logging.
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun i(tag: String, msg: String) {
        if (isMasterEnabled && isSubEnabled(tag)) {
            runCatching { logger.i("PXTK_$tag", msg) }
        }
    }

    /**
     * Logs info message with status if master and sub-toggle are enabled.
     * Appends status prefix to message payload.
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun i(tag: String, status: String, msg: String) {
        if (isMasterEnabled && isSubEnabled(tag)) {
            runCatching { logger.i("PXTK_$tag", "[$status] $msg") }
        }
    }

    /**
     * Logs error message with optional throwable.
     * Critical hook failures are always logged regardless of toggles.
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun e(tag: String, msg: String, tr: Throwable? = null) {
        if (isMasterEnabled || tag == "Hook") {
            runCatching { logger.e("PXTK_$tag", msg, tr) }
        }
    }

    /**
     * Logs error message with status and optional throwable.
     * Critical hook failures are always logged regardless of toggles.
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun e(tag: String, status: String, msg: String, tr: Throwable? = null) {
        if (isMasterEnabled || tag == "Hook") {
            runCatching { logger.e("PXTK_$tag", "[$status] $msg", tr) }
        }
    }

    /**
     * Logs warning message.
     * Checks master and category toggles before logging.
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun w(tag: String, msg: String) {
        if (isMasterEnabled && isSubEnabled(tag)) {
            runCatching { logger.w("PXTK_$tag", msg) }
        }
    }

    /**
     * Logs warning message with status if master and sub-toggle are enabled.
     * Appends status prefix to message payload.
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun w(tag: String, status: String, msg: String) {
        if (isMasterEnabled && isSubEnabled(tag)) {
            runCatching { logger.w("PXTK_$tag", "[$status] $msg") }
        }
    }

    /**
     * Maps functional tags to their respective toggle states.
     * Evaluates tag against security, interface, gestures, and QS flags.
     */
    fun isSubEnabled(tag: String): Boolean {
        return when (tag) {
            "Security", "EasyUnlock", "Screenshot", "HideVpn" -> logSecurity
            "ClearAll", "Traffic", "Battery", "EnergyRing" -> logInterface
            "DT2S" -> logGestures
            "QuickSettings" -> logQuickSettings
            else -> true
        }
    }
}
