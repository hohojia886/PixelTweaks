@file:Suppress("DiscouragedPrivateApi", "PrivateApi", "DiscouragedApi", "UsePropertyAccessSyntax", "RedundantInterpolationPrefix", "UNUSED_PARAMETER")

package io.github.hohojia886.pixeltweaks.hooks.`interface`

import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.core.view.isNotEmpty
import androidx.core.view.isVisible
import io.github.hohojia886.pixeltweaks.utils.BatteryColorManager
import io.github.hohojia886.pixeltweaks.utils.IpcDispatcher
import io.github.hohojia886.pixeltweaks.utils.SystemVersion
import io.github.hohojia886.pixeltweaks.utils.IpcManager
import io.github.hohojia886.pixeltweaks.utils.Logger
import io.github.hohojia886.pixeltweaks.utils.PreferenceKeys
import io.github.hohojia886.pixeltweaks.utils.StatusBarTintManager
import io.github.hohojia886.pixeltweaks.utils.hookAfter
import io.github.hohojia886.pixeltweaks.utils.hookBefore
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.util.Collections
import java.util.WeakHashMap

/**
 * Renders a standalone text battery percentage indicator at the far-right of the status bar in SystemUI.
 *
 * Supports both legacy (Android 17 CP2A.*.*) and modern Compose-based (Android 17 CP3A.*.*) SystemUI architectures.
 * Dynamically hides the stock graphic battery icon and copies status bar Clock font styles.
 */
object BatteryPercentHook {

    private const val TAG = "Battery"
    private const val VIEW_TAG = "PX_RIGHT_BATTERY_PERCENT_TAG"

    @Volatile private var isEnabled = false
    @Volatile private var processPackageName: String? = null
    @Volatile private var currentPercentText = ""
    @Volatile private var currentTint = Color.WHITE

    @Volatile private var currentLevel = 100
    @Volatile private var isCharging = false
    @Volatile private var isPowerSaveMode = false
    @Volatile private var isScreenOn = true
    @Volatile private var animHue = 0f
    private var pulseAnimator: ValueAnimator? = null

    private val stockBatteryViews = Collections.synchronizedMap(WeakHashMap<View, Boolean>())

    /**
     * Marks a given View as a stock battery component in map.
     * Records view in synchronized map to track stock components.
     */
    private fun markAsStockBatteryView(view: View) {
        stockBatteryViews[view] = true
    }

    /**
     * Checks if a given View is marked as a stock battery component.
     * Returns true if view exists in stock battery component map.
     */
    private fun isStockBatteryView(view: View): Boolean {
        return stockBatteryViews[view] == true
    }

    private val trackedPercentTextViews = Collections.synchronizedList(mutableListOf<WeakReference<TextView>>())
    private val mainHandler = Handler(Looper.getMainLooper())

    private val trackedStatusIconContainers = Collections.synchronizedList(mutableListOf<WeakReference<ViewGroup>>())
    private val trackedComposeBatteryViews = Collections.synchronizedList(mutableListOf<WeakReference<View>>())
    private val trackedModernBatteryViews = Collections.synchronizedList(mutableListOf<WeakReference<View>>())

    private val originalPaddingEndMap = Collections.synchronizedMap(WeakHashMap<ViewGroup, Int>())

    @Volatile private var cachedClassLoader: ClassLoader? = null
    
    @Volatile private var isAndroid17CP3A = false

    /**
     * Main entry point to apply battery percentage hooks and UI injection inside SystemUI.
     *
     * @param module Active LSPosed [XposedModule] instance.
     * @param classLoader SystemUI ClassLoader.
     */
    fun hook(module: XposedModule, classLoader: ClassLoader) {
        cachedClassLoader = classLoader
        processPackageName = "com.android.systemui"
        
        /**
         * Dynamically probe Android 17 system version using [SystemVersion]:
         * - CP2A builds & CD1A builds before 260905: Use CP2A layout strategy (isAndroid17CP3A = false)
         * - CP3A builds: Use unified Compose layout strategy (isAndroid17CP3A = true)
         * - Other builds: Fallback to class probing for SingleBindableStatusBarComposeIconView
         */
        val buildDisplay = SystemVersion.display

        isAndroid17CP3A = when {
            SystemVersion.isCp2a() || SystemVersion.isCd1aBefore260905() -> false
            SystemVersion.isCp3a() -> true
            else -> runCatching { 
                classLoader.loadClass("com.android.systemui.statusbar.pipeline.shared.ui.view.SingleBindableStatusBarComposeIconView") != null
            }.getOrDefault(false)
        }
        Logger.i(TAG, "VersionProbe", "Resolved strategy: isAndroid17CP3A=$isAndroid17CP3A (display=$buildDisplay)")

        syncSettings(module, classLoader)

        IpcDispatcher.addListener { intent ->
            handleBroadcast(intent)
        }

        IpcDispatcher.initializeOnce(module, classLoader) { app ->
            registerBatteryReceiver(app)
        }

        applyModernStatusBarViewBatteryHooks(module, classLoader)

        applySingleBindableIconViewBatteryHooks(module, classLoader)

        applyAndroid17ComposeBatteryHooks(module, classLoader)

        applyComposeUnifiedBatteryModifierHooks(module, classLoader)

        applyViewInjectionHooks(module, classLoader)

        applyStatusIconContainerHooks(module, classLoader)

        StatusBarTintManager.register(module, classLoader) { tint ->
            applyTint(tint)
        }
    }

