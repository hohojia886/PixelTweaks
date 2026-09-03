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

/**
 * RemotePrefProvider: A bridge between Credential-Encrypted (CE) and Device-Protected (DE) storage.
 * Provides a secure mechanism for hook processes (SystemUI, Dialer) to read/write module settings 
 * before the user has unlocked the device (FBE support).
 */
class RemotePrefProvider : ContentProvider() {

    private val trustedUids = mutableSetOf<Int>() // Cache for authorized component UIDs
    private val TAG = "Security"

    override fun onCreate(): Boolean = true

    // Resolves and caches UIDs for core system components and specific app packages
    private fun updateTrustedUids() {
        if (trustedUids.isNotEmpty()) return
        val ctx = context ?: return
        val pm = ctx.packageManager
        val packages = listOf("com.android.systemui", "com.google.android.dialer", "com.android.dialer")
        
        packages.forEach { pkg ->
            runCatching {
                pm.getPackageInfo(pkg, 0).applicationInfo?.uid?.let { trustedUids.add(it) }
            }
        }
    }

    // Handles incoming ContentProvider calls with strict UID-based access control
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val callingUid = Binder.getCallingUid()
        updateTrustedUids()

        // Write Authorization: Restricted to the module itself and trusted system components
        if (method == "put") {
            val isModule = callingUid == Process.myUid()
            val isTrusted = callingUid == 1000 || trustedUids.contains(callingUid)
            
            if (isModule || isTrusted) {
                Logger.d(TAG, "Sync", "Allowed WRITE from UID: $callingUid")
                return handlePut(extras)
            }
            
            Logger.e(TAG, "Blocked", "Unauthorized WRITE from UID: $callingUid")
            return null
        }

        // Read Authorization: Allows whitelisted components to access the synchronized settings
        if (method == "get") {
            val isWhitelisted = callingUid < 1000 || trustedUids.contains(callingUid)
            if (!isWhitelisted) {
                Logger.w(TAG, "Warning", "UID $callingUid is reading prefs without whitelist")
            }
            return handleGet(isWhitelisted)
        }

        return null
    }

    // Internal read logic that bundles DE SharedPreferences into a Bundle for IPC
    private fun handleGet(fullAccess: Boolean): Bundle {
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
    private fun handlePut(extras: Bundle?): Bundle {
        val ctx = context?.createDeviceProtectedStorageContext() ?: return Bundle()
        val prefs = ctx.getSharedPreferences(IpcManager.PREF_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        
        extras?.keySet()?.forEach { key ->
            when (val value = extras.get(key)) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Float -> editor.putFloat(key, value)
                is Long -> editor.putLong(key, value)
                is String -> editor.putString(key, value)
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
