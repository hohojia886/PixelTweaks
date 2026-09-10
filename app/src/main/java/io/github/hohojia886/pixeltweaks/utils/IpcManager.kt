package io.github.hohojia886.pixeltweaks.utils

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.os.Process
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference

/**
 * IpcManager: Orchestrates cross-process communication and settings synchronization.
 * Manages secure broadcast registration, system context retrieval via reflection, 
 * and ensures that all hook instances across different processes stay in sync with the UI.
 */
object IpcManager {
    const val PREF_NAME = "io.github.hohojia886.pixeltweaks"
    const val ACTION_SETTING_CHANGED = "io.github.hohojia886.pixeltweaks.SETTING_CHANGED"
    const val ACTION_SETTINGS_SYNC = "io.github.hohojia886.pixeltweaks.SETTINGS_SYNC"
    const val ACTION_REQUEST_SLEEP = "io.github.hohojia886.pixeltweaks.REQUEST_SLEEP"
    const val PERMISSION_SYNC_SETTINGS = "io.github.hohojia886.pixeltweaks.permission.SYNC_SETTINGS"

    private var sysContextRef: WeakReference<Context>? = null // Cached system context

    // Retrieves the underlying system context using ActivityThread reflection
    fun getSystemContext(classLoader: ClassLoader): Context? {
        sysContextRef?.get()?.let { return it }
        return runCatching {
            val atClass = classLoader.loadClass("android.app.ActivityThread")
            val at = atClass.getDeclaredMethod("currentActivityThread").invoke(null) ?: return null
            val context = atClass.getDeclaredMethod("getSystemContext").invoke(at) as? Context
            context?.let { sysContextRef = WeakReference(it) }
            context
        }.getOrNull()
    }

    // Obtains a context suitable for ContentProvider calls, matching the current process identity
    fun getSafeContext(classLoader: ClassLoader, packageName: String? = null): Context? {
        return runCatching {
            val atClass = classLoader.loadClass("android.app.ActivityThread")
            val at = atClass.getDeclaredMethod("currentActivityThread").invoke(null) ?: return null
            val app = atClass.getDeclaredMethod("getApplication").invoke(at) as? Context
            
            if (app != null) return app

            val sysContext = atClass.getDeclaredMethod("getSystemContext").invoke(at) as? Context ?: return null
            
            val myUid = Process.myUid()
            val targetPackage = packageName ?: runCatching {
                val ipmClass = classLoader.loadClass("android.app.AppGlobals")
                val ipm = ipmClass.getDeclaredMethod("getPackageManager").invoke(null) ?: return@runCatching null
                val getPackagesMethod = ipm.javaClass.getDeclaredMethod("getPackagesForUid", Int::class.javaPrimitiveType)
                val packages = getPackagesMethod.invoke(ipm, myUid) as? Array<*>
                packages?.get(0) as? String
            }.getOrNull()

            if (myUid != 1000 && targetPackage != null && targetPackage != "android" && targetPackage != "unknown") {
                runCatching { sysContext.createPackageContext(targetPackage, 0) }.getOrDefault(sysContext)
            } else {
                sysContext
            }
        }.getOrNull()
    }