    /**
     * Loads settings from DE or CE storage fallback.
     * Queries ContentProvider or RemotePreferences for percentage toggle.
     */
    private fun syncSettings(module: XposedModule, classLoader: ClassLoader) {
        val loadedFromDe = runCatching {
            val ctx = IpcManager.getSafeContext(classLoader, processPackageName) ?: IpcManager.getSystemContext(classLoader) ?: return@runCatching false
            val uri = "content://io.github.hohojia886.pixeltweaks".toUri()
            val bundle = ctx.contentResolver.call(uri, "get", null, null) ?: return@runCatching false
            isEnabled = bundle.getBoolean(PreferenceKeys.ENABLE_STATUSBAR_BATTERY_PERCENT, false)
            Logger.i(TAG, "Sync", "Settings loaded from DE: statusbarBatteryPercent=$isEnabled")
            updateAllBatteryVisibility()
            true
        }.getOrDefault(false)

        if (!loadedFromDe) {
            runCatching {
                val prefs = module.getRemotePreferences(IpcManager.PREF_NAME)
                isEnabled = prefs.getBoolean(PreferenceKeys.ENABLE_STATUSBAR_BATTERY_PERCENT, false)
                Logger.i(TAG, "Sync", "Settings loaded from CE (fallback): statusbarBatteryPercent=$isEnabled")
                updateAllBatteryVisibility()
            }.onFailure { e ->
                Logger.e(TAG, "Error", "Failed to sync statusbarBatteryPercent settings", e)
            }
        }
    }

    /**
     * Responds to IPC setting broadcasts for dynamic toggling.
     * Processes full sync and setting changed intents to update UI.
     */
    private fun handleBroadcast(intent: Intent, @Suppress("UNUSED_PARAMETER") appCtx: Context? = null) {
        when (intent.action) {
            IpcManager.ACTION_SETTINGS_SYNC -> {
                isEnabled = intent.getBooleanExtra(PreferenceKeys.ENABLE_STATUSBAR_BATTERY_PERCENT, false)
                Logger.i(TAG, "Sync", "Full sync received: statusbarBatteryPercent=$isEnabled")
                updateViewVisibility()
                updateAllBatteryVisibility()
            }
            IpcManager.ACTION_SETTING_CHANGED -> {
                val key = intent.getStringExtra(PreferenceKeys.EXTRA_KEY)
                if (key == PreferenceKeys.ENABLE_STATUSBAR_BATTERY_PERCENT) {
                    isEnabled = intent.getBooleanExtra(PreferenceKeys.EXTRA_VALUE, false)
                    Logger.i(TAG, "Sync", "Setting [enable_statusbar_battery_percent] updated to $isEnabled")
                    updateViewVisibility()
                    updateAllBatteryVisibility()
                }
            }
        }
    }

    /**
     * Hooks ModernStatusBarView on Android 17 for battery slot visibility.
     * Intercepts initView and setVisibleState methods for slot="battery".
     */
    private fun applyModernStatusBarViewBatteryHooks(module: XposedModule, classLoader: ClassLoader) {
        runCatching {
            val modernViewClass = classLoader.loadClass("com.android.systemui.statusbar.pipeline.shared.ui.view.ModernStatusBarView")

            modernViewClass.declaredMethods.filter { it.name == "initView" }.forEach { method ->
                module.hookAfter(method) { chain, _ ->
                    runCatching {
                        val view = chain.thisObject as? View ?: return@runCatching
                        val slot = chain.args.getOrNull(0)?.toString() ?: ""
                        if (slot == "battery" || slot == "status_bar_battery") {
                            markAsStockBatteryView(view)
                            synchronized(trackedModernBatteryViews) {
                                if (trackedModernBatteryViews.none { it.get() == view }) {
                                    trackedModernBatteryViews.add(WeakReference(view))
                                }
                            }
                            applyVisibilityToModernBatteryView(view)
                        }
                    }.onFailure { e ->
                        Logger.e(TAG, "ModernBattery", "Failed during initView hook processing", e)
                    }
                }
            }

            modernViewClass.declaredMethods.filter { it.name == "setVisibleState" }.forEach { method ->
                module.hookBefore(method) { chain ->
                    if (isEnabled) {
                        val view = chain.thisObject as? View ?: return@hookBefore
                        if (isStockBatteryView(view)) {
                            chain.args[0] = 2
                        }
                    }
                }
            }
        }.onFailure { e ->
            Logger.e(TAG, "ModernBattery", "ModernStatusBarView hook failed", e)
        }
    }

