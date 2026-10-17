package io.github.hohojia886.pixeltweaks.providers

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import androidx.core.content.edit
import io.github.hohojia886.pixeltweaks.utils.IpcManager
import io.github.hohojia886.pixeltweaks.utils.Logger
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys
import java.util.concurrent.ConcurrentHashMap

/**
 * Bridge between Credential-Encrypted and Device-Protected storage.
 * Secures IPC preference reads and writes before device unlock.
 */
class RemotePrefProvider : ContentProvider() {

    private val trustedAppIds = ConcurrentHashMap.newKeySet<Int>()

    companion object {
        private const val TAG = "Security"

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
    }

    /**
     * Initializes the content provider during process startup.
     * Returns true indicating successful provider initialization.
     */
    override fun onCreate(): Boolean = true

    /**
     * Resolves and caches App IDs for authorized components.
     * Queries package manager for SystemUI and launcher UIDs.
     */
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

    /**
     * Handles incoming ContentProvider IPC method calls (`get` / `put`) with UID-based access control.
     *
     * @param method Method string (`get` for reading DE storage, `put` for writing DE storage).
     * @param arg Optional string argument.
     * @param extras [Bundle] containing payload data for `put` operations.
     * @return A [Bundle] containing results, or null if the calling process is unauthorized.
     */
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val callingUid = Binder.getCallingUid()
        val callingAppId = callingUid % 100000
        val moduleAppId = Process.myUid() % 100000
        updateTrustedAppIds()

        val isModule = callingAppId == moduleAppId
        val isTrusted = callingAppId == 0 || callingAppId == 1000 || trustedAppIds.contains(callingAppId)

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

    /**
     * Internal read logic bundling DE preferences for IPC.
     * Reads all device protected storage entries into Bundle.
     */
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

    /**
     * Internal write logic persisting input bundle into DE storage.
     * Writes whitelisted extras into device protected storage.
     */
    @Suppress("DEPRECATION")
    private fun handlePut(extras: Bundle?, filterKeys: Boolean): Bundle {
        val ctx = context?.createDeviceProtectedStorageContext() ?: return Bundle()
        val prefs = ctx.getSharedPreferences(IpcManager.PREF_NAME, Context.MODE_PRIVATE)
        
        prefs.edit {
            extras?.keySet()?.forEach { key ->
                if (!filterKeys || key in ALLOWED_EXTERNAL_WRITE_KEYS) {
                    when (val value = extras.get(key)) {
                        is Boolean -> putBoolean(key, value)
                        is Int -> putInt(key, value)
                        is Float -> putFloat(key, value)
                        is Long -> putLong(key, value)
                        is String -> putString(key, value)
                    }
                } else {
                    Logger.e(TAG, "Blocked", "Blocked external write to restricted key: $key")
                }
            }
        }
        return Bundle().apply { putBoolean("success", true) }
    }

    /**
     * Query operation unsupported by this preference provider.
     * Always returns null as database queries are not implemented.
     */
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null

    /**
     * getType operation unsupported by this preference provider.
     * Always returns null as MIME types are not implemented.
     */
    override fun getType(uri: Uri): String? = null

    /**
     * Insert operation unsupported by this preference provider.
     * Always returns null as database inserts are not implemented.
     */
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    /**
     * Delete operation unsupported by this preference provider.
     * Always returns zero as database deletes are not implemented.
     */
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    /**
     * Update operation unsupported by this preference provider.
     * Always returns zero as database updates are not implemented.
     */
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
