@file:Suppress("DiscouragedPrivateApi", "PrivateApi", "DiscouragedApi", "DEPRECATION")

package io.github.hohojia886.pixeltweaks.utils

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Process
import android.util.Log
import androidx.core.net.toUri
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference

/**
 * Orchestrates cross-process communication (IPC) and settings synchronization.
 *
 * Manages secure broadcast registration, sender UID verification, and RemotePreferences/ContentProvider fallbacks.
 * Ensures settings changes in the UI app are reliably propagated to hooks running in SystemUI, Launcher, and SystemServer.
 */
object IpcManager {
    const val PREF_NAME = "io.github.hohojia886.pixeltweaks"
    const val ACTION_SETTING_CHANGED = "io.github.hohojia886.pixeltweaks.SETTING_CHANGED"
    const val ACTION_SETTINGS_SYNC = "io.github.hohojia886.pixeltweaks.SETTINGS_SYNC"
    const val ACTION_REQUEST_SLEEP = "io.github.hohojia886.pixeltweaks.REQUEST_SLEEP"

    private var sysContextRef: WeakReference<Context>? = null
    @Volatile private var cachedModulePkgAppId = -1

    /**
     * Resolves the real sender UID of an incoming broadcast using framework APIs.
     *
     * @param receiver The active [BroadcastReceiver] executing inside [BroadcastReceiver.onReceive].
     * @return The integer UID of the process that sent the broadcast, or -1 if unable to resolve.
     */
    fun resolveSenderUid(receiver: BroadcastReceiver): Int {
        // 1. Try Android 14+ (API 34) public getSentFromUid API
        runCatching {
            val method = receiver.javaClass.getMethod("getSentFromUid")
            val uid = method.invoke(receiver) as Int
            if (uid > 0) return uid
        }

        // 2. Try AOSP hidden getSendingUid API
        runCatching {
            val method = BroadcastReceiver::class.java.getDeclaredMethod("getSendingUid")
            method.isAccessible = true
            val uid = method.invoke(receiver) as Int
            if (uid > 0) return uid
        }

        return -1
    }

    /**
     * Resolves and caches the App ID of the PixelTweaks package.
     * Queries PackageManager for package info and extracts App ID.
     */
    private fun getModulePackageAppId(context: Context): Int {
        if (cachedModulePkgAppId > 0) return cachedModulePkgAppId
        val appId = runCatching {
            context.packageManager.getPackageInfo("io.github.hohojia886.pixeltweaks", 0)?.applicationInfo?.uid?.rem(100000)
        }.getOrNull() ?: -1
        if (appId > 0) {
            cachedModulePkgAppId = appId
        }
        return cachedModulePkgAppId
    }

    /**
     * Retrieves the underlying system context using ActivityThread reflection.
     *
     * @param classLoader The target process ClassLoader used to reflectively load ActivityThread.
     * @return A cached [Context] reference associated with system_server, or null on failure.
     */
    fun getSystemContext(classLoader: ClassLoader): Context? {
        sysContextRef?.get()?.let { return it }
        return runCatching {
            val atClass = classLoader.loadClass("android.app.ActivityThread")
            val at = atClass.getDeclaredMethod("currentActivityThread").invoke(null) ?: return null
            val context = atClass.getDeclaredMethod("getSystemContext").invoke(at) as? Context
            context?.let { sysContextRef = WeakReference(it) }
            context
        }.onFailure { e ->
            Logger.e("IpcManager", "Error", "Failed to getSystemContext", e)
        }.getOrNull()
    }

