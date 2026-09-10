package io.github.hohojia886.pixeltweaks.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.hohojia886.pixeltweaks.utils.IpcManager
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys

/**
 * BootReceiver: Seeds default DE preferences upon boot/unlock and
 * broadcasts full settings synchronization across all active module hooks.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED || 
            action == Intent.ACTION_USER_UNLOCKED) {
            
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

            // Seed DE defaults if keys are absent, and reset high-risk security bypasses
            prefs.edit().apply {
                if (!prefs.contains(PreferenceKeys.ENABLE_EASY_UNLOCK)) putBoolean(PreferenceKeys.ENABLE_EASY_UNLOCK, true)
                if (!prefs.contains(PreferenceKeys.ENABLE_EASY_UNLOCK_REBOOT)) putBoolean(PreferenceKeys.ENABLE_EASY_UNLOCK_REBOOT, false)
                if (!prefs.contains(PreferenceKeys.ENABLE_QS_WIFI_FIX)) putBoolean(PreferenceKeys.ENABLE_QS_WIFI_FIX, true)
                if (!prefs.contains(PreferenceKeys.ENABLE_QS_DATA_FIX)) putBoolean(PreferenceKeys.ENABLE_QS_DATA_FIX, true)
                if (!prefs.contains(PreferenceKeys.ENABLE_CLEAR_ALL)) putBoolean(PreferenceKeys.ENABLE_CLEAR_ALL, true)
                if (!prefs.contains(PreferenceKeys.ENABLE_NETWORK_TRAFFIC)) putBoolean(PreferenceKeys.ENABLE_NETWORK_TRAFFIC, true)
                if (!prefs.contains(PreferenceKeys.ENABLE_DT_LAUNCHER)) putBoolean(PreferenceKeys.ENABLE_DT_LAUNCHER, true)
                if (!prefs.contains(PreferenceKeys.ENABLE_DT_LOCKSCREEN)) putBoolean(PreferenceKeys.ENABLE_DT_LOCKSCREEN, true)
                if (!prefs.contains(PreferenceKeys.ENABLE_DT_STATUSBAR)) putBoolean(PreferenceKeys.ENABLE_DT_STATUSBAR, true)
                if (!prefs.contains(PreferenceKeys.ENABLE_CALL_RECORDING)) putBoolean(PreferenceKeys.ENABLE_CALL_RECORDING, false)
                if (!prefs.contains(PreferenceKeys.DISABLE_VOICE_ANNOUNCEMENT)) putBoolean(PreferenceKeys.DISABLE_VOICE_ANNOUNCEMENT, true)
                if (!prefs.contains(PreferenceKeys.DISABLE_CALL_NOTES_ANNOUNCEMENT)) putBoolean(PreferenceKeys.DISABLE_CALL_NOTES_ANNOUNCEMENT, true)

                // Security Reset
                putBoolean(PreferenceKeys.ALLOW_DOWNGRADE, false)
                putBoolean(PreferenceKeys.BYPASS_SIGNATURE, false)
                putLong(PreferenceKeys.DOWNGRADE_TIMESTAMP, 0L)
                putLong(PreferenceKeys.SIGNATURE_TIMESTAMP, 0L)
                putBoolean(PreferenceKeys.IS_FIRST_UNLOCK_DONE, false)
                apply()
            }
            
            // Actively broadcast full settings sync to SystemUI, Dialer, Launcher, and SystemServer
            IpcManager.syncAllSettings(context, prefs)
        }
    }
}
