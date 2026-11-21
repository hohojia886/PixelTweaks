@file:Suppress("DiscouragedPrivateApi", "PrivateApi")

package io.github.hohojia886.pixeltweaks.hooks.security

import android.content.Context
import android.os.IBinder
import android.provider.Settings
import android.view.Display
import io.github.hohojia886.pixeltweaks.utils.Logger
import java.lang.reflect.Method

/**
 * Manages forced display density switching for Tablet Mode inside `system_server`.
 *
 * Uses reflective `IWindowManager` calls (`setForcedDisplayDensityForUser` / `clearForcedDisplayDensityForUser`)
 * with a `Settings.Secure` (`display_density_forced`) fallback, while preventing redundant full-sync overwrites.
 */
internal object TabletModeDpiController {

    private const val TAG = "Density"

    @Volatile internal var appliedTabletMode: Boolean? = null
    @Volatile private var cachedWms: Any? = null
    @Volatile private var cachedSetDpiMethod: Method? = null
    @Volatile private var cachedClearDpiMethod: Method? = null

    /**
     * Determines the appropriate action for forced display density configuration.
     * Prevents race conditions during full settings synchronization where an unchanged tablet mode
     * setting might unintentionally overwrite manual display density changes or reset display scale.
     *
     * @param isFullSync True if triggered by a full settings sync broadcast, false if triggered by an individual toggle change.
     * @param requested The desired state of the tablet mode toggle from preferences.
     * @param applied The last successfully applied tablet mode state in the current process, or null if uninitialized.
     * @return `true` to apply tablet density, `false` to clear forced density back to default, or `null` if no action is needed.
     */
    internal fun decideDensityAction(isFullSync: Boolean, requested: Boolean, applied: Boolean?): Boolean? {
        if (!isFullSync) return requested
        if (requested && applied != true) return true
        if (!requested && applied == true) return false
        return null
    }

    /**
     * Evaluates the requested tablet mode state and applies display density changes only when necessary.
     *
     * @param context SystemServer [Context] used to read display metrics and update settings.
     * @param isFullSync True if invoked from an initial or full-sync broadcast; false for an explicit user toggle.
     * @param requested Desired tablet mode toggle value.
     */
    fun applyIfNeeded(context: Context, isFullSync: Boolean, requested: Boolean) {
        val action = decideDensityAction(isFullSync, requested, appliedTabletMode) ?: return
        updateSystemServerDensity(context, action)
    }

    /**
     * Obtains the core WindowManager binder instance using ServiceManager.
     * Reflectively calls `getService("window")` and converts IBinder to `IWindowManager`.
     *
     * @return Cached `IWindowManager` interface proxy, or null if unavailable.
     */
    private fun getIWindowManager(): Any? {
        if (cachedWms != null) return cachedWms
        return runCatching {
            val smClass = Class.forName("android.os.ServiceManager")
            val getService = smClass.getDeclaredMethod("getService", String::class.java)
            val binder = getService.invoke(null, "window") as IBinder
            val iwmClass = Class.forName("android.view.IWindowManager" + '$' + "Stub")
            val asInterface = iwmClass.getDeclaredMethod("asInterface", IBinder::class.java)
            val wms = asInterface.invoke(null, binder)
            cachedWms = wms
            wms
        }.getOrNull()
    }

    /**
     * Configures system density settings for tablet-like interfaces.
     * Calls `setForcedDisplayDensityForUser` / `clearForcedDisplayDensityForUser` or falls back to `Settings.Secure`.
     *
     * @param context SystemServer [Context] for display metrics and ContentResolver fallback.
     * @param enabled True to apply 600dp tablet density; false to restore default display density.
     */
    private fun updateSystemServerDensity(context: Context, enabled: Boolean) {
        runCatching {
            val wms = getIWindowManager()
            if (wms != null) {
                if (enabled) {
                    val dm = context.resources.displayMetrics
                    val minPx = minOf(dm.widthPixels, dm.heightPixels)
                    val targetDpi = (minPx * 160) / 600
                    if (cachedSetDpiMethod == null) {
                        cachedSetDpiMethod = wms.javaClass.methods.firstOrNull { it.name == "setForcedDisplayDensityForUser" }
                    }
                    if (cachedSetDpiMethod != null) {
                        cachedSetDpiMethod!!.invoke(wms, Display.DEFAULT_DISPLAY, targetDpi, -2)
                        appliedTabletMode = true
                        Logger.i(TAG, "Success", "Successfully called setForcedDisplayDensityForUser(DEFAULT_DISPLAY, $targetDpi, USER_CURRENT)")
                        return
                    }
                } else {
                    if (cachedClearDpiMethod == null) {
                        cachedClearDpiMethod = wms.javaClass.methods.firstOrNull { it.name == "clearForcedDisplayDensityForUser" }
                    }
                    if (cachedClearDpiMethod != null) {
                        cachedClearDpiMethod!!.invoke(wms, Display.DEFAULT_DISPLAY, -2)
                        appliedTabletMode = false
                        Logger.i(TAG, "Success", "Successfully called clearForcedDisplayDensityForUser(DEFAULT_DISPLAY, USER_CURRENT)")
                        return
                    }
                }
            }

            if (enabled) {
                val dm = context.resources.displayMetrics
                val minPx = minOf(dm.widthPixels, dm.heightPixels)
                val targetDpi = (minPx * 160) / 600
                Settings.Secure.putInt(context.contentResolver, "display_density_forced", targetDpi)
                appliedTabletMode = true
                Logger.i(TAG, "Active", "Fallback: Set display_density_forced = $targetDpi")
            } else {
                Settings.Secure.putString(context.contentResolver, "display_density_forced", null)
                appliedTabletMode = false
                Logger.i(TAG, "Active", "Fallback: Cleared display_density_forced")
            }
        }.onFailure { e ->
            Logger.e(TAG, "Error", "Failed to update display density in SystemServer", e)
        }
    }
}
