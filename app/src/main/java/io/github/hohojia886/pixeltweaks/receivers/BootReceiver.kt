package io.github.hohojia886.pixeltweaks.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys
import io.github.hohojia886.pixeltweaks.utils.IpcManager

/**
 * Resets sensitive security bypasses upon system boot or user unlock.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED || 
            action == Intent.ACTION_USER_UNLOCKED) {
            
            val deContext = context.createDeviceProtectedStorageContext()
            val prefs = deContext.getSharedPreferences(IpcManager.PREF_NAME, Context.MODE_PRIVATE)
            
            // Security Reset: Disable high-risk bypasses on boot
            prefs.edit().apply {
                putBoolean(PreferenceKeys.ALLOW_DOWNGRADE, false)
                putBoolean(PreferenceKeys.BYPASS_SIGNATURE, false)
                putLong(PreferenceKeys.DOWNGRADE_TIMESTAMP, 0L)
                putLong(PreferenceKeys.SIGNATURE_TIMESTAMP, 0L)
                putBoolean(PreferenceKeys.IS_FIRST_UNLOCK_DONE, false)
                apply()
            }
            
            IpcManager.syncAllSettings(context, prefs)
        }
    }
}
