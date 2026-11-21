@file:Suppress("UNUSED_PARAMETER", "PrivateApi")

package io.github.hohojia886.pixeltweaks.hooks.`interface`

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.net.toUri
import io.github.hohojia886.pixeltweaks.hooks.`interface`.battery.BatteryPercentViewInjector
import io.github.hohojia886.pixeltweaks.hooks.`interface`.battery.BatteryStateController
import io.github.hohojia886.pixeltweaks.hooks.`interface`.battery.StockBatterySuppressor
import io.github.hohojia886.pixeltweaks.utils.ComposeRecompositionBridge
import io.github.hohojia886.pixeltweaks.utils.IpcDispatcher
import io.github.hohojia886.pixeltweaks.utils.IpcManager
import io.github.hohojia886.pixeltweaks.utils.Logger
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys
import io.github.hohojia886.pixeltweaks.utils.StatusBarTintManager
import io.github.hohojia886.pixeltweaks.utils.SystemVersion
import io.github.libxposed.api.XposedModule

/**
 * Facade coordinating standalone right-side status bar battery percentage replacement inside SystemUI.
 *
 * Delegates responsibilities across:
 * - [ComposeRecompositionBridge] for R8-resilient reactive Compose recomposition on Android 17 CP3A.
 * - [BatteryStateController] for battery intents, tinting, and charging rainbow pulse animation.
 * - [StockBatterySuppressor] for hiding stock battery icons across Compose and View pipelines.
 * - [BatteryPercentViewInjector] for right-side TextView injection and atomic same-frame visibility sync.
 */
object BatteryPercentHook {

    private const val TAG = "Battery"
    internal const val VIEW_TAG = "PX_RIGHT_BATTERY_PERCENT_TAG"

    @Volatile internal var isEnabled = false
    @Volatile internal var isAndroid17CP3A = false
    @Volatile private var processPackageName: String? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    internal val composeBridge = ComposeRecompositionBridge(TAG)

    internal val stateController: BatteryStateController = BatteryStateController(
        logTag = TAG,
        mainHandler = mainHandler,
        onTextChanged = { text, color -> viewInjector.updatePercentText(text, color) },
        onColorChanged = { color -> viewInjector.updatePercentTextColors(color) },
    )

    internal val suppressor: StockBatterySuppressor = StockBatterySuppressor(
        logTag = TAG,
        viewTag = VIEW_TAG,
        mainHandler = mainHandler,
        composeBridge = composeBridge,
        isEnabledProvider = { isEnabled },
        isCp3aProvider = { isAndroid17CP3A },
        onComposeFrameSync = { viewInjector.flushPendingVisibility() },
    )

    internal val viewInjector: BatteryPercentViewInjector = BatteryPercentViewInjector(
        logTag = TAG,
        viewTag = VIEW_TAG,
        mainHandler = mainHandler,
        isEnabledProvider = { isEnabled },
        isCp3aProvider = { isAndroid17CP3A },
        percentTextProvider = { stateController.currentPercentText },
        textColorProvider = { stateController.getEffectiveTextColor() },
        suppressor = suppressor,
    )

    /**
     * Main entry point to apply battery percentage hooks and UI injection inside SystemUI.
     *
     * @param module Active LSPosed [XposedModule] instance.
     * @param classLoader SystemUI ClassLoader.
     */
    fun hook(module: XposedModule, classLoader: ClassLoader) {
        processPackageName = "com.android.systemui"

        // Dynamically probe Android 17 system version:
        // - CP2A builds & CD1A builds before 260905: CP2A layout strategy (isAndroid17CP3A = false)
        // - CP3A builds: unified Compose layout strategy (isAndroid17CP3A = true)
        // - Other builds: fallback class probing for SingleBindableStatusBarComposeIconView
        val buildDisplay = SystemVersion.display
        isAndroid17CP3A = when {
            SystemVersion.isCp2a() || SystemVersion.isCd1aBefore260905() -> false
            SystemVersion.isCp3a() -> true
            else -> runCatching {
                classLoader.loadClass("com.android.systemui.statusbar.pipeline.shared.ui.view.SingleBindableStatusBarComposeIconView") != null
            }.getOrDefault(false)
        }
        Logger.i(TAG, "VersionProbe", "Resolved strategy: isAndroid17CP3A=$isAndroid17CP3A (display=$buildDisplay)")

        composeBridge.initialize(classLoader)
        syncSettings(module, classLoader)

        IpcDispatcher.addListener { intent ->
            handleBroadcast(intent)
        }

        IpcDispatcher.initializeOnce(module, classLoader) { app ->
            stateController.registerReceiver(app)
            val refreshed = IpcManager.loadPreferences(module, classLoader, processPackageName)
            if (refreshed.containsKey(PreferenceKeys.ENABLE_STATUSBAR_BATTERY_PERCENT)) {
                val newEnabled = refreshed.getBoolean(PreferenceKeys.ENABLE_STATUSBAR_BATTERY_PERCENT, false)
                if (newEnabled != isEnabled) {
                    isEnabled = newEnabled
                    Logger.i(TAG, "Sync", "Settings refreshed on Context ready: statusbarBatteryPercent=$isEnabled")
                    updateViewVisibility()
                }
            }
        }

        suppressor.installHooks(module, classLoader)
        viewInjector.installHooks(module, classLoader)

        StatusBarTintManager.register(module, classLoader) { tint ->
            stateController.applyTint(tint)
        }
    }

