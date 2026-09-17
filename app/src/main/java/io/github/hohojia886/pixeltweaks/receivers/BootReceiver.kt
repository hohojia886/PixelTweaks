package io.github.hohojia886.pixeltweaks.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.hohojia886.pixeltweaks.utils.IpcManager
import io.github.hohojia886.pixeltweaks.utils.Logger
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys

/**
 * BootReceiver: Seeds default DE preferences upon boot/unlock/package-installation and
 * broadcasts full settings synchronization across all active module hooks.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED || 
            action == Intent.ACTION_USER_UNLOCKED ||
            action == Intent.ACTION_PACKAGE_ADDED ||
            action == Intent.ACTION_PACKAGE_REPLACED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            
            val deContext = context.createDeviceProtectedStorageContext()
            val prefs = deContext.getSharedPreferences(IpcManager.PREF_NAME, Context.MODE_PRIVATE)
            
            if (action == Intent.ACTION_USER_UNLOCKED) {
                // When user unlocks, CE storage is accessible. Sync CE preferences over to DE preferences to preserve user settings.
                val cePrefs = context.getSharedPreferences(IpcManager.PREF_NAME, Context.MODE_PRIVATE)
                if (cePrefs.all.isNotEmpty()) {
                    val editor = prefs.edit()
                    cePrefs.all.forEach { (k, v) ->
                        when (v) {
                            is Boolean -> editor.putBoolean(k, v)
                            is Int -> editor.putInt(k, v)
                            is Float -> editor.putFloat(k, v)
                            is Long -> editor.putLong(k, v)
                            is String -> editor.putString(k, v)
                        }
                    }
                    editor.apply()
                }
            }

            // Seed DE defaults if keys are absent
            if (!prefs.contains(PreferenceKeys.ENABLE_EASY_UNLOCK)) {
                prefs.edit().apply {
                    putBoolean(PreferenceKeys.ENABLE_EASY_UNLOCK, true)
                    putBoolean(PreferenceKeys.ENABLE_EASY_UNLOCK_REBOOT, false)
                    putBoolean(PreferenceKeys.ENABLE_QS_WIFI_FIX, true)
                    putBoolean(PreferenceKeys.ENABLE_QS_DATA_FIX, true)
                    putBoolean(PreferenceKeys.ENABLE_CLEAR_ALL, true)
                    putBoolean(PreferenceKeys.ENABLE_NETWORK_TRAFFIC, true)
                    putBoolean(PreferenceKeys.ENABLE_DT_LAUNCHER, true)
                    putBoolean(PreferenceKeys.ENABLE_DT_LOCKSCREEN, true)
                    putBoolean(PreferenceKeys.ENABLE_DT_STATUSBAR, true)

                    // Security Reset on boot
                    putBoolean(PreferenceKeys.ALLOW_DOWNGRADE, false)
                    putBoolean(PreferenceKeys.BYPASS_SIGNATURE, false)
                    putLong(PreferenceKeys.DOWNGRADE_TIMESTAMP, 0L)
                    putLong(PreferenceKeys.SIGNATURE_TIMESTAMP, 0L)
                    putBoolean(PreferenceKeys.IS_FIRST_UNLOCK_DONE, false)
                    apply()
                }
            }
            
            // Actively broadcast full settings sync to SystemUI, Launcher, and SystemServer
            IpcManager.syncAllSettings(context, prefs)
            Logger.i("BootReceiver", "Sync", "Self-healing sync performed for action: $action")
        }
    }
}
