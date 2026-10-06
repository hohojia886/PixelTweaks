package io.github.hohojia886.pixeltweaks.providers

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import io.github.hohojia886.pixeltweaks.utils.IpcManager
import io.github.hohojia886.pixeltweaks.utils.Logger
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys
import java.util.concurrent.ConcurrentHashMap

/**
 * RemotePrefProvider: A bridge between Credential-Encrypted (CE) and Device-Protected (DE) storage.
 * Provides a secure mechanism for hook processes (SystemUI, Dialer, SystemServer) to read/write module settings 
 * before the user has unlocked the device (FBE support).
 * 
 * Security & Multi-User Architecture:
 * 1. Thread-safe App ID caching via ConcurrentHashMap.newKeySet().
 * 2. Multi-user compatible via (uid % 100000) App ID resolution.
 * 3. Strict write whitelist filtering for external callers.
 */
class RemotePrefProvider : ContentProvider() {

    // Thread-safe cache for authorized component App IDs (uid % 100000)
    private val trustedAppIds = ConcurrentHashMap.newKeySet<Int>()
    private val TAG = "Security"

    // Whitelist of keys that external trusted callers (SystemUI, SystemServer UID 1000) are allowed to write.
    private val ALLOWED_EXTERNAL_WRITE_KEYS = setOf(
        PreferenceKeys.EXPECTED_PASS_LEN,
        PreferenceKeys.IS_FIRST_UNLOCK_DONE,
        PreferenceKeys.EXTRA_KEY,
        PreferenceKeys.EXTRA_VALUE,
        PreferenceKeys.EXTRA_BATTERY_CYCLES,
        PreferenceKeys.EXTRA_BATTERY_RATED,
        PreferenceKeys.EXTRA_BATTERY_ESTIMATED,
        PreferenceKeys.EXTRA_BATTERY_HEALTH_CAP_INDEX,
        PreferenceKeys.EXTRA_BATTERY_OVERALL_HEALTH,
        PreferenceKeys.EXTRA_BATTERY_TEMP,
        PreferenceKeys.EXTRA_BATTERY_RESISTANCE_AVG,
        PreferenceKeys.EXTRA_BATTERY_RESISTANCE_NOW,
        PreferenceKeys.EXTRA_BATTERY_HEALTH_IMP_INDEX,
        PreferenceKeys.EXTRA_BATTERY_SERIAL_NUMBER,
        PreferenceKeys.EXTRA_BATTERY_FIRST_USAGE,
        PreferenceKeys.EXTRA_BATTERY_AGE,
        PreferenceKeys.EXTRA_BATTERY_AAFV_OFFSET
    )

    override fun onCreate(): Boolean = true

    // Resolves and caches App IDs (uid % 100000) for core system components and specific app packages
    private fun updateTrustedAppIds() {
        if (trustedAppIds.isNotEmpty()) return
        val ctx = context ?: return
        val pm = ctx.packageManager
        val packages = listOf(
            "com.android.systemui", 
            "com.google.android.dialer", 
            "com.android.dialer",
            "com.google.android.apps.nexuslauncher",
            "com.google.android.launcher",
            "com.android.launcher3"
        )
        
        packages.forEach { pkg ->
            runCatching {
                pm.getPackageInfo(pkg, 0).applicationInfo?.uid?.let { uid ->
                    trustedAppIds.add(uid % 100000)
                }
            }
        }
    }

    // Handles incoming ContentProvider calls with strict UID/AppID-based access control
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val callingUid = Binder.getCallingUid()
        val callingAppId = callingUid % 100000
        val moduleAppId = Process.myUid() % 100000
        updateTrustedAppIds()

        val isModule = callingAppId == moduleAppId
        val isTrusted = callingAppId == 0 || callingAppId == 1000 || trustedAppIds.contains(callingAppId)

        // Write Authorization: Module writes everything; trusted callers write ONLY whitelisted keys
        if (method == "put") {
            if (isModule) {
                Logger.d(TAG, "Sync", "Allowed MODULE WRITE from UID: $callingUid")
                return handlePut(extras, filterKeys = false)
            }
            if (isTrusted) {
                Logger.d(TAG, "Sync", "Allowed TRUSTED EXTERNAL WRITE from UID: $callingUid (AppId: $callingAppId)")
                return handlePut(extras, filterKeys = true)
            }
            
            Logger.e(TAG, "Blocked", "Unauthorized WRITE from UID: $callingUid (AppId: $callingAppId)")
            return null
        }

        // Read Authorization: Whitelisted components & system components can read DE settings
        if (method == "get") {
            val isWhitelisted = isModule || isTrusted || callingAppId < 1000
            if (!isWhitelisted) {
                Logger.e(TAG, "Blocked", "Unauthorized READ from UID: $callingUid (AppId: $callingAppId)")
                return null
            }
            return handleGet()
        }

        return null
    }

    // Internal read logic that bundles DE SharedPreferences into a Bundle for IPC
    private fun handleGet(): Bundle {
        val ctx = context?.createDeviceProtectedStorageContext() ?: return Bundle()
        val prefs = ctx.getSharedPreferences(IpcManager.PREF_NAME, Context.MODE_PRIVATE)
        val bundle = Bundle()
        
        prefs.all.forEach { (key, value) ->
            when (value) {
                is Boolean -> bundle.putBoolean(key, value)
                is Int -> bundle.putInt(key, value)
                is Float -> bundle.putFloat(key, value)
                is Long -> bundle.putLong(key, value)
                is String -> bundle.putString(key, value)
            }
        }
        return bundle
    }

    // Internal write logic that persists data into DE storage
    private fun handlePut(extras: Bundle?, filterKeys: Boolean): Bundle {
        val ctx = context?.createDeviceProtectedStorageContext() ?: return Bundle()
        val prefs = ctx.getSharedPreferences(IpcManager.PREF_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        extras?.keySet()?.forEach { key ->
            if (!filterKeys || key in ALLOWED_EXTERNAL_WRITE_KEYS) {
                when (val value = extras.get(key)) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Long -> editor.putLong(key, value)
                    is String -> editor.putString(key, value)
                }
            } else {
                Logger.e(TAG, "BlockedKey", "Blocked external write to restricted key: $key")
            }
        }
        editor.apply()
        return Bundle().apply { putBoolean("success", true) }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