    // Unified preference loader: Prioritizes ContentProvider DE storage query (Direct Boot compatible) before CE fallback
    fun loadPreferences(module: XposedModule, classLoader: ClassLoader? = null, packageName: String? = null): Bundle {
        val bundle = Bundle()
        
        // 1. Primary: Xposed/LSPosed RemotePreferences
        val prefs = runCatching { module.getRemotePreferences(PREF_NAME) }.getOrNull()
        if (prefs != null) {
            runCatching {
                prefs.all.forEach { (k, v) ->
                    when (v) {
                        is Boolean -> bundle.putBoolean(k, v)
                        is Int -> bundle.putInt(k, v)
                        is Float -> bundle.putFloat(k, v)
                        is Long -> bundle.putLong(k, v)
                        is String -> bundle.putString(k, v)
                    }
                }
            }
            val knownBooleans = listOf(
                PreferenceKeys.ENABLE_EASY_UNLOCK, PreferenceKeys.ENABLE_EASY_UNLOCK_REBOOT,
                PreferenceKeys.ENABLE_QS_WIFI_FIX, PreferenceKeys.ENABLE_QS_DATA_FIX,
                PreferenceKeys.ENABLE_CLEAR_ALL, PreferenceKeys.ENABLE_NETWORK_TRAFFIC,
                PreferenceKeys.ENABLE_DT_LAUNCHER, PreferenceKeys.ENABLE_DT_LOCKSCREEN, PreferenceKeys.ENABLE_DT_STATUSBAR,
                PreferenceKeys.ENABLE_CALL_RECORDING, PreferenceKeys.DISABLE_VOICE_ANNOUNCEMENT, PreferenceKeys.DISABLE_CALL_NOTES_ANNOUNCEMENT,
                PreferenceKeys.ALLOW_DOWNGRADE, PreferenceKeys.BYPASS_SIGNATURE, PreferenceKeys.ENABLE_UNRESTRICTED_SCREENSHOTS,
                PreferenceKeys.ENABLE_MASTER_LOG, PreferenceKeys.LOG_CALL_NOTES, PreferenceKeys.LOG_CALL_RECORDING,
                PreferenceKeys.LOG_CLEAR_ALL, PreferenceKeys.LOG_NETWORK_TRAFFIC, PreferenceKeys.LOG_QUICK_SETTINGS,
                PreferenceKeys.LOG_SECURITY_BYPASSES, PreferenceKeys.LOG_UNRESTRICTED_SCREENSHOTS, PreferenceKeys.LOG_DT2S, PreferenceKeys.LOG_EASY_UNLOCK
            )
            val defaultFalseKeys = setOf(
                PreferenceKeys.ENABLE_CALL_RECORDING,
                PreferenceKeys.DISABLE_CALL_NOTES_ANNOUNCEMENT,
                PreferenceKeys.ALLOW_DOWNGRADE,
                PreferenceKeys.BYPASS_SIGNATURE,
                PreferenceKeys.ENABLE_EASY_UNLOCK_REBOOT,
                PreferenceKeys.ENABLE_MASTER_LOG
            )
            knownBooleans.forEach { key ->
                runCatching {
                    val defaultVal = key !in defaultFalseKeys
                    val v = prefs.getBoolean(key, defaultVal)
                    bundle.putBoolean(key, v)
                }
            }
            runCatching {
                val len = prefs.getInt(PreferenceKeys.EXPECTED_PASS_LEN, -1)
                if (len > 0) bundle.putInt(PreferenceKeys.EXPECTED_PASS_LEN, len)
            }
        }

        // 2. Direct DE Storage ContentProvider query (overlays/overrides for Direct Boot / pre-unlock)
        if (classLoader != null) {
            runCatching {
                val ctx = getSafeContext(classLoader, packageName) ?: getSystemContext(classLoader)
                if (ctx != null) {
                    val uri = Uri.parse("content://io.github.hohojia886.pixeltweaks")
                    val cpBundle = ctx.contentResolver.call(uri, "get", null, null)
                    if (cpBundle != null && !cpBundle.isEmpty) {
                        cpBundle.keySet().forEach { k ->
                            val v = cpBundle.get(k)
                            when (v) {
                                is Boolean -> bundle.putBoolean(k, v)
                                is Int -> bundle.putInt(k, v)
                                is Float -> bundle.putFloat(k, v)
                                is Long -> bundle.putLong(k, v)
                                is String -> bundle.putString(k, v)
                            }
                        }
                    }
                }
            }
        }

        return bundle
    }