    /**
     * Obtains a process-safe Context matching caller identity for ContentProvider calls.
     *
     * @param classLoader The target process ClassLoader.
     * @param packageName Optional package name override for package context creation.
     * @return A safe [Context] instance matching process scope.
     */
    fun getSafeContext(classLoader: ClassLoader, packageName: String? = null): Context? {
        return runCatching {
            val atClass = classLoader.loadClass("android.app.ActivityThread")
            val at = atClass.getDeclaredMethod("currentActivityThread").invoke(null) ?: return null
            val app = atClass.getDeclaredMethod("getApplication").invoke(at) as? Context
            app?.let { return it }

            val sysContext = (atClass.getDeclaredMethod("getSystemContext").invoke(at) as? Context) ?: return null
            
            val myUid = Process.myUid()
            val targetPackage = packageName ?: runCatching {
                val ipmClass = classLoader.loadClass("android.app.AppGlobals")
                val ipm = ipmClass.getDeclaredMethod("getPackageManager").invoke(null) ?: return@runCatching null
                val getPackagesMethod = ipm.javaClass.getDeclaredMethod("getPackagesForUid", Int::class.javaPrimitiveType)
                val packages = getPackagesMethod.invoke(ipm, myUid) as? Array<*>
                packages?.get(0) as? String
            }.getOrNull()

            if ((myUid != 1000) && (targetPackage != null) && (targetPackage != "android") && (targetPackage != "unknown")) {
                runCatching { sysContext.createPackageContext(targetPackage, 0) }.getOrDefault(sysContext)
            } else {
                sysContext
            }
        }.onFailure { e ->
            Logger.e("IpcManager", "Error", "Failed to getSafeContext", e)
        }.getOrNull()
    }

