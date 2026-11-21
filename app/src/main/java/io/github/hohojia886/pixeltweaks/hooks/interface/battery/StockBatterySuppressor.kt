@file:Suppress("DiscouragedPrivateApi", "PrivateApi", "DiscouragedApi", "UsePropertyAccessSyntax")

package io.github.hohojia886.pixeltweaks.hooks.`interface`.battery

import android.os.Handler
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.isNotEmpty
import io.github.hohojia886.pixeltweaks.utils.ComposeRecompositionBridge
import io.github.hohojia886.pixeltweaks.utils.Logger
import io.github.hohojia886.pixeltweaks.utils.findField
import io.github.hohojia886.pixeltweaks.utils.hookAfter
import io.github.hohojia886.pixeltweaks.utils.hookBefore
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

/**
 * Installs and manages all Xposed hooks responsible for hiding the stock SystemUI battery icon.
 *
 * Supports both Android 17 CP3A unified Compose status bar (`UnifiedBatteryKt` cancellation with reactive
 * recomposition via [ComposeRecompositionBridge]) and legacy View-based architectures (`ModernStatusBarView`,
 * `SingleBindableStatusBarIconView`, `UnifiedBatteryViewBinder`, and `StatusIconContainer`).
 *
 * @param logTag Logger subsystem tag.
 * @param viewTag View tag identifying the injected custom percentage indicator so it is never hidden.
 * @param mainHandler Main-looper [Handler] for legacy View visibility mutations.
 * @param composeBridge Reflective Compose recomposition bridge for CP3A reactive updates.
 * @param isEnabledProvider Supplies whether status bar battery percentage replacement is enabled.
 * @param isCp3aProvider Supplies whether the active firmware uses Android 17 CP3A unified Compose layout.
 * @param onComposeFrameSync Callback invoked inside Compose's `UnifiedBattery` recomposition pass for atomic View sync.
 */