    // Dispatches a full settings synchronization broadcast to all active hook processes
    @SuppressLint("WrongConstant")
    fun syncAllSettings(context: Context, prefs: SharedPreferences) {
        val intent = Intent(ACTION_SETTINGS_SYNC).apply {
            // 1. CallNotes
            putExtra(PreferenceKeys.DISABLE_CALL_NOTES_ANNOUNCEMENT, prefs.getBoolean(PreferenceKeys.DISABLE_CALL_NOTES_ANNOUNCEMENT, true))
            putExtra(PreferenceKeys.LOG_CALL_NOTES, prefs.getBoolean(PreferenceKeys.LOG_CALL_NOTES, true))

            // 2. CallRec
            putExtra(PreferenceKeys.ENABLE_CALL_RECORDING, prefs.getBoolean(PreferenceKeys.ENABLE_CALL_RECORDING, false))
            putExtra(PreferenceKeys.DISABLE_VOICE_ANNOUNCEMENT, prefs.getBoolean(PreferenceKeys.DISABLE_VOICE_ANNOUNCEMENT, true))
            putExtra(PreferenceKeys.LOG_CALL_RECORDING, prefs.getBoolean(PreferenceKeys.LOG_CALL_RECORDING, true))

            // 3. ClearAll
            putExtra(PreferenceKeys.ENABLE_CLEAR_ALL, prefs.getBoolean(PreferenceKeys.ENABLE_CLEAR_ALL, true))
            putExtra(PreferenceKeys.LOG_CLEAR_ALL, prefs.getBoolean(PreferenceKeys.LOG_CLEAR_ALL, true))

            // 4. DT2S
            putExtra(PreferenceKeys.ENABLE_DT_LAUNCHER, prefs.getBoolean(PreferenceKeys.ENABLE_DT_LAUNCHER, true))
            putExtra(PreferenceKeys.ENABLE_DT_LOCKSCREEN, prefs.getBoolean(PreferenceKeys.ENABLE_DT_LOCKSCREEN, true))
            putExtra(PreferenceKeys.ENABLE_DT_STATUSBAR, prefs.getBoolean(PreferenceKeys.ENABLE_DT_STATUSBAR, true))
            putExtra(PreferenceKeys.LOG_DT2S, prefs.getBoolean(PreferenceKeys.LOG_DT2S, true))

            // 5. EasyUnlock
            val expectedPassLen = prefs.getInt(PreferenceKeys.EXPECTED_PASS_LEN, -1).let { inMemoryLen ->
                if (inMemoryLen > 0) inMemoryLen else runCatching {
                    val uri = Uri.parse("content://io.github.hohojia886.pixeltweaks")
                    val bundle = context.contentResolver.call(uri, "get", null, null)
                    bundle?.getInt(PreferenceKeys.EXPECTED_PASS_LEN, -1) ?: -1
                }.getOrDefault(-1)
            }
            putExtra(PreferenceKeys.ENABLE_EASY_UNLOCK, prefs.getBoolean(PreferenceKeys.ENABLE_EASY_UNLOCK, true))
            putExtra(PreferenceKeys.ENABLE_EASY_UNLOCK_REBOOT, prefs.getBoolean(PreferenceKeys.ENABLE_EASY_UNLOCK_REBOOT, false))
            putExtra(PreferenceKeys.EXPECTED_PASS_LEN, expectedPassLen)
            putExtra(PreferenceKeys.IS_FIRST_UNLOCK_DONE, prefs.getBoolean(PreferenceKeys.IS_FIRST_UNLOCK_DONE, false))
            putExtra(PreferenceKeys.LOG_EASY_UNLOCK, prefs.getBoolean(PreferenceKeys.LOG_EASY_UNLOCK, true))

            // 6. QuickSettings
            putExtra(PreferenceKeys.ENABLE_QS_WIFI_FIX, prefs.getBoolean(PreferenceKeys.ENABLE_QS_WIFI_FIX, true))
            putExtra(PreferenceKeys.ENABLE_QS_DATA_FIX, prefs.getBoolean(PreferenceKeys.ENABLE_QS_DATA_FIX, true))
            putExtra(PreferenceKeys.LOG_QUICK_SETTINGS, prefs.getBoolean(PreferenceKeys.LOG_QUICK_SETTINGS, true))

            // 7. Screenshot
            putExtra(PreferenceKeys.ENABLE_UNRESTRICTED_SCREENSHOTS, prefs.getBoolean(PreferenceKeys.ENABLE_UNRESTRICTED_SCREENSHOTS, true))
            putExtra(PreferenceKeys.LOG_UNRESTRICTED_SCREENSHOTS, prefs.getBoolean(PreferenceKeys.LOG_UNRESTRICTED_SCREENSHOTS, true))

            // 8. Security
            putExtra(PreferenceKeys.ALLOW_DOWNGRADE, prefs.getBoolean(PreferenceKeys.ALLOW_DOWNGRADE, false))
            putExtra(PreferenceKeys.BYPASS_SIGNATURE, prefs.getBoolean(PreferenceKeys.BYPASS_SIGNATURE, false))
            putExtra(PreferenceKeys.DOWNGRADE_TIMESTAMP, prefs.getLong(PreferenceKeys.DOWNGRADE_TIMESTAMP, 0L))
            putExtra(PreferenceKeys.SIGNATURE_TIMESTAMP, prefs.getLong(PreferenceKeys.SIGNATURE_TIMESTAMP, 0L))
            putExtra(PreferenceKeys.LOG_SECURITY_BYPASSES, prefs.getBoolean(PreferenceKeys.LOG_SECURITY_BYPASSES, true))

            // 9. Traffic
            putExtra(PreferenceKeys.ENABLE_NETWORK_TRAFFIC, prefs.getBoolean(PreferenceKeys.ENABLE_NETWORK_TRAFFIC, true))
            putExtra(PreferenceKeys.NETWORK_TRAFFIC_INTERVAL, prefs.getInt(PreferenceKeys.NETWORK_TRAFFIC_INTERVAL, 1))
            putExtra(PreferenceKeys.NETWORK_TRAFFIC_FONT_SIZE, prefs.getFloat(PreferenceKeys.NETWORK_TRAFFIC_FONT_SIZE, 8f))
            putExtra(PreferenceKeys.NETWORK_TRAFFIC_THRESHOLD, prefs.getInt(PreferenceKeys.NETWORK_TRAFFIC_THRESHOLD, 1))
            putExtra(PreferenceKeys.LOG_NETWORK_TRAFFIC, prefs.getBoolean(PreferenceKeys.LOG_NETWORK_TRAFFIC, true))

            // General / Debug
            putExtra(PreferenceKeys.ENABLE_MASTER_LOG, prefs.getBoolean(PreferenceKeys.ENABLE_MASTER_LOG, false))

            addFlags(0x01000000) // FLAG_RECEIVER_INCLUDE_BACKGROUND
        }
        context.sendBroadcast(intent)
    }