    /**
     * Unified preference loader for Xposed modules across target processes.
     *
     * @param module The active LSPosed [XposedModule] instance.
     * @param classLoader Optional ClassLoader for ContentProvider fallback queries.
     * @param packageName Optional target package name.
     * @return A [Bundle] containing populated preference key-value pairs.
     */
    fun loadPreferences(module: XposedModule, classLoader: ClassLoader? = null, packageName: String? = null): Bundle {
        val bundle = Bundle()
        
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
                PreferenceKeys.ENABLE_CLEAR_ALL, PreferenceKeys.ENABLE_TABLET_MODE, PreferenceKeys.ENABLE_BATTERY_INFO, PreferenceKeys.ENABLE_CAMERA_ENERGY_RING, PreferenceKeys.RING_ONLY_CHARGING, PreferenceKeys.ENABLE_NETWORK_TRAFFIC, PreferenceKeys.ENABLE_STATUSBAR_BATTERY_PERCENT, PreferenceKeys.ENABLE_HIDE_VPN_ICON,
                PreferenceKeys.ENABLE_DT_LAUNCHER, PreferenceKeys.ENABLE_DT_LOCKSCREEN, PreferenceKeys.ENABLE_DT_STATUSBAR,
                PreferenceKeys.ALLOW_DOWNGRADE, PreferenceKeys.BYPASS_SIGNATURE, PreferenceKeys.ENABLE_UNRESTRICTED_SCREENSHOTS,
                PreferenceKeys.ENABLE_MASTER_LOG, PreferenceKeys.LOG_SECURITY, PreferenceKeys.LOG_INTERFACE,
                PreferenceKeys.LOG_GESTURES, PreferenceKeys.LOG_QUICK_SETTINGS,
            )
            val defaultFalseKeys = setOf(
                PreferenceKeys.ALLOW_DOWNGRADE,
                PreferenceKeys.BYPASS_SIGNATURE,
                PreferenceKeys.ENABLE_EASY_UNLOCK_REBOOT,
                PreferenceKeys.ENABLE_TABLET_MODE,
                PreferenceKeys.ENABLE_BATTERY_INFO,
                PreferenceKeys.ENABLE_CAMERA_ENERGY_RING,
                PreferenceKeys.RING_ONLY_CHARGING,
                PreferenceKeys.ENABLE_STATUSBAR_BATTERY_PERCENT,
                PreferenceKeys.ENABLE_HIDE_VPN_ICON,
                PreferenceKeys.ENABLE_MASTER_LOG,
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
                val dgTs = prefs.getLong(PreferenceKeys.DOWNGRADE_TIMESTAMP, 0L)
                if (dgTs > 0) bundle.putLong(PreferenceKeys.DOWNGRADE_TIMESTAMP, dgTs)
                val sigTs = prefs.getLong(PreferenceKeys.SIGNATURE_TIMESTAMP, 0L)
                if (sigTs > 0) bundle.putLong(PreferenceKeys.SIGNATURE_TIMESTAMP, sigTs)
            }
        }

        if (classLoader != null && (bundle.isEmpty || prefs == null || prefs.all.isEmpty())) {
            runCatching {
                val ctx = getSafeContext(classLoader, packageName) ?: getSystemContext(classLoader)
                if (ctx != null) {
                    val uri = "content://io.github.hohojia886.pixeltweaks".toUri()
                    val cpBundle = ctx.contentResolver.call(uri, "get", null, null)
                    if (cpBundle != null && !cpBundle.isEmpty) {
                        cpBundle.keySet().forEach { k ->
                            when (val v = cpBundle[k]) {
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

    /**
     * Creates basic broadcast options with share identity enabled.
     * Returns Bundle containing configured BroadcastOptions.
     */
    private fun makeBroadcastOptions(): Bundle {
        return android.app.BroadcastOptions.makeBasic().apply {
            isShareIdentityEnabled = true
        }.toBundle()
    }

    /**
     * Dispatches full settings synchronization broadcast to all active hook processes.
     *
     * @param context Host process Context used to send the broadcast.
     * @param prefs The [SharedPreferences] instance containing current settings.
     */
    @SuppressLint("WrongConstant")
    fun syncAllSettings(context: Context, prefs: SharedPreferences) {
        val intent = Intent(ACTION_SETTINGS_SYNC).apply {
            putExtra(PreferenceKeys.ENABLE_CLEAR_ALL, prefs.getBoolean(PreferenceKeys.ENABLE_CLEAR_ALL, true))
            putExtra(PreferenceKeys.ENABLE_TABLET_MODE, prefs.getBoolean(PreferenceKeys.ENABLE_TABLET_MODE, false))
            putExtra(PreferenceKeys.ENABLE_BATTERY_INFO, prefs.getBoolean(PreferenceKeys.ENABLE_BATTERY_INFO, false))
            putExtra(PreferenceKeys.ENABLE_CAMERA_ENERGY_RING, prefs.getBoolean(PreferenceKeys.ENABLE_CAMERA_ENERGY_RING, false))
            putExtra(PreferenceKeys.RING_RADIUS_OFFSET, prefs.getFloat(PreferenceKeys.RING_RADIUS_OFFSET, 0f))
            putExtra(PreferenceKeys.RING_STROKE_WIDTH, prefs.getFloat(PreferenceKeys.RING_STROKE_WIDTH, 2.0f))
            putExtra(PreferenceKeys.RING_ONLY_CHARGING, prefs.getBoolean(PreferenceKeys.RING_ONLY_CHARGING, false))
            putExtra(PreferenceKeys.ENABLE_RING_ROTATION, prefs.getBoolean(PreferenceKeys.ENABLE_RING_ROTATION, false))

            putExtra(PreferenceKeys.ENABLE_DT_LAUNCHER, prefs.getBoolean(PreferenceKeys.ENABLE_DT_LAUNCHER, true))
            putExtra(PreferenceKeys.ENABLE_DT_LOCKSCREEN, prefs.getBoolean(PreferenceKeys.ENABLE_DT_LOCKSCREEN, true))
            putExtra(PreferenceKeys.ENABLE_DT_STATUSBAR, prefs.getBoolean(PreferenceKeys.ENABLE_DT_STATUSBAR, true))

            val expectedPassLen = prefs.getInt(PreferenceKeys.EXPECTED_PASS_LEN, -1).let { inMemoryLen ->
                if (inMemoryLen > 0) inMemoryLen else runCatching {
                    val uri = "content://io.github.hohojia886.pixeltweaks".toUri()
                    val bundle = context.contentResolver.call(uri, "get", null, null)
                    bundle?.getInt(PreferenceKeys.EXPECTED_PASS_LEN, -1) ?: -1
                }.getOrDefault(-1)
            }
            putExtra(PreferenceKeys.ENABLE_EASY_UNLOCK, prefs.getBoolean(PreferenceKeys.ENABLE_EASY_UNLOCK, true))
            putExtra(PreferenceKeys.ENABLE_EASY_UNLOCK_REBOOT, prefs.getBoolean(PreferenceKeys.ENABLE_EASY_UNLOCK_REBOOT, false))
            putExtra(PreferenceKeys.EXPECTED_PASS_LEN, expectedPassLen)
            putExtra(PreferenceKeys.IS_FIRST_UNLOCK_DONE, prefs.getBoolean(PreferenceKeys.IS_FIRST_UNLOCK_DONE, false))

            putExtra(PreferenceKeys.ENABLE_QS_WIFI_FIX, prefs.getBoolean(PreferenceKeys.ENABLE_QS_WIFI_FIX, true))
            putExtra(PreferenceKeys.ENABLE_QS_DATA_FIX, prefs.getBoolean(PreferenceKeys.ENABLE_QS_DATA_FIX, true))

            putExtra(PreferenceKeys.ENABLE_UNRESTRICTED_SCREENSHOTS, prefs.getBoolean(PreferenceKeys.ENABLE_UNRESTRICTED_SCREENSHOTS, true))

            putExtra(PreferenceKeys.ALLOW_DOWNGRADE, prefs.getBoolean(PreferenceKeys.ALLOW_DOWNGRADE, false))
            putExtra(PreferenceKeys.BYPASS_SIGNATURE, prefs.getBoolean(PreferenceKeys.BYPASS_SIGNATURE, false))
            putExtra(PreferenceKeys.DOWNGRADE_TIMESTAMP, prefs.getLong(PreferenceKeys.DOWNGRADE_TIMESTAMP, 0L))
            putExtra(PreferenceKeys.SIGNATURE_TIMESTAMP, prefs.getLong(PreferenceKeys.SIGNATURE_TIMESTAMP, 0L))

            putExtra(PreferenceKeys.ENABLE_NETWORK_TRAFFIC, prefs.getBoolean(PreferenceKeys.ENABLE_NETWORK_TRAFFIC, true))
            putExtra(PreferenceKeys.ENABLE_STATUSBAR_BATTERY_PERCENT, prefs.getBoolean(PreferenceKeys.ENABLE_STATUSBAR_BATTERY_PERCENT, false))
            putExtra(PreferenceKeys.ENABLE_HIDE_VPN_ICON, prefs.getBoolean(PreferenceKeys.ENABLE_HIDE_VPN_ICON, false))
            putExtra(PreferenceKeys.NETWORK_TRAFFIC_INTERVAL, prefs.getInt(PreferenceKeys.NETWORK_TRAFFIC_INTERVAL, 1))
            putExtra(PreferenceKeys.NETWORK_TRAFFIC_FONT_SIZE, prefs.getFloat(PreferenceKeys.NETWORK_TRAFFIC_FONT_SIZE, 8f))
            putExtra(PreferenceKeys.NETWORK_TRAFFIC_THRESHOLD, prefs.getInt(PreferenceKeys.NETWORK_TRAFFIC_THRESHOLD, 1))

            putExtra(PreferenceKeys.ENABLE_MASTER_LOG, prefs.getBoolean(PreferenceKeys.ENABLE_MASTER_LOG, false))
            putExtra(PreferenceKeys.LOG_SECURITY, prefs.getBoolean(PreferenceKeys.LOG_SECURITY, true))
            putExtra(PreferenceKeys.LOG_INTERFACE, prefs.getBoolean(PreferenceKeys.LOG_INTERFACE, true))
            putExtra(PreferenceKeys.LOG_GESTURES, prefs.getBoolean(PreferenceKeys.LOG_GESTURES, true))
            putExtra(PreferenceKeys.LOG_QUICK_SETTINGS, prefs.getBoolean(PreferenceKeys.LOG_QUICK_SETTINGS, true))

            addFlags(0x01000000)
        }
        context.sendBroadcast(intent, null, makeBroadcastOptions())
    }

    /**
     * Sends a high-priority request to SystemUI to put device to sleep.
     *
     * @param context Host process Context used to send sleep broadcast.
     */
    @SuppressLint("WrongConstant")
    fun sendSleepRequest(context: Context) {
        val intent = Intent(ACTION_REQUEST_SLEEP).apply {
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND or 0x01000000)
        }
        context.sendBroadcast(intent, null, makeBroadcastOptions())
    }

    /**
     * Dispatches a broadcast for a single preference change to minimize IPC overhead.
     *
     * @param context Host process Context used to send the update broadcast.
     * @param key The setting key identifier defined in [PreferenceKeys].
     * @param value The primitive value (Boolean, Int, Float, Long) being updated.
     */
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
            addFlags(0x01000000)
        }
        context.sendBroadcast(intent, null, makeBroadcastOptions())
    }

    /**
     * Registers a receiver with multi-layer UID verification to prevent spoofing.
     *
     * @param context Application Context used to register the receiver.
     * @param moduleUid Module application UID.
     * @param extraActions Additional intent actions to listen for.
     * @param onVerifiedBroadcast Callback invoked when a broadcast passes UID verification.
     */
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
                    val senderUid = resolveSenderUid(this)
                    val senderAppId = if (senderUid > 0) senderUid % 100000 else -1
                    val moduleAppId = moduleUid % 100000
                    val myAppId = Process.myUid() % 100000
                    val pkgAppId = getModulePackageAppId(ctx)

                    val isTrusted = senderUid > 0 && (senderAppId == 0 || senderAppId == 1000 || 
                                    senderAppId == moduleAppId || senderAppId == myAppId || 
                                    (pkgAppId > 0 && senderAppId == pkgAppId))

                    Logger.d("Ipc", "Broadcast", "Action=${intent.action}, senderUid=$senderUid (AppId: $senderAppId), isTrusted=$isTrusted")

                    if (isTrusted) {
                        Logger.handleBroadcast(intent)
                        onVerifiedBroadcast(intent)
                    } else {
                        Logger.e("Ipc", "Blocked", "Unauthorized broadcast from UID: $senderUid (AppId: $senderAppId)")
                    }
                }
            }
            val targetContext = context.applicationContext ?: context
            targetContext.registerReceiver(receiver, filter, null, null, Context.RECEIVER_EXPORTED)
        } catch (t: Throwable) {
            Log.wtf("PXTK_Ipc", "CRITICAL: Receiver registration failed", t)
        }
    }

    /**
     * Specialized receiver for sleep requests with strict identity validation.
     * Validates sender UID against trusted launchers and system apps.
     */
    fun registerSleepReceiver(context: Context, moduleUid: Int, onReceive: () -> Unit) {
        try {
            val filter = IntentFilter(ACTION_REQUEST_SLEEP)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val senderUid = resolveSenderUid(this)
                    val senderAppId = if (senderUid > 0) senderUid % 100000 else -1
                    val moduleAppId = moduleUid % 100000
                    val myAppId = Process.myUid() % 100000
                    val pkgAppId = getModulePackageAppId(ctx)

                    val isTrusted = senderUid > 0 && (senderAppId == 0 || senderAppId == 1000 || 
                                    senderAppId == moduleAppId || senderAppId == myAppId || 
                                    (pkgAppId > 0 && senderAppId == pkgAppId) || run {
                        val trustedLaunchers = listOf("com.google.android.apps.nexuslauncher", "com.android.launcher3", "com.google.android.launcher")
                        trustedLaunchers.any { pkg ->
                            runCatching { context.packageManager.getPackageInfo(pkg, 0)?.applicationInfo?.uid?.rem(100000) }.getOrNull() == senderAppId
                        }
                    })

                    Logger.d("Ipc", "Sleep", "Request received: action=${intent.action}, senderUid=$senderUid (AppId: $senderAppId), isTrusted=$isTrusted")

                    if (isTrusted) {
                        onReceive()
                    } else {
                        Logger.e("Security", "Blocked", "Unauthorized sleep request from UID: $senderUid (AppId: $senderAppId)")
                    }
                }
            }
            val targetContext = context.applicationContext ?: context
            targetContext.registerReceiver(receiver, filter, null, null, Context.RECEIVER_EXPORTED)
        } catch (t: Throwable) {
            Log.wtf("PXTK_Ipc", "CRITICAL: Sleep receiver registration failed", t)
        }
    }
}