    /**
     * Synchronizes initial toggle preference from IpcManager, DE ContentProvider, or CE RemotePreferences fallback.
     *
     * @param module Active LSPosed [XposedModule] instance.
     * @param classLoader SystemUI ClassLoader.
     */
    private fun syncSettings(module: XposedModule, classLoader: ClassLoader) {
        val bundle = IpcManager.loadPreferences(module, classLoader, processPackageName)
        if (bundle.containsKey(PreferenceKeys.ENABLE_STATUSBAR_BATTERY_PERCENT)) {
            isEnabled = bundle.getBoolean(PreferenceKeys.ENABLE_STATUSBAR_BATTERY_PERCENT, false)
            Logger.i(TAG, "Sync", "Settings loaded via IpcManager: statusbarBatteryPercent=$isEnabled")
            refreshAllBatteryState()
            return
        }

        val loadedFromDe = runCatching {
            val ctx = IpcManager.getSafeContext(classLoader, processPackageName)
                ?: IpcManager.getSystemContext(classLoader)
                ?: return@runCatching false
            val uri = "content://io.github.hohojia886.pixeltweaks".toUri()
            val b = ctx.contentResolver.call(uri, "get", null, null) ?: return@runCatching false
            isEnabled = b.getBoolean(PreferenceKeys.ENABLE_STATUSBAR_BATTERY_PERCENT, false)
            Logger.i(TAG, "Sync", "Settings loaded from DE: statusbarBatteryPercent=$isEnabled")
            refreshAllBatteryState()
            true
        }.getOrDefault(false)

        if (!loadedFromDe) {
            runCatching {
                val prefs = module.getRemotePreferences(IpcManager.PREF_NAME)
                isEnabled = prefs.getBoolean(PreferenceKeys.ENABLE_STATUSBAR_BATTERY_PERCENT, false)
                Logger.i(TAG, "Sync", "Settings loaded from CE (fallback): statusbarBatteryPercent=$isEnabled")
                refreshAllBatteryState()
            }.onFailure { e ->
                Logger.e(TAG, "Error", "Failed to sync statusbarBatteryPercent settings", e)
            }
        }
    }

    /**
     * Responds to IPC setting broadcasts for dynamic toggling while filtering redundant unchanged syncs.
     *
     * @param intent Incoming IPC sync or single-setting broadcast intent.
     * @param appCtx Optional application context (retained for test compatibility).
     */
    internal fun handleBroadcast(intent: Intent, appCtx: Context? = null) {
        when (intent.action) {
            IpcManager.ACTION_SETTINGS_SYNC -> {
                val newEnabled = intent.getBooleanExtra(PreferenceKeys.ENABLE_STATUSBAR_BATTERY_PERCENT, false)
                if (newEnabled == isEnabled) {
                    Logger.d(TAG, "Sync", "Ignoring redundant full sync: statusbarBatteryPercent=$newEnabled")
                    return
                }
                isEnabled = newEnabled
                Logger.i(TAG, "Sync", "Full sync received: statusbarBatteryPercent=$isEnabled")
                updateViewVisibility()
            }
            IpcManager.ACTION_SETTING_CHANGED -> {
                val key = intent.getStringExtra(PreferenceKeys.EXTRA_KEY)
                if (key == PreferenceKeys.ENABLE_STATUSBAR_BATTERY_PERCENT) {
                    val newEnabled = intent.getBooleanExtra(PreferenceKeys.EXTRA_VALUE, false)
                    if (newEnabled == isEnabled) {
                        Logger.d(TAG, "Sync", "Ignoring redundant setting update: statusbarBatteryPercent=$newEnabled")
                        return
                    }
                    isEnabled = newEnabled
                    Logger.i(TAG, "Sync", "Setting [enable_statusbar_battery_percent] updated to $isEnabled")
                    updateViewVisibility()
                }
            }
        }
    }

    /**
     * Triggers an immediate refresh of both percentage TextView visibility and stock battery suppression.
     */
    internal fun updateViewVisibility() {
        viewInjector.updateViewVisibility(
            hasComposeBridge = composeBridge.isAvailable(),
            refreshStockBattery = { suppressor.refreshAll() },
        )
    }

    /**
     * Refreshes stock battery suppression and legacy status bar roots on the main thread during initial sync.
     */
    private fun refreshAllBatteryState() {
        viewInjector.runOnMainThread {
            suppressor.refreshAll()
            viewInjector.refreshStatusBarRootsIfLegacy()
        }
    }
}