    // Sends a high-priority request to SystemUI to put the device to sleep
    @SuppressLint("WrongConstant")
    fun sendSleepRequest(context: Context) {
        val intent = Intent(ACTION_REQUEST_SLEEP).apply {
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND or 0x01000000)
        }
        context.sendBroadcast(intent)
    }

    // Dispatches a broadcast for a single preference change to minimize IPC overhead
    @SuppressLint("WrongConstant")
    fun sendUpdateBroadcast(context: Context, key: String, value: Any) {
        val intent = Intent(ACTION_SETTING_CHANGED).apply {
            putExtra(PreferenceKeys.EXTRA_KEY, key)
            when (value) {
                is Boolean -> putExtra(PreferenceKeys.EXTRA_VALUE, value)
                is Int -> putExtra(PreferenceKeys.EXTRA_VALUE, value)
                is Float -> putExtra(PreferenceKeys.EXTRA_VALUE, value)
                is Long -> putExtra(PreferenceKeys.EXTRA_VALUE, value)
            }
            addFlags(0x01000000) // FLAG_RECEIVER_INCLUDE_BACKGROUND
        }
        context.sendBroadcast(intent)
    }

    // Registers a receiver with UID verification to ensure settings are only accepted from trusted sources
    fun registerSecureReceiver(
        context: Context,
        moduleUid: Int,
        extraActions: List<String> = emptyList(),
        onVerifiedBroadcast: (intent: Intent) -> Unit
    ) {
        try {
            val filter = IntentFilter().apply {
                addAction(ACTION_SETTING_CHANGED)
                addAction(ACTION_SETTINGS_SYNC)
                extraActions.forEach { addAction(it) }
            }
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val senderUid = runCatching {
                        val method = BroadcastReceiver::class.java.getDeclaredMethod("getSendingUid")
                        method.invoke(this) as Int
                    }.getOrDefault(-1)

                    if (senderUid == 1000 || senderUid == moduleUid || senderUid == Process.myUid()) {
                        Logger.handleBroadcast(intent)
                        onVerifiedBroadcast(intent)
                    }
                }
            }
            val targetContext = context.applicationContext ?: context
            targetContext.registerReceiver(receiver, filter, null, null, Context.RECEIVER_EXPORTED)
        } catch (t: Throwable) {
            android.util.Log.wtf("PXTK_Ipc", "CRITICAL: Receiver registration failed", t)
        }
    }

    // Specialized receiver for sleep requests with strict sender identity validation
    fun registerSleepReceiver(context: Context, moduleUid: Int, onReceive: () -> Unit) {
        try {
            val filter = IntentFilter(ACTION_REQUEST_SLEEP)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val senderUid = runCatching {
                        val method = BroadcastReceiver::class.java.getDeclaredMethod("getSendingUid")
                        method.invoke(this) as Int
                    }.getOrDefault(-1)

                    val isTrusted = senderUid == 1000 || senderUid == moduleUid || senderUid == Process.myUid() || run {
                        val trustedLaunchers = listOf("com.google.android.apps.nexuslauncher", "com.android.launcher3", "com.google.android.launcher")
                        trustedLaunchers.any { pkg ->
                            runCatching { context.packageManager.getPackageInfo(pkg, 0)?.applicationInfo?.uid }.getOrNull() == senderUid
                        }
                    }

                    if (isTrusted) {
                        onReceive()
                    } else {
                        Logger.e("Security", "Blocked", "Unauthorized sleep request from UID: $senderUid")
                    }
                }
            }
            val targetContext = context.applicationContext ?: context
            targetContext.registerReceiver(receiver, filter, null, null, Context.RECEIVER_EXPORTED)
        } catch (t: Throwable) {
            android.util.Log.wtf("PXTK_Ipc", "CRITICAL: Sleep receiver registration failed", t)
        }
    }
}
