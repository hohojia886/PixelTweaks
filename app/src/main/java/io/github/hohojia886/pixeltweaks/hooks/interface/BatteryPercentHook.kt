package io.github.hohojia886.pixeltweaks.hooks.`interface`

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
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
 * BatteryPercentHook: Renders a clean text battery percentage (e.g., "100%") at the far-right side of the status bar.
 * Designed exclusively for Android 17 (Pixel SystemUIGoogle):
 * 1. Targets ModernStatusBarView (slot="battery") via setVisibleState(2 - STATE_HIDDEN).
 * 2. Targets Compose Battery Views (UnifiedBatteryViewBinder & UnifiedBatteryKt).
 * 3. Zeros paddingEnd on StatusIconContainer to eliminate WiFi-battery spacing.
 * 4. Injects matching Clock-style text on both PhoneStatusBarView and KeyguardStatusBarView.
 */
object BatteryPercentHook {

    private const val TAG = "Battery" // Functional category for Logger (PXTK_Battery)
    private const val VIEW_TAG = "PX_RIGHT_BATTERY_PERCENT_TAG"
    private const val BATTERY_MARKER_TAG = "PX_STOCK_BATTERY_VIEW_MARKER"

    @Volatile private var isEnabled = false
    @Volatile private var processPackageName: String? = null
    @Volatile private var currentPercentText = ""
    @Volatile private var currentTint = Color.WHITE

    private val trackedPercentTextViews = Collections.synchronizedList(mutableListOf<WeakReference<TextView>>())
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var appContextRef: WeakReference<Context>? = null

    private val trackedStatusIconContainers = Collections.synchronizedList(mutableListOf<WeakReference<ViewGroup>>())
    private val trackedComposeBatteryViews = Collections.synchronizedList(mutableListOf<WeakReference<View>>())
    private val trackedModernBatteryViews = Collections.synchronizedList(mutableListOf<WeakReference<View>>())

    // Memory-safe map to hold original paddingEnd for StatusIconContainer
    private val originalPaddingEndMap = Collections.synchronizedMap(WeakHashMap<ViewGroup, Int>())

    fun hook(module: XposedModule, classLoader: ClassLoader) {
        processPackageName = "com.android.systemui"
        syncSettings(module, classLoader)

        runCatching {
            val appClass = classLoader.loadClass("android.app.Application")
            module.hookBefore(appClass.getDeclaredMethod("onCreate")) { chain ->
                val app = chain.thisObject as? Context
                if (app != null) {
                    appContextRef = WeakReference(app)
                    IpcManager.registerSecureReceiver(app, module.getModuleApplicationInfo().uid) { intent ->
                        handleBroadcast(intent, app)
                    }

                    // Register Pure SDK Battery Receiver (Sticky Broadcast)
                    registerBatteryReceiver(app)
                }
            }
        }.onFailure { e ->
            Logger.e(TAG, "Error", "Failed to setup Application.onCreate hook", e)
        }

        // Apply Android 17 ModernStatusBarView Battery Hooks (slot="battery")
        applyModernStatusBarViewBatteryHooks(module, classLoader)

        // Apply Android 17 Compose Battery View Hooks (UnifiedBatteryViewBinder$bind$1$1)
        applyAndroid17ComposeBatteryHooks(module, classLoader)

        // Apply Compose Modifier Alpha 0f Hooks for UnifiedBatteryKt
        applyComposeUnifiedBatteryModifierHooks(module, classLoader)

        // Apply Status Bar Right-Side View Injection (Supports Unlocked + Lockscreen)
        applyViewInjectionHooks(module, classLoader)

        // Apply Direct StatusIconContainer Hooks (mIgnoredSlots & Padding Zeroing Strategy)
        applyStatusIconContainerHooks(module, classLoader)

        // Centralized Dark Mode Sync via StatusBarTintManager
        StatusBarTintManager.register(module, classLoader) { tint ->
            applyTint(tint)
        }
    }