    /**
     * Hooks Compose Icon Views for the CP3A strategy.
     * Intercepts initView method for SingleBindableStatusBarComposeIconView.
     */
    private fun applySingleBindableIconViewBatteryHooks(module: XposedModule, classLoader: ClassLoader) {
        val classNames = listOf(
            "com.android.systemui.statusbar.pipeline.shared.ui.view.SingleBindableStatusBarComposeIconView",
            "com.android.systemui.statusbar.pipeline.shared.ui.view.SingleBindableStatusBarIconView",
            "com.android.systemui.statusbar.pipeline.shared.ui.view.ModernStatusBarView"
        )

        classNames.forEach { className ->
            runCatching {
                val clazz = classLoader.loadClass(className)

                clazz.declaredMethods.filter { it.name == "initView" }.forEach { method ->
                    module.hookBefore(method) { chain ->
                        runCatching {
                            val view = chain.thisObject as? View ?: return@runCatching
                            val slot = chain.args.getOrNull(0)?.toString() ?: ""

                            if (slot == "battery" || slot == "status_bar_battery") {
                                markAsStockBatteryView(view)
                                applyVisibilityToModernBatteryView(view)
                                applyVisibilityToComposeView(view)
                            }
                        }.onFailure { e ->
                            Logger.e(TAG, "ComposeBattery", "Failed during SingleBindableIconView initView hook", e)
                        }
                    }
                }
            }.onFailure { e ->
                Logger.e(TAG, "ComposeBattery", "Failed to hook $className", e)
            }
        }
    }

