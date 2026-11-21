@file:Suppress("DiscouragedApi")

package io.github.hohojia886.pixeltweaks.hooks.`interface`.battery

import android.content.res.Resources
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import io.github.hohojia886.pixeltweaks.utils.Logger
import io.github.hohojia886.pixeltweaks.utils.hookBefore
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Injects and manages the custom right-side battery percentage [TextView] across StatusBar and Keyguard.
 *
 * Clones the system Clock typeface/textSize, prevents duplicate injection across container relayouts,
 * and coordinates atomic same-frame visibility transitions with Compose recomposition on Android 17 CP3A.
 *
 * @param logTag Logger subsystem tag.
 * @param viewTag Unique tag applied to injected percentage [TextView] instances.
 * @param mainHandler Main-looper [Handler] for thread-safe UI updates.
 * @param isEnabledProvider Supplies whether status bar battery percentage display is enabled.
 * @param isCp3aProvider Supplies whether the active firmware uses Android 17 CP3A unified Compose layout.
 * @param percentTextProvider Supplies the current formatted percentage string.
 * @param textColorProvider Supplies the current effective ARGB text color.
 * @param suppressor Collaborating [StockBatterySuppressor] used to scan and track legacy battery views.
 */
internal class BatteryPercentViewInjector(
    private val logTag: String,
    private val viewTag: String,
    private val mainHandler: Handler,
    private val isEnabledProvider: () -> Boolean,
    private val isCp3aProvider: () -> Boolean,
    private val percentTextProvider: () -> String,
    private val textColorProvider: () -> Int,
    private val suppressor: StockBatterySuppressor,
) {

    internal val trackedPercentTextViews = Collections.synchronizedList(mutableListOf<WeakReference<TextView>>())
    private val trackedStatusBarViews = Collections.synchronizedList(mutableListOf<WeakReference<ViewGroup>>())
    internal val pendingPercentVisibilitySync = AtomicBoolean(false)

    /**
     * Hooks Status Bar and Keyguard container lifecycle methods to inject the percentage [TextView].
     *
     * @param module Active LSPosed [XposedModule] instance.
     * @param classLoader SystemUI ClassLoader.
     */
    fun installHooks(module: XposedModule, classLoader: ClassLoader) {
        val candidateClasses = listOf(
            "com.android.systemui.statusbar.phone.PhoneStatusBarView",
            "com.android.systemui.statusbar.phone.PhoneStatusBarViewController",
            "com.android.systemui.statusbar.phone.fragment.CollapsedStatusBarFragment",
            "com.android.systemui.statusbar.phone.KeyguardStatusBarView",
            "com.android.systemui.statusbar.phone.KeyguardStatusBarViewController",
        )

        candidateClasses.forEach { className ->
            runCatching {
                val clazz = classLoader.loadClass(className)
                val targetMethods = listOf("onFinishInflate", "onViewAttached", "onViewCreated")

                clazz.declaredMethods.filter { it.name in targetMethods }.forEach { method ->
                    module.hookBefore(method) { chain ->
                        when (val targetObj = chain.thisObject) {
                            is View -> {
                                val group = targetObj as ViewGroup
                                trackStatusBarRoot(group)
                                injectTextViewToFarRight(group)
                            }
                            else -> runCatching {
                                val field = targetObj.javaClass.declaredFields.find { View::class.java.isAssignableFrom(it.type) }
                                field?.apply { isAccessible = true }?.get(targetObj) as? ViewGroup
                            }.getOrNull()?.let { group ->
                                trackStatusBarRoot(group)
                                injectTextViewToFarRight(group)
                            }
                        }
                    }
                }
            }.onFailure { e ->
                if (e is ClassNotFoundException) {
                    Logger.d(logTag, "ViewInjection", "Skipping unavailable candidate class: $className")
                } else {
                    Logger.e(logTag, "ViewInjection", "Failed to hook $className for view injection", e)
                }
            }
        }
    }

    /**
     * Injects (or refreshes) the right-side battery percentage [TextView] inside [rootGroup].
     *
     * @param rootGroup Inflated status bar or keyguard root [ViewGroup].
     */
    fun injectTextViewToFarRight(rootGroup: ViewGroup) {
        rootGroup.post {
            runCatching {
                val context = rootGroup.context
                val res = context.resources

                suppressor.findStatusIconContainersInGroup(rootGroup)

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

                val isCp3a = isCp3aProvider()
                val targetContainer = when {
                    isCp3a && isKeyguard && statusIconArea != null -> statusIconArea
                    systemIconsContainer != null && (systemIconsGroup == null || systemIconsGroup.isGone) -> systemIconsContainer
                    endSideContent != null && (systemIconsGroup == null || systemIconsGroup.isGone) -> endSideContent
                    systemIconsGroup != null && systemIconsGroup.visibility != View.GONE -> systemIconsGroup
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
                            if (suppressor.isBatteryViewCandidate(child)) {
                                suppressor.trackComposeBatteryView(child)
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
                            } else if (suppressor.isBatteryViewCandidate(child)) {
                                suppressor.trackComposeBatteryView(child)
                            }
                        }
                    }

                    if (systemIconsGroup != null && !systemIconsGroup.isGone && rootGroup.isVisible) {
                        for (i in 0 until systemIconsGroup.childCount) {
                            val child = systemIconsGroup.getChildAt(i) ?: continue
                            if (suppressor.isBatteryViewCandidate(child)) {
                                suppressor.trackComposeBatteryView(child)
                            }
                        }
                    }
                }

                // Search the entire status bar root so container shifts never create a second TextView.
                var textView = rootGroup.findViewWithTag<TextView>(viewTag)
                if (textView == null) {
                    val lp = when (targetContainer) {
                        is LinearLayout -> LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ).apply {
                            gravity = Gravity.CENTER_VERTICAL
                        }
                        is FrameLayout -> FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ).apply {
                            gravity = Gravity.END or Gravity.CENTER_VERTICAL
                        }
                        else -> ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                    }

                    textView = TextView(context).apply {
                        tag = viewTag
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(textColorProvider())

                        val paddingStartPx = if (isKeyguard || !isCp3a) {
                            (resources.displayMetrics.density * 2f).toInt()
                        } else {
                            getNativeIconPaddingPx(res, resources.displayMetrics.density)
                        }

                        setPadding(paddingStartPx, 0, 0, 0)
                        layoutParams = lp
                    }

                    targetContainer.addView(textView)
                    Logger.i(logTag, "Injection", "Injected right-side battery percent TextView into container: ${targetContainer.javaClass.simpleName}")
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
                        Logger.i(logTag, "Injection", "Copied exact style from Clock: sizePx=${clockView.textSize}")
                    }.onFailure { e ->
                        Logger.e(logTag, "Injection", "Failed to copy Clock style", e)
                    }
                } else {
                    textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    textView.typeface = Typeface.DEFAULT_BOLD
                    Logger.i(logTag, "Injection", "Clock View not found, applied lockscreen/keyguard fallback font style (14sp BOLD)")
                }

                synchronized(trackedPercentTextViews) {
                    if (trackedPercentTextViews.none { it.get() == textView }) {
                        trackedPercentTextViews.add(WeakReference(textView))
                    }
                }
                trackStatusBarRoot(rootGroup)

                textView.text = percentTextProvider()
                textView.setTextColor(textColorProvider())
                textView.visibility = if (isEnabledProvider()) View.VISIBLE else View.GONE
            }.onFailure { e ->
                Logger.e(logTag, "Injection", "Failed during TextView injection sequence", e)
            }
        }
    }

    /**
     * Updates text content and color across all tracked percentage [TextView]s.
     *
     * @param text Formatted percentage string (e.g. `"85%"`).
     * @param color Effective ARGB text color.
     */
    fun updatePercentText(text: String, color: Int) {
        runOnMainThread {
            val syncPending = pendingPercentVisibilitySync.get()
            val targetVisibility = if (isEnabledProvider()) View.VISIBLE else View.GONE
            synchronized(trackedPercentTextViews) {
                val iterator = trackedPercentTextViews.iterator()
                while (iterator.hasNext()) {
                    val tv = iterator.next().get()
                    if (tv != null) {
                        tv.text = text
                        tv.setTextColor(color)
                        if (!syncPending && tv.visibility != targetVisibility) {
                            tv.visibility = targetVisibility
                        }
                    } else {
                        iterator.remove()
                    }
                }
            }
        }
    }

    /**
     * Applies updated text color across all tracked percentage [TextView]s.
     *
     * @param color Effective ARGB text color.
     */
    fun updatePercentTextColors(color: Int) {
        synchronized(trackedPercentTextViews) {
            val iterator = trackedPercentTextViews.iterator()
            while (iterator.hasNext()) {
                val tv = iterator.next().get()
                if (tv != null) {
                    tv.setTextColor(color)
                } else {
                    iterator.remove()
                }
            }
        }
    }

    /**
     * Synchronizes percentage [TextView] visibility with stock battery suppression.
     * On Android 17 CP3A, defers TextView visibility until the same VSync animation frame as Compose
     * recomposition so the icon swap commits atomically without horizontal bounce.
     *
     * @param hasComposeBridge True when the Compose recomposition bridge is initialized.
     * @param refreshStockBattery Callback invoking stock battery refresh / Compose recomposition.
     */
    fun updateViewVisibility(hasComposeBridge: Boolean, refreshStockBattery: () -> Unit) {
        runOnMainThread {
            if (isCp3aProvider() && hasComposeBridge) {
                pendingPercentVisibilitySync.set(true)
                refreshStockBattery()
                // Safety fallback on the same VSync animation phase in case no UnifiedBattery
                // composable is currently attached or scheduled to recompose on this frame.
                runCatching {
                    Choreographer.getInstance().postFrameCallback {
                        flushPendingVisibility()
                    }
                }.onFailure { e ->
                    Logger.d(logTag, "Visibility", "Choreographer frame callback unavailable, falling back to mainHandler: ${e.message}")
                    mainHandler.post { flushPendingVisibility() }
                }
            } else {
                pendingPercentVisibilitySync.set(false)
                applyPercentTextViewVisibility()
                refreshStockBattery()
                refreshStatusBarRootsIfLegacy()
            }
        }
    }

    /**
     * Flushes a deferred percentage [TextView] visibility update if pending.
     * Invoked inside Compose's `UnifiedBattery` recomposition pass or the single-frame Choreographer fallback.
     */
    fun flushPendingVisibility() {
        if (pendingPercentVisibilitySync.compareAndSet(true, false)) {
            runOnMainThread {
                applyPercentTextViewVisibility()
            }
        }
    }

    /**
     * Requests a layout pass on tracked status bar roots on legacy (non-CP3A) layouts.
     */
    fun refreshStatusBarRootsIfLegacy() {
        if (isCp3aProvider()) return
        runOnMainThread {
            synchronized(trackedStatusBarViews) {
                val iterator = trackedStatusBarViews.iterator()
                while (iterator.hasNext()) {
                    val root = iterator.next().get()
                    if (root != null && root.isAttachedToWindow) {
                        root.requestLayout()
                        root.invalidate()
                    } else if (root == null) {
                        iterator.remove()
                    }
                }
            }
        }
    }

    /**
     * Executes [action] immediately when already on the main looper, or posts it to [mainHandler].
     *
     * @param action Main-thread UI action.
     */
    internal fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    private fun applyPercentTextViewVisibility() {
        val targetVisibility = if (isEnabledProvider()) View.VISIBLE else View.GONE
        synchronized(trackedPercentTextViews) {
            val iterator = trackedPercentTextViews.iterator()
            while (iterator.hasNext()) {
                val tv = iterator.next().get()
                if (tv != null) {
                    if (tv.visibility != targetVisibility) {
                        tv.visibility = targetVisibility
                    }
                } else {
                    iterator.remove()
                }
            }
        }
    }

    private fun trackStatusBarRoot(group: ViewGroup) {
        synchronized(trackedStatusBarViews) {
            if (trackedStatusBarViews.none { it.get() == group }) {
                trackedStatusBarViews.add(WeakReference(group))
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
        }.onFailure { e ->
            Logger.e(logTag, "ClockProbe", "Failed while searching for Clock view", e)
        }
        return null
    }

    private fun getNativeIconPaddingPx(res: Resources, density: Float): Int {
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
}