    private fun syncSettings(module: XposedModule, classLoader: ClassLoader) {
        val loadedFromDe = runCatching {
            val ctx = IpcManager.getSafeContext(classLoader, processPackageName) ?: IpcManager.getSystemContext(classLoader) ?: return@runCatching false
            val uri = Uri.parse("content://io.github.hohojia886.pixeltweaks")
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
     * Android 17 ModernStatusBarView Strategy:
     * Hooks ModernStatusBarView.initView (filters slot="battery") and ModernStatusBarView.setVisibleState.
     */
    private fun applyModernStatusBarViewBatteryHooks(module: XposedModule, classLoader: ClassLoader) {
        runCatching {
            val modernViewClass = classLoader.loadClass("com.android.systemui.statusbar.pipeline.shared.ui.view.ModernStatusBarView")
            Logger.i(TAG, "ModernBattery", "Found ModernStatusBarView class!")

            modernViewClass.declaredMethods.filter { it.name == "initView" }.forEach { method ->
                module.hookAfter(method) { chain, _ ->
                    runCatching {
                        val view = chain.thisObject as? View ?: return@runCatching
                        val slot = chain.args.getOrNull(0) as? String
                        if (slot == "battery") {
                            view.tag = BATTERY_MARKER_TAG
                            synchronized(trackedModernBatteryViews) {
                                if (trackedModernBatteryViews.none { it.get() == view }) {
                                    trackedModernBatteryViews.add(WeakReference(view))
                                    Logger.i(TAG, "ModernBattery", "Tracked ModernStatusBarView for slot=battery: ${view.javaClass.name}")
                                }
                            }
                            applyVisibilityToModernBatteryView(view)
                        }
                    }
                }
            }

            modernViewClass.declaredMethods.filter { it.name == "setVisibleState" }.forEach { method ->
                module.hookBefore(method) { chain ->
                    if (isEnabled) {
                        val view = chain.thisObject as? View ?: return@hookBefore
                        if (view.tag == BATTERY_MARKER_TAG) {
                            chain.args[0] = 2 // 2 = STATE_HIDDEN
                        }
                    }
                }
            }
        }.onFailure { e ->
            Logger.e(TAG, "ModernBattery", "ModernStatusBarView hook failed", e)
        }
    }

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

    private fun trackComposeBatteryView(view: View) {
        if (view.tag == VIEW_TAG) return
        view.tag = BATTERY_MARKER_TAG
        synchronized(trackedComposeBatteryViews) {
            if (trackedComposeBatteryViews.none { it.get() == view }) {
                trackedComposeBatteryViews.add(WeakReference(view))
                Logger.i(TAG, "ComposeBattery", "Tracked Android 17 Compose Battery View: ${view.javaClass.name}")
            }
        }
        applyVisibilityToComposeView(view)
    }

    private fun applyVisibilityToComposeView(view: View) {
        mainHandler.post {
            runCatching {
                val hide = isEnabled

                // Hide outer ComposeView
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

                // Hide inner AndroidComposeView child if present
                if (view is ViewGroup && view.childCount > 0) {
                    for (i in 0 until view.childCount) {
                        val child = view.getChildAt(i) ?: continue
                        child.tag = BATTERY_MARKER_TAG
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
     * Android 17 Compose Modifier Hook:
     * Intercepts UnifiedBatteryKt Composable methods (UnifiedBattery, BatteryBody, BatteryCap, etc.)
     * and forces Modifier.alpha(0f) on the Composable parameter.
     */
    private fun applyComposeUnifiedBatteryModifierHooks(module: XposedModule, classLoader: ClassLoader) {
        runCatching {
            val unifiedBatteryKtClass = classLoader.loadClass("com.android.systemui.statusbar.pipeline.battery.ui.composable.UnifiedBatteryKt")
            val alphaKtClass = classLoader.loadClass("androidx.compose.ui.draw.AlphaKt")
            val modifierClass = classLoader.loadClass("androidx.compose.ui.Modifier")

            val alphaMethod = alphaKtClass.getMethod("alpha", modifierClass, Float::class.javaPrimitiveType)

            Logger.i(TAG, "ComposeModifier", "Found UnifiedBatteryKt & AlphaKt classes!")

            // Pre-filter methods that have a Modifier parameter before hooking
            unifiedBatteryKtClass.declaredMethods
                .filter { method -> method.parameterTypes.any { modifierClass.isAssignableFrom(it) } }
                .forEach { method ->
                    module.hookBefore(method) { chain ->
                        if (isEnabled) {
                            runCatching {
                                val modifierIdx = method.parameterTypes.indexOfFirst { modifierClass.isAssignableFrom(it) }
                                if (modifierIdx >= 0) {
                                    val currentModifier = chain.args[modifierIdx] ?: return@runCatching
                                    val hiddenModifier = alphaMethod.invoke(null, currentModifier, 0f)
                                    chain.args[modifierIdx] = hiddenModifier
                                }
                            }
                        }
                    }
                }
        }.onFailure { e ->
            Logger.e(TAG, "ComposeModifier", "UnifiedBatteryKt modifier hook failed", e)
        }
    }

    /**
     * Android 17 Strategy:
     * Hooks UnifiedBatteryViewBinder$bind$1$1.invokeSuspend and View.setVisibility.
     */
    private fun applyAndroid17ComposeBatteryHooks(module: XposedModule, classLoader: ClassLoader) {
        // 1. Hook UnifiedBatteryViewBinder$bind$1$1.invokeSuspend
        runCatching {
            val bindInnerClass = classLoader.loadClass("com.android.systemui.statusbar.pipeline.battery.ui.binder.UnifiedBatteryViewBinder\$bind\$1\$1")
            Logger.i(TAG, "Android17Compose", "Found UnifiedBatteryViewBinder\$bind\$1\$1 class!")

            bindInnerClass.declaredMethods.filter { it.name == "invokeSuspend" }.forEach { method ->
                module.hookAfter(method) { chain, _ ->
                    runCatching {
                        val instance = chain.thisObject
                        val viewField = findField(instance.javaClass, "\$view")
                        val composeView = viewField?.get(instance) as? View
                        if (composeView != null) {
                            composeView.tag = BATTERY_MARKER_TAG
                            Logger.i(TAG, "Android17Compose", "Intercepted UnifiedBatteryViewBinder\$bind\$1\$1 \$view field: ${composeView.javaClass.name}")
                            trackComposeBatteryView(composeView)
                        }
                    }
                }
            }
        }.onFailure { e ->
            Logger.e(TAG, "Android17Compose", "UnifiedBatteryViewBinder\$bind\$1\$1 hook failed", e)
        }

        // 2. Hook View.setVisibility(int) with O(1) tag-based lookup
        runCatching {
            val viewClass = classLoader.loadClass("android.view.View")
            val setVisibilityMethod = viewClass.getDeclaredMethod("setVisibility", Int::class.javaPrimitiveType)

            module.hookBefore(setVisibilityMethod) { chain ->
                if (isEnabled) {
                    val view = chain.thisObject as? View ?: return@hookBefore
                    if (view.tag == BATTERY_MARKER_TAG || (view as? ViewGroup)?.getChildAt(0)?.tag == BATTERY_MARKER_TAG) {
                        chain.args[0] = View.GONE
                        view.alpha = 0f
                        view.scaleX = 0f
                        view.scaleY = 0f
                    }
                }
            }
        }.onFailure { e ->
            Logger.e(TAG, "Android17Compose", "View.setVisibility hook failed", e)
        }
    }

    /**
     * Direct StatusIconContainer Lifecycle Strategy:
     * Hooks StatusIconContainer.onAttachedToWindow directly to manage mIgnoredSlots & paddingEnd.
     */
    private fun applyStatusIconContainerHooks(module: XposedModule, classLoader: ClassLoader) {
        runCatching {
            val containerClass = classLoader.loadClass("com.android.systemui.statusbar.phone.StatusIconContainer")
            Logger.i(TAG, "IconContainer", "Found StatusIconContainer class: ${containerClass.name}")

            val onAttachedMethod = containerClass.getDeclaredMethod("onAttachedToWindow")
            module.hookBefore(onAttachedMethod) { chain ->
                runCatching {
                    val container = chain.thisObject as? ViewGroup ?: return@runCatching

                    synchronized(trackedStatusIconContainers) {
                        if (trackedStatusIconContainers.none { it.get() == container }) {
                            trackedStatusIconContainers.add(WeakReference(container))
                            Logger.i(TAG, "IconContainer", "Tracked StatusIconContainer in onAttachedToWindow")
                        }
                    }
                    updateIgnoredSlotsForContainer(container)
                }
            }
        }.onFailure { e ->
            Logger.e(TAG, "IconContainer", "Failed to hook StatusIconContainer", e)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun updateIgnoredSlotsForContainer(container: ViewGroup) {
        runCatching {
            val field = findField(container.javaClass, "mIgnoredSlots") ?: return@runCatching
            val ignoredSlots = field.get(container) as? MutableCollection<String> ?: return@runCatching

            synchronized(ignoredSlots) {
                if (isEnabled) {
                    if ("battery" !in ignoredSlots) {
                        ignoredSlots.add("battery")
                        Logger.i(TAG, "IconContainer", "Added 'battery' to mIgnoredSlots in StatusIconContainer")
                    }
                } else {
                    ignoredSlots.remove("battery")
                    Logger.i(TAG, "IconContainer", "Removed 'battery' from mIgnoredSlots in StatusIconContainer")
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

    private fun updateAllBatteryVisibility() {
        // 1. Update all tracked ModernStatusBarViews
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

        // 2. Update all tracked Compose Battery Views
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

        // 3. Update all tracked StatusIconContainers
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

    private fun registerBatteryReceiver(context: Context) {
        runCatching {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    if (rawLevel >= 0 && scale > 0) {
                        val percent = (rawLevel * 100) / scale
                        currentPercentText = "$percent%"
                        updatePercentText()
                    }
                }
            }
            val stickyIntent = context.registerReceiver(receiver, filter)
            if (stickyIntent != null) {
                val rawLevel = stickyIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = stickyIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (rawLevel >= 0 && scale > 0) {
                    val percent = (rawLevel * 100) / scale
                    currentPercentText = "$percent%"
                    updatePercentText()
                }
            }
        }.onFailure { e ->
            Logger.e(TAG, "Error", "Failed to register sticky battery receiver", e)
        }
    }

    private fun applyTint(tint: Int) {
        currentTint = tint
        mainHandler.post {
            synchronized(trackedPercentTextViews) {
                val iterator = trackedPercentTextViews.iterator()
                while (iterator.hasNext()) {
                    val tv = iterator.next().get()
                    tv?.setTextColor(currentTint)
                }
            }
        }
    }

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
                        val targetObj = chain.thisObject
                        val rootGroup = when (targetObj) {
                            is View -> targetObj as? ViewGroup
                            else -> runCatching {
                                val field = targetObj.javaClass.declaredFields.find { View::class.java.isAssignableFrom(it.type) }
                                field?.apply { isAccessible = true }?.get(targetObj) as? ViewGroup
                            }.getOrNull()
                        }

                        if (rootGroup != null) {
                            injectTextViewToFarRight(rootGroup)
                        }
                    }
                }
            }
        }
    }

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
        }
        return null
    }

    private fun findStatusIconContainersInGroup(group: ViewGroup) {
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
        }
    }

    private fun injectTextViewToFarRight(rootGroup: ViewGroup) {
        rootGroup.post {
            runCatching {
                val context = rootGroup.context
                val res = context.resources

                // Scan View tree for pre-existing StatusIconContainer (Safeguard for Late Hook Loading)
                findStatusIconContainersInGroup(rootGroup)

                val systemIconsId = res.getIdentifier("system_icons", "id", "com.android.systemui")
                val endSideId = res.getIdentifier("status_bar_end_side_content", "id", "com.android.systemui")

                val systemIconsGroup = if (systemIconsId > 0) rootGroup.findViewById(systemIconsId) as? ViewGroup else null
                val targetContainer = systemIconsGroup
                    ?: (if (endSideId > 0) rootGroup.findViewById(endSideId) as? ViewGroup else null)
                    ?: rootGroup

                // Precision scan inside systemIconsGroup ONLY:
                // Sibling ComposeViews inside system_icons that are NOT StatusIconContainer and NOT LinearLayout is the stock battery View!
                if (systemIconsGroup != null) {
                    for (i in 0 until systemIconsGroup.childCount) {
                        val child = systemIconsGroup.getChildAt(i) ?: continue
                        val cName = child.javaClass.name
                        if (child.tag != VIEW_TAG && !cName.contains("StatusIconContainer") && !cName.contains("LinearLayout")) {
                            Logger.i(TAG, "Injection", "Found stock battery View inside system_icons: $cName")
                            trackComposeBatteryView(child)
                        }
                    }
                }

                var textView = targetContainer.findViewWithTag<TextView>(VIEW_TAG)
                if (textView == null) {
                    textView = TextView(context).apply {
                        tag = VIEW_TAG
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(currentTint)
                        val paddingStartPx = (resources.displayMetrics.density * 2f).toInt()
                        setPadding(paddingStartPx, 0, 0, 0)
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            gravity = Gravity.CENTER_VERTICAL
                        }
                    }

                    targetContainer.addView(textView)
                    Logger.i(TAG, "Injection", "Injected right-side battery percent TextView into container: ${targetContainer.javaClass.simpleName}")
                }

                // Copy exact font size, typeface, and style from status bar Clock View
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
                    }
                }

                synchronized(trackedPercentTextViews) {
                    if (trackedPercentTextViews.none { it.get() == textView }) {
                        trackedPercentTextViews.add(WeakReference(textView))
                    }
                }

                textView.text = currentPercentText
                textView.setTextColor(currentTint)
                textView.visibility = if (isEnabled) View.VISIBLE else View.GONE
            }
        }
    }

    private fun updatePercentText() {
        mainHandler.post {
            synchronized(trackedPercentTextViews) {
                val iterator = trackedPercentTextViews.iterator()
                while (iterator.hasNext()) {
                    val tv = iterator.next().get()
                    if (tv != null) {
                        tv.text = currentPercentText
                        tv.setTextColor(currentTint)
                        tv.visibility = if (isEnabled) View.VISIBLE else View.GONE
                    } else {
                        iterator.remove()
                    }
                }
            }
        }
    }

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

    private fun findField(clazz: Class<*>, name: String): Field? {
        var curr: Class<*>? = clazz
        while (curr != null) {
            try { return curr.getDeclaredField(name).apply { isAccessible = true } }
            catch (_: NoSuchFieldException) { curr = curr.superclass }
        }
        return null
    }
}
