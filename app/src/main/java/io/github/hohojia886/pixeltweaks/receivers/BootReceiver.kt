package io.github.hohojia886.pixeltweaks.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import io.github.hohojia886.pixeltweaks.utils.IpcManager
import io.github.hohojia886.pixeltweaks.utils.Logger
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys

/**
 * BroadcastReceiver responsible for seeding default preferences and executing Direct Boot sync upon system boot.
 * Resets security switches on boot and triggers a full settings synchronization IPC broadcast to active hooks.
 */
class BootReceiver : BroadcastReceiver() {
    /**
     * Handles system boot completed, unlock, and package modification intent broadcasts.
     *
     * @param context Host process Context.
     * @param intent Received broadcast [Intent] containing target action.
     */
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        
        if ((action == Intent.ACTION_BOOT_COMPLETED) ||
            (action == Intent.ACTION_LOCKED_BOOT_COMPLETED) || 
            (action == Intent.ACTION_USER_UNLOCKED) ||
            (action == Intent.ACTION_PACKAGE_ADDED) ||
            (action == Intent.ACTION_PACKAGE_REPLACED) ||
            (action == Intent.ACTION_MY_PACKAGE_REPLACED)) {
            
            val deContext = context.createDeviceProtectedStorageContext()
            val prefs = deContext.getSharedPreferences(IpcManager.PREF_NAME, Context.MODE_PRIVATE)
            val cePrefs = runCatching { context.getSharedPreferences(IpcManager.PREF_NAME, Context.MODE_PRIVATE) }.getOrNull()

            if ((action == Intent.ACTION_BOOT_COMPLETED) || (action == Intent.ACTION_LOCKED_BOOT_COMPLETED)) {
                prefs.edit(commit = true) {
                    putBoolean(PreferenceKeys.ALLOW_DOWNGRADE, false)
                    putBoolean(PreferenceKeys.BYPASS_SIGNATURE, false)
                    putLong(PreferenceKeys.DOWNGRADE_TIMESTAMP, 0L)
                    putLong(PreferenceKeys.SIGNATURE_TIMESTAMP, 0L)
                    putBoolean(PreferenceKeys.IS_FIRST_UNLOCK_DONE, false)
                }
                cePrefs?.edit(commit = true) {
                    putBoolean(PreferenceKeys.ALLOW_DOWNGRADE, false)
                    putBoolean(PreferenceKeys.BYPASS_SIGNATURE, false)
                    putLong(PreferenceKeys.DOWNGRADE_TIMESTAMP, 0L)
                    putLong(PreferenceKeys.SIGNATURE_TIMESTAMP, 0L)
                    putBoolean(PreferenceKeys.IS_FIRST_UNLOCK_DONE, false)
                }
            }
            
            if ((action == Intent.ACTION_USER_UNLOCKED) && (cePrefs != null)) {
                if (cePrefs.all.isNotEmpty()) {
                    prefs.edit(commit = true) {
                        cePrefs.all.forEach { (k, v) ->
                            when (v) {
                                is Boolean -> putBoolean(k, v)
                                is Int -> putInt(k, v)
                                is Float -> putFloat(k, v)
                                is Long -> putLong(k, v)
                                is String -> putString(k, v)
                            }
                        }
                        putBoolean(PreferenceKeys.ALLOW_DOWNGRADE, false)
                        putBoolean(PreferenceKeys.BYPASS_SIGNATURE, false)
                        putLong(PreferenceKeys.DOWNGRADE_TIMESTAMP, 0L)
                        putLong(PreferenceKeys.SIGNATURE_TIMESTAMP, 0L)
                        putBoolean(PreferenceKeys.IS_FIRST_UNLOCK_DONE, false)
                    }
                }
            }

            if (!prefs.contains(PreferenceKeys.ENABLE_EASY_UNLOCK)) {
                prefs.edit(commit = true) {
                    putBoolean(PreferenceKeys.ENABLE_EASY_UNLOCK, true)
                    putBoolean(PreferenceKeys.ENABLE_EASY_UNLOCK_REBOOT, false)
                    putBoolean(PreferenceKeys.ENABLE_QS_WIFI_FIX, true)
                    putBoolean(PreferenceKeys.ENABLE_QS_DATA_FIX, true)
                    putBoolean(PreferenceKeys.ENABLE_CLEAR_ALL, true)
                    putBoolean(PreferenceKeys.ENABLE_TABLET_MODE, false)
                    putBoolean(PreferenceKeys.ENABLE_BATTERY_INFO, false)
                    putBoolean(PreferenceKeys.ENABLE_NETWORK_TRAFFIC, true)
                    putBoolean(PreferenceKeys.ENABLE_STATUSBAR_BATTERY_PERCENT, false)
                    putBoolean(PreferenceKeys.ENABLE_HIDE_VPN_ICON, false)
                    putBoolean(PreferenceKeys.ENABLE_DT_LAUNCHER, true)
                    putBoolean(PreferenceKeys.ENABLE_DT_LOCKSCREEN, true)
                    putBoolean(PreferenceKeys.ENABLE_DT_STATUSBAR, true)

                    putBoolean(PreferenceKeys.ALLOW_DOWNGRADE, false)
                    putBoolean(PreferenceKeys.BYPASS_SIGNATURE, false)
                    putLong(PreferenceKeys.DOWNGRADE_TIMESTAMP, 0L)
                    putLong(PreferenceKeys.SIGNATURE_TIMESTAMP, 0L)
                    putBoolean(PreferenceKeys.IS_FIRST_UNLOCK_DONE, false)
                }
            }
            
            IpcManager.syncAllSettings(context, prefs)
            Logger.i("BootReceiver", "Sync", "Self-healing sync performed for action: $action")
        }
    }
}