    /**
     * Updates visibility and dimensions for Modern Battery View.
     * Invokes setVisibleState and sets layout parameters accordingly.
     */
    private fun applyVisibilityToModernBatteryView(view: View) {
        mainHandler.post {
            runCatching {
                val hide = isEnabled

                runCatching {
                    val setVisibleStateMethod = view.javaClass.getMethod("setVisibleState", Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                    setVisibleStateMethod.invoke(view, if (hide) 2 else 0, false)
                }

                view.visibility = if (hide) View.GONE else View.VISIBLE
                view.alpha = if (hide) 0f else 1f

                val lp = view.layoutParams
                if (lp != null) {
                    lp.width = if (hide) 0 else ViewGroup.LayoutParams.WRAP_CONTENT
                    lp.height = if (hide) 0 else ViewGroup.LayoutParams.WRAP_CONTENT
                    view.layoutParams = lp
                }

                view.requestLayout()
                view.invalidate()

                (view.parent as? View)?.let { parent ->
                    parent.requestLayout()
                    parent.invalidate()
                }
            }
        }
    }

    /**
     * Starts tracking a Compose Battery view if not already cached.
     * Adds reference to weak list and applies initial visibility state.
     */
    private fun trackComposeBatteryView(view: View) {
        if (view.tag == VIEW_TAG) return
        if (!isBatteryViewCandidate(view)) {
            return
        }
        markAsStockBatteryView(view)
        synchronized(trackedComposeBatteryViews) {
            if (trackedComposeBatteryViews.none { it.get() == view }) {
                trackedComposeBatteryViews.add(WeakReference(view))
            }
        }
        applyVisibilityToComposeView(view)
    }

    /**
     * Hides outer and inner elements of Compose-based battery UI.
     * Manages alpha, scale, and layout params for compose battery views.
     */
    private fun applyVisibilityToComposeView(view: View) {
        if (isAndroid17CP3A) {
            return
        }

        mainHandler.post {
            runCatching {
                val hide = isEnabled

                view.visibility = if (hide) View.GONE else View.VISIBLE
                view.alpha = if (hide) 0f else 1f
                view.scaleX = if (hide) 0f else 1f
                view.scaleY = if (hide) 0f else 1f

                val lp = view.layoutParams
                if (lp != null) {
                    lp.width = if (hide) 0 else ViewGroup.LayoutParams.WRAP_CONTENT
                    lp.height = if (hide) 0 else ViewGroup.LayoutParams.WRAP_CONTENT
                    view.layoutParams = lp
                }

                if (hide) {
                    view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                        if (isEnabled && isStockBatteryView(v)) {
                            if (v.visibility != View.GONE || v.alpha != 0f) {
                                v.visibility = View.GONE
                                v.alpha = 0f
                                v.scaleX = 0f
                                v.scaleY = 0f
                                val currentLp = v.layoutParams
                                if (currentLp != null && currentLp.width != 0) {
                                    currentLp.width = 0
                                    currentLp.height = 0
                                    v.layoutParams = currentLp
                                }
                            }
                        }
                    }
                }

                if (view is ViewGroup && view.isNotEmpty()) {
                    for (i in 0 until view.childCount) {
                        val child = view.getChildAt(i) ?: continue
                        markAsStockBatteryView(child)
                        child.visibility = if (hide) View.GONE else View.VISIBLE
                        child.alpha = if (hide) 0f else 1f
                        child.scaleX = if (hide) 0f else 1f
                        child.scaleY = if (hide) 0f else 1f
                        val childLp = child.layoutParams
                        if (childLp != null) {
                            childLp.width = if (hide) 0 else ViewGroup.LayoutParams.WRAP_CONTENT
                            childLp.height = if (hide) 0 else ViewGroup.LayoutParams.WRAP_CONTENT
                            child.layoutParams = childLp
                        }
                        if (hide) {
                            child.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                                if (isEnabled && isStockBatteryView(v)) {
                                    if (v.visibility != View.GONE || v.alpha != 0f) {
                                        v.visibility = View.GONE
                                        v.alpha = 0f
                                        v.scaleX = 0f
                                        v.scaleY = 0f
                                        val currentLp = v.layoutParams
                                        if (currentLp != null && currentLp.width != 0) {
                                            currentLp.width = 0
                                            currentLp.height = 0
                                            v.layoutParams = currentLp
                                        }
                                    }
                                }
                            }
                        }
                        child.requestLayout()
                        child.invalidate()
                    }
                }

                view.requestLayout()
                view.invalidate()

                (view.parent as? View)?.let { parent ->
                    parent.requestLayout()
                    parent.invalidate()
                }
            }
        }
    }

    /**
     * Intercepts UnifiedBatteryKt composables directly to cancel.
     * Returns null on intercepted composable methods to prevent render.
     */
    private fun applyComposeUnifiedBatteryModifierHooks(module: XposedModule, classLoader: ClassLoader) {
        runCatching {
            val unifiedBatteryKtClass = classLoader.loadClass("com.android.systemui.statusbar.pipeline.battery.ui.composable.UnifiedBatteryKt")
            Logger.i(TAG, "ComposeModifier", "Found UnifiedBatteryKt class!")

            unifiedBatteryKtClass.declaredMethods.filter { 
                it.name == "UnifiedBattery" || 
                it.name == "BatteryBody" || 
                it.name == "BatteryCap" || 
                it.name == "BatteryAttribution" || 
                it.name == "BatteryLayout"
            }.forEach { method ->
                module.hook(method).intercept { chain ->
                    if (isEnabled) {
                        Logger.i(TAG, "ComposeModifier", "Intercepted & cancelled composition of ${method.name} in UnifiedBatteryKt")
                        return@intercept null
                    }
                    chain.proceed()
                }
            }
        }.onFailure { e ->
            Logger.e(TAG, "ComposeModifier", "UnifiedBatteryKt hook failed", e)
        }
    }

    /**
     * Hooks UnifiedBatteryViewBinder to capture the generated View.
     * Intercepts invokeSuspend method and tracks the resulting view.
     */
    private fun applyAndroid17ComposeBatteryHooks(module: XposedModule, classLoader: ClassLoader) {
        runCatching {
            val binderClassName = "com.android.systemui.statusbar.pipeline.battery.ui.binder.UnifiedBatteryViewBinder" + '$' + "bind" + '$' + "1" + '$' + "1"
            val bindInnerClass = classLoader.loadClass(binderClassName)

            bindInnerClass.declaredMethods.filter { it.name == "invokeSuspend" }.forEach { method ->
                module.hookAfter(method) { chain, _ ->
                    runCatching {
                        val instance = chain.thisObject
                        val viewField = findField(instance.javaClass, '$' + "view")
                        val composeView = viewField?.get(instance) as? View
                        if (composeView != null) {
                            markAsStockBatteryView(composeView)
                            trackComposeBatteryView(composeView)
                        }
                    }
                }
            }
        }.onFailure { e ->
            Logger.e(TAG, "Android17Compose", "UnifiedBatteryViewBinder hook failed", e)
        }
    }

    /**
     * Hooks StatusIconContainer to adjust layout sizing and ignored slots.
     * Manages paddingEnd and intercepts addView/onMeasure calls.
     */
    private fun applyStatusIconContainerHooks(module: XposedModule, classLoader: ClassLoader) {
        if (isAndroid17CP3A) {
            return
        }

        runCatching {
            val containerClass = classLoader.loadClass("com.android.systemui.statusbar.phone.StatusIconContainer")

            val onAttachedMethod = containerClass.getDeclaredMethod("onAttachedToWindow")
            module.hookBefore(onAttachedMethod) { chain ->
                runCatching {
                    val container = chain.thisObject as? ViewGroup ?: return@hookBefore

                    synchronized(trackedStatusIconContainers) {
                        if (trackedStatusIconContainers.none { it.get() == container }) {
                            trackedStatusIconContainers.add(WeakReference(container))
                        }
                    }
                    updateIgnoredSlotsForContainer(container)
                }.onFailure { e ->
                    Logger.e(TAG, "IconContainer", "Failed during onAttachedToWindow hook", e)
                }
            }

            module.hookBefore(onAttachedMethod) { chain ->
                if (isEnabled) {
                    val container = chain.thisObject as? ViewGroup ?: return@hookBefore
                    val lp = container.layoutParams
                    if (lp != null && lp.width != ViewGroup.LayoutParams.WRAP_CONTENT) {
                        lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
                        container.layoutParams = lp
                    }
                }
            }

            val viewClass = classLoader.loadClass("android.view.View")
            val layoutParamsClass = classLoader.loadClass("android.view.ViewGroup" + '$' + "LayoutParams")
            val addViewMethod = containerClass.getMethod("addView", viewClass, Int::class.javaPrimitiveType, layoutParamsClass)
            
            module.hookBefore(addViewMethod) { chain ->
                if (isEnabled) {
                    val view = chain.args[0] as? View ?: return@hookBefore
                    if (isStockBatteryView(view) || view.javaClass.name.contains("Battery")) {
                        view.visibility = View.GONE
                        view.alpha = 0f
                        view.scaleX = 0f
                        view.scaleY = 0f
                        chain.args[2] = LinearLayout.LayoutParams(0, 0)
                    }
                }
            }

            val onMeasureMethod = containerClass.getDeclaredMethod("onMeasure", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            module.hookAfter(onMeasureMethod) { chain, _ ->
                if (isEnabled) {
                    val container = chain.thisObject as? ViewGroup ?: return@hookAfter
                    val originalWidth = container.measuredWidth
                    val originalHeight = container.measuredHeight

                    var newWidth = 0
                    for (i in 0 until container.childCount) {
                        val child = container.getChildAt(i)
                        if (child != null && child.visibility != View.GONE && !isStockBatteryView(child)) {
                            newWidth += child.measuredWidth
                        }
                    }

                    if (newWidth in 1 until originalWidth) {
                        runCatching {
                            val setMeasuredDimensionMethod = View::class.java.getDeclaredMethod("setMeasuredDimension", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                            setMeasuredDimensionMethod.isAccessible = true
                            setMeasuredDimensionMethod.invoke(container, newWidth, originalHeight)
                        }.onFailure { e ->
                            Logger.e(TAG, "IconContainer", "Failed to shrink StatusIconContainer width", e)
                        }
                    } else if (newWidth == 0) {
                        runCatching {
                            val setMeasuredDimensionMethod = View::class.java.getDeclaredMethod("setMeasuredDimension", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                            setMeasuredDimensionMethod.isAccessible = true
                            setMeasuredDimensionMethod.invoke(container, 0, 0)
                        }.onFailure { e ->
                            Logger.e(TAG, "IconContainer", "Failed to collapse StatusIconContainer", e)
                        }
                    }
                }
            }

        }.onFailure { e ->
            Logger.e(TAG, "IconContainer", "Failed to hook StatusIconContainer", e)
        }
    }

    /**
     * Safely updates mIgnoredSlots reflection array for target container.
     * Adds or removes "battery" from container's ignored slots list.
     */
    @Suppress("UNCHECKED_CAST")
    private fun updateIgnoredSlotsForContainer(container: ViewGroup) {
        if (isAndroid17CP3A) return

        runCatching {
            val field = findField(container.javaClass, "mIgnoredSlots") ?: return@runCatching
            val ignoredSlots = field.get(container) as? MutableCollection<String> ?: return@runCatching

            synchronized(ignoredSlots) {
                if (isEnabled) {
                    if ("battery" !in ignoredSlots) {
                        ignoredSlots.add("battery")
                    }
                } else {
                    ignoredSlots.remove("battery")
                }
            }

            mainHandler.post {
                val paddingStart = container.paddingStart
                val paddingTop = container.paddingTop
                val originalPaddingEnd = originalPaddingEndMap[container]
                    ?: container.paddingEnd.also { originalPaddingEndMap[container] = it }
                val paddingEnd = if (isEnabled) 0 else originalPaddingEnd
                val paddingBottom = container.paddingBottom
                container.setPaddingRelative(paddingStart, paddingTop, paddingEnd, paddingBottom)

                container.requestLayout()
                container.invalidate()
            }
        }.onFailure { e ->
            Logger.e(TAG, "IconContainer", "Failed to update mIgnoredSlots on StatusIconContainer", e)
        }
    }

    /**
     * Triggers a refresh of visibility states for all tracked battery views.
     * Iterates modern views, compose views, and status icon containers.
     */
    private fun updateAllBatteryVisibility() {
        synchronized(trackedModernBatteryViews) {
            val iterator = trackedModernBatteryViews.iterator()
            while (iterator.hasNext()) {
                val view = iterator.next().get()
                if (view != null) {
                    applyVisibilityToModernBatteryView(view)
                } else {
                    iterator.remove()
                }
            }
        }

        synchronized(trackedComposeBatteryViews) {
            val iterator = trackedComposeBatteryViews.iterator()
            while (iterator.hasNext()) {
                val view = iterator.next().get()
                if (view != null) {
                    applyVisibilityToComposeView(view)
                } else {
                    iterator.remove()
                }
            }
        }

        synchronized(trackedStatusIconContainers) {
            val iterator = trackedStatusIconContainers.iterator()
            while (iterator.hasNext()) {
                val container = iterator.next().get()
                if (container != null) {
                    updateIgnoredSlotsForContainer(container)
                } else {
                    iterator.remove()
                }
            }
        }
    }

    /**
     * Computes final text color using system tint and battery state.
     * Evaluates charging, power save mode, level, and current tint.
     */
    private fun getEffectiveTextColor(): Int {
        return BatteryColorManager.getPercentTextColor(isCharging, isPowerSaveMode, currentLevel, animHue, currentTint)
    }

    @Volatile private var lastPulseInvalidateTime = 0L

    /**
     * Animates hue values to provide a pulsing charging indicator.
     * Runs ValueAnimator on UI thread throttled to 30 FPS for LTPO.
     */
    private fun startPulseAnimation() {
        if (!isScreenOn || pulseAnimator != null) return
        mainHandler.post {
            pulseAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 3000L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastPulseInvalidateTime >= 33L) {
                        lastPulseInvalidateTime = now
                        animHue = it.animatedValue as Float
                        updatePercentTextColors()
                    }
                }
                start()
            }
        }
    }

    /**
     * Terminates hue pulse animation and resets animator reference.
     * Cancels active pulseAnimator and refreshes text colors on UI thread.
     */
    private fun stopPulseAnimation() {
        mainHandler.post {
            pulseAnimator?.cancel()
            pulseAnimator = null
            updatePercentTextColors()
        }
    }

    /**
     * Applies current text colors to all tracked percentage text views.
     * Iterates weak references and updates TextView text colors.
     */
    private fun updatePercentTextColors() {
        val color = getEffectiveTextColor()
        synchronized(trackedPercentTextViews) {
            val iterator = trackedPercentTextViews.iterator()
            while (iterator.hasNext()) {
                val tv = iterator.next().get()
                tv?.setTextColor(color)
            }
        }
    }

    /**
     * Subscribes to battery framework intents for state updates.
     * Listens for battery changed, screen off/on, and power save intents.
     */
    private fun registerBatteryReceiver(context: Context) {
        runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            isPowerSaveMode = pm?.isPowerSaveMode ?: false

            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            }
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    when (intent.action) {
                        Intent.ACTION_BATTERY_CHANGED -> {
                            val rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                            if (rawLevel >= 0 && scale > 0) {
                                currentLevel = (rawLevel * 100) / scale
                                currentPercentText = "$currentLevel%"
                                updatePercentText()
                            }
                            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                            val newCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                            if (newCharging != isCharging) {
                                isCharging = newCharging
                                if (isCharging && isScreenOn) startPulseAnimation() else stopPulseAnimation()
                            }
                            updatePercentTextColors()
                        }
                        Intent.ACTION_SCREEN_OFF -> {
                            isScreenOn = false
                            stopPulseAnimation()
                        }
                        Intent.ACTION_SCREEN_ON -> {
                            isScreenOn = true
                            if (isCharging) startPulseAnimation() else stopPulseAnimation()
                        }
                        PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                            val pManager = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
                            isPowerSaveMode = pManager?.isPowerSaveMode ?: false
                            updatePercentTextColors()
                        }
                    }
                }
            }
            val stickyIntent = context.registerReceiver(receiver, filter)
            if (stickyIntent != null) {
                val rawLevel = stickyIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = stickyIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (rawLevel >= 0 && scale > 0) {
                    currentLevel = (rawLevel * 100) / scale
                    currentPercentText = "$currentLevel%"
                    updatePercentText()
                }
                val status = stickyIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                if (isCharging) startPulseAnimation() else stopPulseAnimation()
                updatePercentTextColors()
            }
        }.onFailure { e ->
            Logger.e(TAG, "Error", "Failed to register sticky battery receiver", e)
        }
    }

    /**
     * Applies dynamic tint color to the percent text views.
     * Updates currentTint variable and refreshes percent text colors.
     */
    private fun applyTint(tint: Int) {
        currentTint = tint
        updatePercentTextColors()
    }

    /**
     * Hooks Status Bar containers to inject the new percent text view.
     * Targets PhoneStatusBarView and KeyguardStatusBarView lifecycle.
     */
    private fun applyViewInjectionHooks(module: XposedModule, classLoader: ClassLoader) {
        val candidateClasses = listOf(
            "com.android.systemui.statusbar.phone.PhoneStatusBarView",
            "com.android.systemui.statusbar.phone.PhoneStatusBarViewController",
            "com.android.systemui.statusbar.phone.fragment.CollapsedStatusBarFragment",
            "com.android.systemui.statusbar.phone.KeyguardStatusBarView",
            "com.android.systemui.statusbar.phone.KeyguardStatusBarViewController"
        )

        candidateClasses.forEach { className ->
            runCatching {
                val clazz = classLoader.loadClass(className)
                val targetMethods = listOf("onFinishInflate", "onViewAttached", "onViewCreated")

                clazz.declaredMethods.filter { it.name in targetMethods }.forEach { method ->
                    module.hookBefore(method) { chain ->
                        when (val targetObj = chain.thisObject) {
                            is View -> injectTextViewToFarRight(targetObj as ViewGroup)
                            else -> runCatching {
                                val field = targetObj.javaClass.declaredFields.find { View::class.java.isAssignableFrom(it.type) }
                                field?.apply { isAccessible = true }?.get(targetObj) as? ViewGroup
                            }.getOrNull()?.let { injectTextViewToFarRight(it) }
                        }
                    }
                }
            }.onFailure { e ->
                Logger.e(TAG, "ViewInjection", "Failed to hook $className for view injection", e)
            }
        }
    }

    /**
     * Recursively searches for the system clock text view.
     * Traverses view hierarchy until a Clock TextView instance is found.
     */
    private fun findClockInGroup(group: ViewGroup): TextView? {
        runCatching {
            for (i in 0 until group.childCount) {
                val child = group.getChildAt(i) ?: continue
                if (child is TextView && (child.javaClass.name.contains("Clock") || child.javaClass.simpleName.contains("Clock"))) {
                    return child
                }
                if (child is ViewGroup) {
                    val found = findClockInGroup(child)
                    if (found != null) return found
                }
            }
        }.onFailure { e ->
            Logger.e(TAG, "ClockProbe", "Failed while searching for Clock view", e)
        }
        return null
    }

    /**
     * Recursively caches pre-existing StatusIconContainers.
     * Traverses view group hierarchy and adds containers to weak list.
     */
    private fun findStatusIconContainersInGroup(group: ViewGroup) {
        if (isAndroid17CP3A) return

        runCatching {
            for (i in 0 until group.childCount) {
                val child = group.getChildAt(i) ?: continue
                if (child is ViewGroup && child.javaClass.name.contains("StatusIconContainer")) {
                    synchronized(trackedStatusIconContainers) {
                        if (trackedStatusIconContainers.none { it.get() == child }) {
                            trackedStatusIconContainers.add(WeakReference(child))
                            Logger.i(TAG, "IconContainer", "Tracked pre-existing StatusIconContainer from View tree scan")
                        }
                    }
                    updateIgnoredSlotsForContainer(child)
                } else if (child is ViewGroup) {
                    findStatusIconContainersInGroup(child)
                }
            }
        }.onFailure { e ->
            Logger.e(TAG, "IconContainer", "Failed while searching for StatusIconContainers", e)
        }
    }

    /**
     * Determines whether a given view fits criteria for stock battery.
     * Protects WiFi, Mobile, Clock, and Notification icon view classes.
     */
    private fun isBatteryViewCandidate(child: View): Boolean {
        val cName = child.javaClass.name
        if (child.tag == VIEW_TAG) return false

        if (cName.contains("Wifi") || 
            cName.contains("Mobile") || 
            cName.contains("Signal") || 
            cName.contains("Carrier") || 
            cName.contains("Satellite") || 
            cName.contains("StatusIcon") || 
            cName.contains("User") || 
            cName.contains("Clock") || 
            cName.contains("Notification") || 
            cName.contains("TextView") || 
            cName.contains("LinearLayout")) {
            return false
        }

        return cName.contains("ComposeView") || cName.contains("Battery") || isStockBatteryView(child)
    }

    /**
     * Dynamically queries system padding for status bar icons in px.
     * Queries status_bar_icon_drawing_padding or falls back to 6dp.
     */
    private fun getNativeIconPaddingPx(res: android.content.res.Resources, density: Float): Int {
        val iconPaddingId = res.getIdentifier("status_bar_icon_drawing_padding", "dimen", "com.android.systemui")
            .takeIf { it > 0 }
            ?: res.getIdentifier("signal_cluster_battery_padding", "dimen", "com.android.systemui")
                .takeIf { it > 0 }

        return if (iconPaddingId != null) {
            runCatching { res.getDimensionPixelSize(iconPaddingId) }.getOrDefault((density * 6f).toInt())
        } else {
            (density * 6f).toInt()
        }
    }

    /**
     * Orchestrates injecting and styling percentage text into status bar.
     * Finds active container, instantiates TextView, and copies Clock style.
     */
    private fun injectTextViewToFarRight(rootGroup: ViewGroup) {
        rootGroup.post {
            runCatching {
                val context = rootGroup.context
                val res = context.resources

                findStatusIconContainersInGroup(rootGroup)

                val systemIconsContainerId = res.getIdentifier("system_icons_container", "id", "com.android.systemui")
                val systemIconsId = res.getIdentifier("system_icons", "id", "com.android.systemui")
                val endSideContentId = res.getIdentifier("status_bar_end_side_content", "id", "com.android.systemui")
                val endSideContainerId = res.getIdentifier("status_bar_end_side_container", "id", "com.android.systemui")
                val statusIconAreaId = res.getIdentifier("status_icon_area", "id", "com.android.systemui")

                val systemIconsContainer = if (systemIconsContainerId > 0) rootGroup.findViewById(systemIconsContainerId) as? ViewGroup else null
                val systemIconsGroup = if (systemIconsId > 0) rootGroup.findViewById(systemIconsId) as? ViewGroup else null
                val endSideContent = if (endSideContentId > 0) rootGroup.findViewById(endSideContentId) as? ViewGroup else null
                val endSideContainer = if (endSideContainerId > 0) rootGroup.findViewById(endSideContainerId) as? ViewGroup else null
                val statusIconArea = if (statusIconAreaId > 0) rootGroup.findViewById(statusIconAreaId) as? ViewGroup else null

                val isKeyguard = rootGroup.javaClass.simpleName.contains("Keyguard", ignoreCase = true) ||
                                 rootGroup.id == res.getIdentifier("keyguard_header", "id", "com.android.systemui")

                val targetContainer = when {
                    isAndroid17CP3A && isKeyguard && statusIconArea != null -> {
                        statusIconArea
                    }
                    systemIconsContainer != null && (systemIconsGroup == null || systemIconsGroup.isGone) -> {
                        systemIconsContainer
                    }
                    endSideContent != null && (systemIconsGroup == null || systemIconsGroup.isGone) -> {
                        endSideContent
                    }
                    systemIconsGroup != null && systemIconsGroup.visibility != View.GONE -> {
                        systemIconsGroup
                    }
                    systemIconsContainer != null -> systemIconsContainer
                    endSideContent != null -> endSideContent
                    endSideContainer != null -> endSideContainer
                    else -> rootGroup
                }

                val dumpTarget = systemIconsContainer ?: endSideContent ?: systemIconsGroup
                if (dumpTarget != null && dumpTarget.tag != "DUMPED") {
                    dumpTarget.tag = "DUMPED"
                }

                if (isKeyguard) {
                    val groupsToScan = listOfNotNull(systemIconsGroup, systemIconsContainer).distinct()
                    for (group in groupsToScan) {
                        for (i in 0 until group.childCount) {
                            val child = group.getChildAt(i) ?: continue
                            if (isBatteryViewCandidate(child)) {
                                trackComposeBatteryView(child)
                            }
                        }
                    }
                } else {
                    if (endSideContent != null && rootGroup.isVisible) {
                        for (i in 0 until endSideContent.childCount) {
                            val child = endSideContent.getChildAt(i) ?: continue
                            val cName = child.javaClass.name

                            if (cName.contains("LinearLayout") || child.id == systemIconsId) {
                                if (child.visibility != View.VISIBLE || child.alpha != 1.0f) {
                                    child.visibility = View.VISIBLE
                                    child.alpha = 1.0f
                                }
                            }
                            else if (isBatteryViewCandidate(child)) {
                                trackComposeBatteryView(child)
                            }
                        }
                    }

                    if (systemIconsGroup != null && !systemIconsGroup.isGone && rootGroup.isVisible) {
                        for (i in 0 until systemIconsGroup.childCount) {
                            val child = systemIconsGroup.getChildAt(i) ?: continue
                            if (isBatteryViewCandidate(child)) {
                                trackComposeBatteryView(child)
                            }
                        }
                    }
                }

                var textView = targetContainer.findViewWithTag<TextView>(VIEW_TAG)
                if (textView == null) {
                    val lp = when (targetContainer) {
                        is LinearLayout -> LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            gravity = Gravity.CENTER_VERTICAL
                        }
                        is FrameLayout -> FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            gravity = Gravity.END or Gravity.CENTER_VERTICAL
                        }
                        else -> ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    }

                    textView = TextView(context).apply {
                        tag = VIEW_TAG
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(getEffectiveTextColor())

                        val paddingStartPx = if (isKeyguard || !isAndroid17CP3A) {
                            (resources.displayMetrics.density * 2f).toInt()
                        } else {
                            getNativeIconPaddingPx(res, resources.displayMetrics.density)
                        }

                        setPadding(paddingStartPx, 0, 0, 0)
                        layoutParams = lp
                    }

                    targetContainer.addView(textView)
                    Logger.i(TAG, "Injection", "Injected right-side battery percent TextView into container: ${targetContainer.javaClass.simpleName}")
                }

                val clockId = res.getIdentifier("clock", "id", "com.android.systemui")
                val clockView = (if (clockId > 0) rootGroup.findViewById(clockId) as? TextView else null)
                    ?: findClockInGroup(rootGroup)

                if (clockView != null) {
                    runCatching {
                        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, clockView.textSize)
                        textView.typeface = clockView.typeface
                        textView.letterSpacing = clockView.letterSpacing
                        textView.includeFontPadding = clockView.includeFontPadding
                        Logger.i(TAG, "Injection", "Copied exact style from Clock: sizePx=${clockView.textSize}")
                    }.onFailure { e ->
                        Logger.e(TAG, "Injection", "Failed to copy Clock style", e)
                    }
                } else {
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    textView.typeface = Typeface.DEFAULT_BOLD
                    Logger.i(TAG, "Injection", "Clock View not found, applied lockscreen/keyguard fallback font style (14sp BOLD)")
                }

                synchronized(trackedPercentTextViews) {
                    if (trackedPercentTextViews.none { it.get() == textView }) {
                        trackedPercentTextViews.add(WeakReference(textView))
                    }
                }

                textView.text = currentPercentText
                textView.setTextColor(getEffectiveTextColor())
                textView.visibility = if (isEnabled) View.VISIBLE else View.GONE
            }.onFailure { e ->
                Logger.e(TAG, "Injection", "Failed during TextView injection sequence", e)
            }
        }
    }

    /**
     * Updates literal percent text string for tracked text views.
     * Posts update to UI thread and sets text and visibility on views.
     */
    private fun updatePercentText() {
        mainHandler.post {
            synchronized(trackedPercentTextViews) {
                val iterator = trackedPercentTextViews.iterator()
                while (iterator.hasNext()) {
                    val tv = iterator.next().get()
                    if (tv != null) {
                        tv.text = currentPercentText
                        tv.setTextColor(getEffectiveTextColor())
                        tv.visibility = if (isEnabled) View.VISIBLE else View.GONE
                    } else {
                        iterator.remove()
                    }
                }
            }
        }
    }

    /**
     * Shows or hides custom text views based on user preferences.
     * Updates visibility on UI thread for all tracked percentage views.
     */
    private fun updateViewVisibility() {
        mainHandler.post {
            synchronized(trackedPercentTextViews) {
                val iterator = trackedPercentTextViews.iterator()
                while (iterator.hasNext()) {
                    val tv = iterator.next().get()
                    if (tv != null) {
                        tv.visibility = if (isEnabled) View.VISIBLE else View.GONE
                    } else {
                        iterator.remove()
                    }
                }
            }
            updateAllBatteryVisibility()
        }
    }

    /**
     * Reflectively locates a declared field traversing class hierarchy.
     * Traverses superclasses until field with specified name is found.
     */
    private fun findField(clazz: Class<*>, name: String): Field? {
        var curr: Class<*>? = clazz
        while (curr != null) {
            try { return curr.getDeclaredField(name).apply { isAccessible = true } }
            catch (_: NoSuchFieldException) { curr = curr.superclass }
        }
        return null
    }
}