internal class StockBatterySuppressor(
    private val logTag: String,
    private val viewTag: String,
    private val mainHandler: Handler,
    private val composeBridge: ComposeRecompositionBridge,
    private val isEnabledProvider: () -> Boolean,
    private val isCp3aProvider: () -> Boolean,
    private val onComposeFrameSync: () -> Unit,
) {

    private val stockBatteryViews = Collections.synchronizedMap(WeakHashMap<View, Boolean>())
    private val trackedStatusIconContainers = Collections.synchronizedList(mutableListOf<WeakReference<ViewGroup>>())
    private val trackedComposeBatteryViews = Collections.synchronizedList(mutableListOf<WeakReference<View>>())
    private val trackedModernBatteryViews = Collections.synchronizedList(mutableListOf<WeakReference<View>>())
    private val originalPaddingEndMap = Collections.synchronizedMap(WeakHashMap<ViewGroup, Int>())

    /**
     * Installs all stock battery suppression hooks across ViewModel, View, Compose, and StatusIconContainer layers.
     *
     * @param module Active LSPosed [XposedModule] instance.
     * @param classLoader SystemUI ClassLoader.
     */
    fun installHooks(module: XposedModule, classLoader: ClassLoader) {
        applyBatteryViewModelHooks(module, classLoader)
        applyModernStatusBarViewBatteryHooks(module, classLoader)
        applySingleBindableIconViewBatteryHooks(module, classLoader)
        applyAndroid17ComposeBatteryHooks(module, classLoader)
        applyComposeUnifiedBatteryModifierHooks(module, classLoader)
        applyStatusIconContainerHooks(module, classLoader)
    }

    /**
     * Marks a [View] as a stock battery component so visibility hooks suppress it when enabled.
     *
     * @param view Stock battery view instance to record.
     */
    fun markAsStockBatteryView(view: View) {
        stockBatteryViews[view] = true
    }

    /**
     * Checks whether a [View] has been recorded as a stock battery component.
     *
     * @param view Candidate view to check.
     * @return True if previously marked as a stock battery view.
     */
    fun isStockBatteryView(view: View): Boolean {
        return stockBatteryViews[view] == true
    }

    /**
     * Determines whether a view fits criteria for a stock battery component while protecting sibling icons.
     *
     * @param child Candidate child view in the status bar hierarchy.
     * @return True if [child] is a stock battery view candidate; false for WiFi, Mobile, Clock, or injected TextView.
     */
    fun isBatteryViewCandidate(child: View): Boolean {
        val cName = child.javaClass.name
        if (child.tag == viewTag) return false

        if (cName.contains("Wifi") ||
            cName.contains("Mobile") ||
            cName.contains("Signal") ||
            cName.contains("Carrier") ||
            cName.contains("Satellite") ||
            cName.contains("StatusIcon") ||
            cName.contains("Vpn") ||
            cName.contains("User") ||
            cName.contains("Clock") ||
            cName.contains("Notification") ||
            cName.contains("TextView") ||
            cName.contains("LinearLayout")
        ) {
            return false
        }

        val resName = runCatching { child.resources.getResourceEntryName(child.id) }.getOrNull() ?: ""
        val contentDesc = child.contentDescription?.toString() ?: ""

        return cName.contains("Battery", ignoreCase = true) ||
            resName.contains("battery", ignoreCase = true) ||
            contentDesc.contains("battery", ignoreCase = true) ||
            isStockBatteryView(child)
    }

    /**
     * Starts tracking a Compose battery view if not already cached and applies current visibility state.
     *
     * @param view Candidate battery view discovered during View hierarchy traversal.
     */
    fun trackComposeBatteryView(view: View) {
        if (view.tag == viewTag || !isBatteryViewCandidate(view)) return
        markAsStockBatteryView(view)
        synchronized(trackedComposeBatteryViews) {
            if (trackedComposeBatteryViews.none { it.get() == view }) {
                trackedComposeBatteryViews.add(WeakReference(view))
            }
        }
        applyVisibilityToComposeView(view)
    }

    /**
     * Recursively discovers and caches pre-existing `StatusIconContainer` instances on legacy (non-CP3A) layouts.
     *
     * @param group Root status bar [ViewGroup] to traverse.
     */
    fun findStatusIconContainersInGroup(group: ViewGroup) {
        if (isCp3aProvider()) return

        runCatching {
            for (i in 0 until group.childCount) {
                val child = group.getChildAt(i) ?: continue
                if (child is ViewGroup && child.javaClass.name.contains("StatusIconContainer")) {
                    synchronized(trackedStatusIconContainers) {
                        if (trackedStatusIconContainers.none { it.get() == child }) {
                            trackedStatusIconContainers.add(WeakReference(child))
                            Logger.i(logTag, "IconContainer", "Tracked pre-existing StatusIconContainer from View tree scan")
                        }
                    }
                    updateIgnoredSlotsForContainer(child)
                } else if (child is ViewGroup) {
                    findStatusIconContainersInGroup(child)
                }
            }
        }.onFailure { e ->
            Logger.e(logTag, "IconContainer", "Failed while searching for StatusIconContainers", e)
        }
    }

    /**
     * Triggers Compose recomposition and refreshes all tracked legacy and modern stock battery views.
     */
    fun refreshAll() {
        composeBridge.trigger("isEnabled=${isEnabledProvider()}")

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
     * Hooks `ModernStatusBarView` on Android 17 for battery slot visibility.
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
                        Logger.e(logTag, "ModernBattery", "Failed during initView hook processing", e)
                    }
                }
            }

            modernViewClass.declaredMethods.filter { it.name == "setVisibleState" }.forEach { method ->
                module.hookBefore(method) { chain ->
                    if (isEnabledProvider()) {
                        val view = chain.thisObject as? View ?: return@hookBefore
                        if (isStockBatteryView(view)) {
                            chain.args[0] = 2
                        }
                    }
                }
            }
        }.onFailure { e ->
            Logger.e(logTag, "ModernBattery", "ModernStatusBarView hook failed", e)
        }
    }

    /**
     * Intercepts Battery ViewModel visibility getters across legacy and modern Compose architectures.
     */
    private fun applyBatteryViewModelHooks(module: XposedModule, classLoader: ClassLoader) {
        val candidateClasses = listOf(
            "com.android.systemui.statusbar.pipeline.battery.ui.viewmodel.BatteryViewModel",
            "com.android.systemui.statusbar.systemstatusicons.battery.ui.viewmodel.BatteryIconViewModel",
            "com.android.systemui.statusbar.pipeline.battery.ui.viewmodel.UnifiedBatteryViewModel",
        )

        candidateClasses.forEach { className ->
            runCatching {
                val clazz = classLoader.loadClass(className)
                Logger.i(logTag, "BatteryViewModel", "Probed ViewModel class: $className")

                clazz.declaredMethods.forEach { method ->
                    val mName = method.name
                    val isBool = method.returnType == Boolean::class.javaPrimitiveType || method.returnType == java.lang.Boolean.TYPE
                    if ((mName == "getVisible" || mName == "isVisible" || mName == "isBatteryVisible" || mName == "isBatteryIconVisible") && isBool) {
                        module.hook(method).intercept { chain ->
                            composeBridge.observe(emptyList())
                            if (isEnabledProvider()) {
                                return@intercept false
                            }
                            chain.proceed()
                        }
                    } else if (mName == "getIcon" || mName == "getBatteryIcon") {
                        module.hook(method).intercept { chain ->
                            composeBridge.observe(emptyList())
                            if (isEnabledProvider()) {
                                return@intercept null
                            }
                            chain.proceed()
                        }
                    }
                }
            }.onFailure { e ->
                if (e is ClassNotFoundException) {
                    Logger.d(logTag, "Probe", "Battery ViewModel class not present on this build: $className")
                } else {
                    Logger.e(logTag, "BatteryViewModel", "Failed to hook $className", e)
                }
            }
        }
    }

    /**
     * Hooks bindable status bar icon views (`SingleBindableStatusBarComposeIconView`, etc.) for battery slot candidates.
     */
    private fun applySingleBindableIconViewBatteryHooks(module: XposedModule, classLoader: ClassLoader) {
        val classNames = listOf(
            "com.android.systemui.statusbar.pipeline.shared.ui.view.SingleBindableStatusBarComposeIconView",
            "com.android.systemui.statusbar.pipeline.shared.ui.view.SingleBindableStatusBarIconView",
            "com.android.systemui.statusbar.pipeline.shared.ui.view.ModernStatusBarView",
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
                                synchronized(trackedModernBatteryViews) {
                                    if (trackedModernBatteryViews.none { it.get() == view }) {
                                        trackedModernBatteryViews.add(WeakReference(view))
                                    }
                                }
                                applyVisibilityToModernBatteryView(view)
                                applyVisibilityToComposeView(view)
                            }
                        }.onFailure { e ->
                            Logger.e(logTag, "ComposeBattery", "Failed during SingleBindableIconView initView hook", e)
                        }
                    }
                }

                clazz.declaredMethods.filter { it.name == "setVisibleState" }.forEach { method ->
                    module.hookBefore(method) { chain ->
                        val view = chain.thisObject as? View ?: return@hookBefore
                        if (isEnabledProvider() && isStockBatteryView(view)) {
                            chain.args[0] = 2 // STATE_HIDDEN
                        }
                    }
                }

                clazz.declaredMethods.filter { it.name == "setVisibility" }.forEach { method ->
                    module.hookBefore(method) { chain ->
                        val view = chain.thisObject as? View ?: return@hookBefore
                        if (isEnabledProvider() && isStockBatteryView(view)) {
                            chain.args[0] = View.GONE
                        }
                    }
                }
            }.onFailure { e ->
                Logger.e(logTag, "ComposeBattery", "Failed to hook $className", e)
            }
        }
    }

    /**
     * Intercepts `UnifiedBatteryKt` composables to subscribe Compose recomposition and cancel rendering when enabled.
     */
    private fun applyComposeUnifiedBatteryModifierHooks(module: XposedModule, classLoader: ClassLoader) {
        runCatching {
            val unifiedBatteryKtClass = classLoader.loadClass("com.android.systemui.statusbar.pipeline.battery.ui.composable.UnifiedBatteryKt")
            Logger.i(logTag, "ComposeModifier", "Found UnifiedBatteryKt class!")

            val targetNames = setOf(
                "UnifiedBattery",
                "BatteryBody",
                "BatteryCap",
                "BatteryAttribution",
                "BatteryLayout",
            )

            unifiedBatteryKtClass.declaredMethods.filter { method ->
                method.name in targetNames || targetNames.any { method.name == "$it\$default" }
            }.forEach { method ->
                module.hook(method).intercept { chain ->
                    // Subscribe the enclosing RecomposeScope before branching so both enable and disable
                    // transitions reliably trigger Compose recomposition even without WiFi/VPN activity.
                    composeBridge.observe(chain.args)
                    if (isEnabledProvider()) {
                        // Flip percent TextView visibility inside the same Compose animation frame
                        // so View layout and Compose battery removal commit atomically without bounce.
                        onComposeFrameSync()
                        Logger.i(logTag, "ComposeModifier", "Intercepted & cancelled composition of ${method.name} in UnifiedBatteryKt")
                        return@intercept null
                    }
                    val result = chain.proceed()
                    onComposeFrameSync()
                    result
                }
            }
        }.onFailure { e ->
            Logger.e(logTag, "ComposeModifier", "UnifiedBatteryKt hook failed", e)
        }
    }

    /**
     * Hooks `UnifiedBatteryViewBinder` on non-CP3A builds to capture and track generated Compose battery views.
     */
    private fun applyAndroid17ComposeBatteryHooks(module: XposedModule, classLoader: ClassLoader) {
        if (isCp3aProvider()) return
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
            Logger.e(logTag, "Android17Compose", "UnifiedBatteryViewBinder hook failed", e)
        }
    }

    /**
     * Hooks `StatusIconContainer` on legacy (non-CP3A) layouts to suppress battery slot sizing and padding.
     */
    private fun applyStatusIconContainerHooks(module: XposedModule, classLoader: ClassLoader) {
        if (isCp3aProvider()) return

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
                    Logger.e(logTag, "IconContainer", "Failed during onAttachedToWindow hook", e)
                }
            }

            module.hookBefore(onAttachedMethod) { chain ->
                if (isEnabledProvider()) {
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
                if (isEnabledProvider()) {
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
                if (isEnabledProvider()) {
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
                            val setMeasuredDimensionMethod = View::class.java.getDeclaredMethod(
                                "setMeasuredDimension",
                                Int::class.javaPrimitiveType,
                                Int::class.javaPrimitiveType,
                            )
                            setMeasuredDimensionMethod.isAccessible = true
                            setMeasuredDimensionMethod.invoke(container, newWidth, originalHeight)
                        }.onFailure { e ->
                            Logger.e(logTag, "IconContainer", "Failed to shrink StatusIconContainer width", e)
                        }
                    } else if (newWidth == 0) {
                        runCatching {
                            val setMeasuredDimensionMethod = View::class.java.getDeclaredMethod(
                                "setMeasuredDimension",
                                Int::class.javaPrimitiveType,
                                Int::class.javaPrimitiveType,
                            )
                            setMeasuredDimensionMethod.isAccessible = true
                            setMeasuredDimensionMethod.invoke(container, 0, 0)
                        }.onFailure { e ->
                            Logger.e(logTag, "IconContainer", "Failed to collapse StatusIconContainer", e)
                        }
                    }
                }
            }
        }.onFailure { e ->
            Logger.e(logTag, "IconContainer", "Failed to hook StatusIconContainer", e)
        }
    }

    /**
     * Updates visibility and layout dimensions for a tracked `ModernStatusBarView` battery view.
     */
    private fun applyVisibilityToModernBatteryView(view: View) {
        mainHandler.post {
            runCatching {
                val hide = isEnabledProvider()

                runCatching {
                    val setVisibleStateMethod = view.javaClass.getMethod(
                        "setVisibleState",
                        Int::class.javaPrimitiveType,
                        Boolean::class.javaPrimitiveType,
                    )
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
     * Hides outer and inner elements of legacy Compose-based battery views.
     * No-op on Android 17 CP3A where the outer ComposeView hosts VPN, WiFi, Cellular, and Battery together.
     */
    private fun applyVisibilityToComposeView(view: View) {
        if (isCp3aProvider()) return

        mainHandler.post {
            runCatching {
                val hide = isEnabledProvider()

                if (isBatteryViewCandidate(view)) {
                    view.alpha = if (hide) 0f else 1f
                    view.scaleX = if (hide) 0f else 1f
                    view.scaleY = if (hide) 0f else 1f
                    view.visibility = if (hide) View.GONE else View.VISIBLE

                    val lp = view.layoutParams
                    if (lp != null) {
                        lp.width = if (hide) 0 else ViewGroup.LayoutParams.WRAP_CONTENT
                        lp.height = if (hide) 0 else ViewGroup.LayoutParams.WRAP_CONTENT
                        view.layoutParams = lp
                    }
                }

                if (view is ViewGroup && view.isNotEmpty()) {
                    for (i in 0 until view.childCount) {
                        val child = view.getChildAt(i) ?: continue
                        if (isBatteryViewCandidate(child)) {
                            markAsStockBatteryView(child)
                            child.alpha = if (hide) 0f else 1f
                            child.scaleX = if (hide) 0f else 1f
                            child.scaleY = if (hide) 0f else 1f
                            child.visibility = if (hide) View.GONE else View.VISIBLE
                            val childLp = child.layoutParams
                            if (childLp != null) {
                                childLp.width = if (hide) 0 else ViewGroup.LayoutParams.WRAP_CONTENT
                                childLp.height = if (hide) 0 else ViewGroup.LayoutParams.WRAP_CONTENT
                                child.layoutParams = childLp
                            }
                        }
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
     * Reflectively updates `mIgnoredSlots` and end padding on a legacy `StatusIconContainer`.
     */
    @Suppress("UNCHECKED_CAST")
    private fun updateIgnoredSlotsForContainer(container: ViewGroup) {
        if (isCp3aProvider()) return

        runCatching {
            val field = findField(container.javaClass, "mIgnoredSlots") ?: return@runCatching
            val ignoredSlots = field.get(container) as? MutableCollection<String> ?: return@runCatching
            val hide = isEnabledProvider()

            synchronized(ignoredSlots) {
                if (hide) {
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
                val paddingEnd = if (hide) 0 else originalPaddingEnd
                val paddingBottom = container.paddingBottom
                container.setPaddingRelative(paddingStart, paddingTop, paddingEnd, paddingBottom)

                container.requestLayout()
                container.invalidate()
            }
        }.onFailure { e ->
            Logger.e(logTag, "IconContainer", "Failed to update mIgnoredSlots on StatusIconContainer", e)
        }
    }
}
